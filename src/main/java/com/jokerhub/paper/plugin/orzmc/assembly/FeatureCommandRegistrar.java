package com.jokerhub.paper.plugin.orzmc.assembly;

import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.adminInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.commandInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.guardedExec;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.requirement;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzConfigCommand;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.AdminOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PrisonDenyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceCommandService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuCommandService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalCommandService;
import com.jokerhub.paper.plugin.orzmc.features.prison.PrisonCommandService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import com.jokerhub.paper.plugin.orzmc.features.update.UpdateCommandService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 命令注册协调器：在同一个 {@code LifecycleEvents.COMMANDS} 事件里编排全部特性命令组。
 *
 * <p>命令树已按特性拆到独立 {@link CommandGroup} 注册器（portal/blacklist/review/rank/prison/
 * config/update 各一文件），本类只保留共享编排 + 未独立化的简单命令（guide/menu/tpbow、
 * /bot、/orzdebug、/maintenance）。依赖全部 Feature 服务在构造后由 FeatureModule 注入。</p>
 */
final class FeatureCommandRegistrar {

    private final PlatformModule platform;
    private final BotModule botModule;
    private final GuideService guideService;
    private final MenuCommandService menuCommandService;
    private final TeleportBowService teleportBowService;
    private final MaintenanceCommandService maintenanceCommandService;
    /** 坐牢拦截（prison 玩家禁全开放命令；装配层注入 prisonService 判定，LP 缺失恒 false）。 */
    private final PrisonDenyInterceptor prisonDenyInterceptor;
    /** 原始坐牢判定（传给子注册器构建 prison 追加拦截）。 */
    private final Predicate<Player> prisonDenyCheck;

    /** 各特性命令组（一个文件一个特性）。 */
    private final List<CommandGroup> groups;

    /** command_policies 快照缓存：拦截器每次判断读 volatile 缓存，避免热路径（每次执行/补全）
     * 全量重解析整张策略表；配置改动经 {@link #refreshCommandPolicies()} 刷新缓存。 */
    private volatile CommandPolicies commandPolicies = CommandPolicies.from(null);

    FeatureCommandRegistrar(
            PlatformModule platform,
            BotModule botModule,
            GuideService guideService,
            MenuCommandService menuCommandService,
            TeleportBowService teleportBowService,
            PortalCommandService portalCommandService,
            AccessRuleService accessRuleService,
            ReviewCommandService reviewCommandService,
            RankCommandService rankCommandService,
            RankService rankService,
            OrzConfigCommand orzConfigCommand,
            MaintenanceCommandService maintenanceCommandService,
            PrisonCommandService prisonCommandService,
            UpdateCommandService updateCommandService,
            Predicate<Player> prisonDenyCheck) {
        this.platform = platform;
        this.botModule = botModule;
        this.guideService = guideService;
        this.menuCommandService = menuCommandService;
        this.teleportBowService = teleportBowService;
        this.maintenanceCommandService = maintenanceCommandService;
        this.prisonDenyCheck = prisonDenyCheck;
        this.prisonDenyInterceptor = prisonDenyCheck == null ? null : new PrisonDenyInterceptor(prisonDenyCheck);
        Supplier<CommandPolicies> cpSupplier = () -> commandPolicies;
        OrzTextStyles styles = platform.textStyles();
        this.groups = List.of(
                new PortalCommandRegistrar(portalCommandService, styles, cpSupplier, prisonDenyCheck),
                new BlacklistCommandRegistrar(accessRuleService, styles),
                new ReviewCommandRegistrar(reviewCommandService, styles, cpSupplier, prisonDenyCheck),
                new RankCommandRegistrar(rankCommandService, rankService, styles, cpSupplier, prisonDenyCheck),
                new PrisonCommandRegistrar(prisonCommandService, styles),
                new ConfigCommandRegistrar(orzConfigCommand, styles),
                new UpdateCommandRegistrar(updateCommandService, styles));
    }

    /** 给开放命令拦截器链追加坐牢拒绝（null 守卫：未注入 prison 判定时不追加）。 */
    private List<CommandInterceptor> withPrisonDeny(List<CommandInterceptor> interceptors) {
        if (prisonDenyInterceptor == null) {
            return interceptors;
        }
        List<CommandInterceptor> extended = new ArrayList<>(interceptors);
        extended.add(prisonDenyInterceptor);
        return extended;
    }

    /** 注册所有命令（Paper LifecycleEvents.COMMANDS + Brigadier）。 */
    public void registerCommands(OrzMC plugin) {
        // Using direct Brigadier LiteralCommandNode (not BasicCommand wrapper) so that:
        // 1. No-arg commands show as /<name> (no auto-generated [args] in help)
        // 2. Subcommand commands show proper argument structure in help
        // 3. Tab completion suggests subcommand names naturally
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            // command_policies 读缓存快照：拦截器每次判断读 volatile 缓存而非全量重解析。
            // /orzmc config set/reset/reload 经 refreshCommandPolicies() 刷新（沿用 rankColors/
            // accessRules reload 回调模式），改动即时生效。
            refreshCommandPolicies();
            Supplier<CommandPolicies> cpSupplier = () -> commandPolicies;

            // ---- 特性级命令组（一个文件一个特性的命令树）----
            for (CommandGroup group : groups) {
                group.register(commands);
            }

            // ---- No-argument commands (clean literals, no [args] in help) ----
            registerSimple(
                    commands,
                    "guide",
                    "获取新手教程，更快的熟悉服务器",
                    List.of(),
                    cpSupplier,
                    false,
                    player -> guideService.openGuide(player));
            registerSimple(
                    commands,
                    "menu",
                    "菜单展示",
                    List.of(),
                    cpSupplier,
                    false,
                    player -> menuCommandService.handle(player));
            registerSimple(
                    commands,
                    "tpbow",
                    "传送弓，射出的箭落地时会把自己传送到箭落地的位置",
                    List.of("tpb"),
                    cpSupplier,
                    false,
                    player -> teleportBowService.giveAndEquip(player));
            // ---- Bot 健康状态：/bot 显示最简状态，/bot http、/bot ws 查看详情 ----
            registerBotStatus(commands, cpSupplier);
            // ---- Debug: /orzdebug <bot-command> 模拟群里用户发 Bot 命令 ----
            // 注：Paper 26 中 Brigadier 命令不触发 ServerCommandEvent，OrzDebugEvent
            // 监听器收不到事件，因此直接在此处调用 BotInboundHandler 完成模拟。
            // 安全：仅 OP/orzmc.admin 可用（AdminOnlyInterceptor），控制台始终放行。
            // 非管理员在 Tab 补全中不可见，直接输入会被 Brigadier 拒绝（requires 拦截）。
            List<CommandInterceptor> debugInterceptors = adminInterceptors("orzdebug");
            Predicate<CommandSourceStack> debugRequires = requirement(debugInterceptors);
            commands.register(
                    literal("orzdebug")
                            .requires(debugRequires)
                            .then(argument("cmd", StringArgumentType.greedyString())
                                    .executes(guardedExec("orzdebug", debugInterceptors, ctx -> {
                                        String cmd = ctx.getArgument("cmd", String.class);
                                        ctx.getSource().getSender().sendMessage("debug 已受理（模拟 Bot 入站命令）");
                                        var inbound = botModule.botInboundHandler();
                                        platform.serverFacade().runAsync(() -> {
                                            try {
                                                inbound.handleMessage(cmd, true, "控制台", env -> {
                                                    if (env != null) {
                                                        plugin.getLogger().info("cmd debug: \n" + env.message());
                                                    }
                                                });
                                            } catch (Exception e) {
                                                plugin.getLogger()
                                                        .log(java.util.logging.Level.SEVERE, "debug 命令异步执行异常", e);
                                            }
                                        });
                                        return 1;
                                    })))
                            .executes(guardedExec("orzdebug", debugInterceptors, ctx -> {
                                ctx.getSource().getSender().sendMessage("用法: /orzdebug <Bot命令>");
                                return 1;
                            }))
                            .build(),
                    "模拟群里用户发 Bot 命令（调试用）",
                    List.of());

            // ---- Maintenance: /maintenance on|off|status（手动维护模式）----
            // op/orzmc.admin 权限；与备份/优化互斥由 MaintenanceCommandService 内部保证
            List<CommandInterceptor> maintenanceInterceptors = adminInterceptors("maintenance");
            MaintenanceCommandService maintSvc = maintenanceCommandService;
            OrzTextStyles styles = platform.textStyles();
            commands.register(
                    literal("maintenance")
                            .requires(requirement(maintenanceInterceptors))
                            .then(literal("on").executes(guardedExec("maintenance", maintenanceInterceptors, ctx -> {
                                String result = maintSvc.enterManual();
                                CommandSender sender = ctx.getSource().getSender();
                                sender.sendMessage(result == null ? styles.success("已进入手动维护模式") : styles.error(result));
                                return 1;
                            })))
                            .then(literal("off").executes(guardedExec("maintenance", maintenanceInterceptors, ctx -> {
                                String result = maintSvc.exitManual();
                                CommandSender sender = ctx.getSource().getSender();
                                sender.sendMessage(result == null ? styles.success("已退出维护模式") : styles.error(result));
                                return 1;
                            })))
                            .then(literal("status")
                                    .executes(guardedExec("maintenance", maintenanceInterceptors, ctx -> {
                                        ctx.getSource().getSender().sendMessage(styles.info(maintSvc.status()));
                                        return 1;
                                    })))
                            .executes(guardedExec("maintenance", maintenanceInterceptors, ctx -> {
                                ctx.getSource().getSender().sendMessage(styles.info("用法: /maintenance on|off|status"));
                                return 1;
                            }))
                            .build(),
                    "手动维护模式管理（on/off/status）",
                    List.of("mt"));
        });
    }

    /** 从 config.yml 重读 command_policies 刷新缓存（/orzmc config set/reset/reload 后由组合根调用）。 */
    void refreshCommandPolicies() {
        this.commandPolicies = CommandPolicies.from(
                platform.configService().getConfig("config").getConfigurationSection("command_policies"));
    }

    private void registerSimple(
            Commands commands,
            String name,
            String description,
            List<String> aliases,
            Supplier<CommandPolicies> cpSupplier,
            boolean skipPlayerOnly,
            Consumer<Player> action) {
        List<CommandInterceptor> interceptors = withPrisonDeny(commandInterceptors(name, cpSupplier, skipPlayerOnly));
        commands.register(
                literal(name)
                        .requires(requirement(interceptors))
                        .executes(guardedExec(name, interceptors, ctx -> {
                            // 不依赖 PlayerOnlyInterceptor 顺序兜底：显式判断，控制台执行给友好反馈
                            CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(platform.textStyles().error("仅玩家可用"));
                                return 1;
                            }
                            action.accept(player);
                            return 1;
                        }))
                        .build(),
                description,
                aliases);
    }

    /** Bot 健康状态：/bot 显示 enabled/http/websocket 三个彩色状态词，/bot http、/bot ws 查看对应详情。 */
    private void registerBotStatus(Commands commands, Supplier<CommandPolicies> cpSupplier) {
        List<CommandInterceptor> rootInterceptors = commandInterceptors("bot", cpSupplier, true);
        // 详情子命令由点击触发，不套用冷却，避免紧跟 /bot 后点击被冷却拦截；adminOnly 惰性读取以热生效
        List<CommandInterceptor> detailInterceptors =
                List.of(new AdminOnlyInterceptor(BrigadierSupport.policyFor("bot", cpSupplier)));
        Predicate<CommandSourceStack> req = requirement(rootInterceptors);
        commands.register(
                literal("bot")
                        .requires(req)
                        .executes(guardedExec("bot", rootInterceptors, ctx -> {
                            botModule.botMessageService().tryReconnectIfDisconnected();
                            ctx.getSource()
                                    .getSender()
                                    .sendMessage(botModule.botStatusService().buildMinimalMessage());
                            return 1;
                        }))
                        .then(literal("http").executes(guardedExec("bot", detailInterceptors, ctx -> {
                            ctx.getSource()
                                    .getSender()
                                    .sendMessage(botModule.botStatusService().buildHttpDetail());
                            return 1;
                        })))
                        .then(literal("ws").executes(guardedExec("bot", detailInterceptors, ctx -> {
                            ctx.getSource()
                                    .getSender()
                                    .sendMessage(botModule.botStatusService().buildWsDetail());
                            return 1;
                        })))
                        .build(),
                "查看机器人健康状态",
                List.of());
    }
}
