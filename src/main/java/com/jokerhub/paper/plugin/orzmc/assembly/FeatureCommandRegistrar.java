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
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuCommandService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalCommandService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRule;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRuleFeedback;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigPath;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 命令注册器：承载 FeatureModule 的全部 Brigadier 命令注册（从 FeatureModule 抽离，减小 God class）。
 *
 * <p>依赖全部 Feature 服务在构造后由 FeatureModule 注入。注册入口
 * {@link #registerCommands(OrzMC)} 在 {@code LifecycleEvents.COMMANDS} 上注册所有命令树。</p>
 */
final class FeatureCommandRegistrar {

    private final PlatformModule platform;
    private final BotModule botModule;
    private final GuideService guideService;
    private final MenuCommandService menuCommandService;
    private final TeleportBowService teleportBowService;
    private final PortalCommandService portalCommandService;
    private final AccessRuleService accessRuleService;
    private final ReviewCommandService reviewCommandService;
    private final RankCommandService rankCommandService;
    private final RankService rankService;
    private final OrzConfigCommand orzConfigCommand;

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
            OrzConfigCommand orzConfigCommand) {
        this.platform = platform;
        this.botModule = botModule;
        this.guideService = guideService;
        this.menuCommandService = menuCommandService;
        this.teleportBowService = teleportBowService;
        this.portalCommandService = portalCommandService;
        this.accessRuleService = accessRuleService;
        this.reviewCommandService = reviewCommandService;
        this.rankCommandService = rankCommandService;
        this.rankService = rankService;
        this.orzConfigCommand = orzConfigCommand;
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

            // ---- Portal: /portal [remove] <host> [port] ----
            registerPortal(commands, cpSupplier);

            // ---- Blacklist: /blacklist list|add|remove <pattern> ----
            registerBlacklist(commands);

            // ---- Rank: /apply（申请）/ /review（审核）/ /rank（查询）----
            registerRank(commands, cpSupplier);

            // ---- Config: /config list|get|set|reset|dump|reload ----
            registerConfig(commands);

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
        List<CommandInterceptor> interceptors = commandInterceptors(name, cpSupplier, skipPlayerOnly);
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

    /** Portal: /portal [remove] <host> [port] */
    private void registerPortal(Commands commands, Supplier<CommandPolicies> cpSupplier) {
        List<CommandInterceptor> interceptors = commandInterceptors("portal", cpSupplier, false);
        Predicate<CommandSourceStack> req = requirement(interceptors);
        OrzTextStyles styles = platform.textStyles();
        PortalCommandService svc = portalCommandService;

        // /portal remove <host> [port]
        Command<CommandSourceStack> removeExec = guardedExec("portal", interceptors, ctx -> {
            String target = ctx.getArgument("target", String.class);
            return handlePortal(svc, ctx.getSource(), "remove " + target, styles);
        });

        // /portal <host> [port]
        Command<CommandSourceStack> createExec = guardedExec("portal", interceptors, ctx -> {
            String target = ctx.getArgument("target", String.class);
            return handlePortal(svc, ctx.getSource(), target, styles);
        });

        // /portal (no args → show usage)
        Command<CommandSourceStack> usageExec = guardedExec("portal", interceptors, ctx -> {
            ctx.getSource()
                    .getSender()
                    .sendMessage(styles.info("用法: /portal <host> [port] 或 /portal remove <host> [port]"));
            return 1;
        });

        commands.register(
                literal("portal")
                        .requires(req)
                        .then(literal("remove")
                                .then(argument("target", StringArgumentType.greedyString())
                                        .executes(removeExec)))
                        .then(argument("target", StringArgumentType.greedyString())
                                .executes(createExec))
                        .executes(usageExec)
                        .build(),
                "创建或移除传送门",
                List.of());
    }

    private static int handlePortal(
            PortalCommandService svc, CommandSourceStack source, String argsStr, OrzTextStyles styles) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(svc.requirePlayerTip());
            return 1;
        }
        String[] args = argsStr.split(" ");
        PortalCommandService.Result result = svc.handle(p, args);
        if (result instanceof PortalCommandService.Result.Success s) {
            p.sendMessage(s.message());
        } else if (result instanceof PortalCommandService.Result.Failure f) {
            p.sendMessage(f.message());
        }
        return 1;
    }

    /** Blacklist: /blacklist list|add|remove <pattern>，并支持 player 玩家名规则子命令。 */
    private void registerBlacklist(Commands commands) {
        List<CommandInterceptor> interceptors = adminInterceptors("blacklist");
        Predicate<CommandSourceStack> req = requirement(interceptors);
        AccessRuleService svc = accessRuleService;
        OrzTextStyles styles = platform.textStyles();

        commands.register(
                literal("blacklist")
                        .requires(req)
                        .then(literal("list")
                                .executes(guardedExec("blacklist", interceptors, ctx -> {
                                    listAccessRules(ctx.getSource().getSender(), svc, styles);
                                    return 1;
                                }))
                                .then(literal("player").executes(guardedExec("blacklist", interceptors, ctx -> {
                                    listPlayerRules(ctx.getSource().getSender(), svc, styles);
                                    return 1;
                                }))))
                        .then(literal("add")
                                .then(literal("player")
                                        .then(argument("type", StringArgumentType.word())
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                                            String type = ctx.getArgument("type", String.class);
                                                            String value = ctx.getArgument("value", String.class);
                                                            handlePlayerRule(
                                                                    ctx.getSource()
                                                                            .getSender(),
                                                                    svc,
                                                                    styles,
                                                                    false,
                                                                    type,
                                                                    value);
                                                            return 1;
                                                        })))))
                                .then(argument("pattern", StringArgumentType.greedyString())
                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                            // greedyString 保留尾随空格：trim 后再判语法/入库，避免带空格的规则永不命中
                                            String pattern = ctx.getArgument("pattern", String.class)
                                                    .trim();
                                            if (pattern.isEmpty()) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error("用法: /blacklist add <IP>"));
                                                return 1;
                                            }
                                            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error(
                                                                "玩家名规则请使用: /blacklist add player <type> <value>"));
                                                return 1;
                                            }
                                            if (svc.addIpPattern(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success("已添加黑名单: " + pattern));
                                            } else {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success("黑名单已存在: " + pattern));
                                            }
                                            return 1;
                                        }))))
                        .then(literal("remove")
                                .then(literal("player")
                                        .then(argument("type", StringArgumentType.word())
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                                            String type = ctx.getArgument("type", String.class);
                                                            String value = ctx.getArgument("value", String.class);
                                                            handlePlayerRule(
                                                                    ctx.getSource()
                                                                            .getSender(),
                                                                    svc,
                                                                    styles,
                                                                    true,
                                                                    type,
                                                                    value);
                                                            return 1;
                                                        })))))
                                .then(argument("pattern", StringArgumentType.greedyString())
                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                            String pattern = ctx.getArgument("pattern", String.class)
                                                    .trim();
                                            if (pattern.isEmpty()) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error("用法: /blacklist remove <IP>"));
                                                return 1;
                                            }
                                            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error(
                                                                "玩家名规则请使用: /blacklist remove player <type> <value>"));
                                                return 1;
                                            }
                                            if (svc.removeIpPattern(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success("已从黑名单移除: " + pattern));
                                            } else {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error("未在黑名单中找到: " + pattern));
                                            }
                                            return 1;
                                        }))))
                        // Shorthand: /blacklist <pattern> → add
                        .then(argument("input", StringArgumentType.greedyString())
                                .executes(guardedExec("blacklist", interceptors, ctx -> {
                                    String input = ctx.getArgument("input", String.class);
                                    // player 玩家名规则绝不落入 IP 简写分支（对齐 bot $d 语义），大小写不敏感
                                    String lower = input.toLowerCase(Locale.ROOT);
                                    if (lower.equals("player") || lower.equals("player list")) {
                                        listPlayerRules(ctx.getSource().getSender(), svc, styles);
                                        return 1;
                                    }
                                    // 简写玩家名规则增删（对齐 bot $d 语义，大小写不敏感）：
                                    // /blacklist -player <type> <value> 移除、/blacklist player <type> <value> 添加
                                    if (lower.startsWith("-player")) {
                                        handlePlayerRuleShorthand(
                                                ctx.getSource().getSender(), svc, styles, true, input);
                                        return 1;
                                    }
                                    if (lower.startsWith("player ")) {
                                        handlePlayerRuleShorthand(
                                                ctx.getSource().getSender(), svc, styles, false, input);
                                        return 1;
                                    }
                                    if (lower.startsWith("player") || lower.startsWith("-player")) {
                                        ctx.getSource()
                                                .getSender()
                                                .sendMessage(styles.error(
                                                        "玩家名规则请使用: /blacklist add|remove player <type> <value>"));
                                        return 1;
                                    }
                                    if (input.startsWith("-")) {
                                        // trim：`/blacklist - exact foo` 破折号后带空格时，去掉空格再判玩家名规则语法
                                        String pattern = input.substring(1).trim();
                                        if (pattern.isEmpty()) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error("用法: /blacklist remove <IP>"));
                                            return 1;
                                        }
                                        if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(
                                                            "玩家名规则请使用: /blacklist remove player <type> <value>"));
                                            return 1;
                                        }
                                        if (svc.removeIpPattern(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("已从黑名单移除: " + pattern));
                                        } else {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error("未在黑名单中找到: " + pattern));
                                        }
                                    } else {
                                        String pattern = input.trim();
                                        if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(
                                                            "玩家名规则请使用: /blacklist add player <type> <value>"));
                                            return 1;
                                        }
                                        if (svc.addIpPattern(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("已添加黑名单: " + pattern));
                                        } else {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("黑名单已存在: " + pattern));
                                        }
                                    }
                                    return 1;
                                })))
                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                            listAccessRules(ctx.getSource().getSender(), svc, styles);
                            return 1;
                        }))
                        .build(),
                "IP黑名单与玩家名规则管理",
                List.of("bl"));
    }

    /**
     * 通用审核命令注册：
     * <ul>
     *   <li>/apply — 列出可申请类型（注册表驱动）</li>
     *   <li>/apply &lt;type&gt; [理由] — 提交申请</li>
     *   <li>/apply status — 查看自己的申请及状态</li>
     *   <li>/apply cancel &lt;type&gt; — 撤回待审申请</li>
     *   <li>/review approve|reject &lt;name&gt; — 管理员审核（替代 /rank approve|reject）</li>
     *   <li>/rank — 查自己（当前组 + 时长/进度 + 下一步可申请）</li>
     *   <li>/rank &lt;玩家&gt; — admin 查指定玩家</li>
     * </ul>
     */
    private void registerRank(Commands commands, Supplier<CommandPolicies> cpSupplier) {
        OrzTextStyles styles = platform.textStyles();

        // ---- /apply 通用申请命令 ----
        List<CommandInterceptor> applyInterceptors = commandInterceptors("apply", cpSupplier, false);
        commands.register(
                literal("apply")
                        .requires(requirement(applyInterceptors))
                        // /apply — 列出可申请类型
                        .executes(guardedExec("apply", applyInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(styles.error("仅玩家可用"));
                                return 1;
                            }
                            renderReviewResult(sender, reviewCommandService.listTypes(player));
                            return 1;
                        }))
                        // /apply status — 查看自己的申请
                        .then(literal("status").executes(guardedExec("apply", applyInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(styles.error("仅玩家可用"));
                                return 1;
                            }
                            renderReviewResult(sender, reviewCommandService.status(player));
                            return 1;
                        })))
                        // /apply cancel <type> — 撤回待审申请
                        .then(literal("cancel")
                                .then(argument("type", StringArgumentType.word())
                                        .executes(guardedExec("apply", applyInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) {
                                                sender.sendMessage(styles.error("仅玩家可用"));
                                                return 1;
                                            }
                                            String type = ctx.getArgument("type", String.class);
                                            renderReviewResult(sender, reviewCommandService.cancel(player, type));
                                            return 1;
                                        }))))
                        // /apply <type> [理由] — 提交申请
                        .then(argument("type", StringArgumentType.word())
                                .executes(guardedExec("apply", applyInterceptors, ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) {
                                        sender.sendMessage(styles.error("仅玩家可用"));
                                        return 1;
                                    }
                                    String type = ctx.getArgument("type", String.class);
                                    renderReviewResult(sender, reviewCommandService.apply(player, type, ""));
                                    return 1;
                                }))
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(guardedExec("apply", applyInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) {
                                                sender.sendMessage(styles.error("仅玩家可用"));
                                                return 1;
                                            }
                                            String type = ctx.getArgument("type", String.class);
                                            String reason = ctx.getArgument("reason", String.class);
                                            renderReviewResult(
                                                    sender, reviewCommandService.apply(player, type, reason));
                                            return 1;
                                        }))))
                        .build(),
                "提交/查询/撤回审核申请（如 /apply builder [理由]）",
                List.of("apply"));

        // ---- /review approve|reject <name> — 管理员审核（替代 /rank approve|reject）----
        List<CommandInterceptor> adminReviewInterceptors = adminInterceptors("review");
        commands.register(
                literal("review")
                        .requires(requirement(adminReviewInterceptors))
                        .then(literal("approve")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(guardedExec("review", adminReviewInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player admin)) {
                                                sender.sendMessage(styles.error("仅玩家可用"));
                                                return 1;
                                            }
                                            String name = ctx.getArgument("name", String.class);
                                            renderReviewResultAsync(
                                                    sender, reviewCommandService.review(admin, name, true));
                                            return 1;
                                        }))))
                        .then(literal("reject")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(guardedExec("review", adminReviewInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player admin)) {
                                                sender.sendMessage(styles.error("仅玩家可用"));
                                                return 1;
                                            }
                                            String name = ctx.getArgument("name", String.class);
                                            renderReviewResultAsync(
                                                    sender, reviewCommandService.review(admin, name, false));
                                            return 1;
                                        }))))
                        .build(),
                "管理员审核申请（/review approve|reject <玩家>）",
                List.of("review"));

        // ---- /rank — 查询自己 / /rank <玩家> — admin 查指定玩家 ----
        List<CommandInterceptor> rankInterceptors = commandInterceptors("rank", cpSupplier, false);
        List<CommandInterceptor> adminRankInterceptors = adminInterceptors("rank");
        commands.register(
                literal("rank")
                        .requires(requirement(rankInterceptors))
                        // /rank <玩家> — admin 查指定玩家
                        .then(argument("player", StringArgumentType.greedyString())
                                .requires(requirement(adminRankInterceptors))
                                .executes(guardedExec("rank", adminRankInterceptors, ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    String playerName = ctx.getArgument("player", String.class);
                                    UUID id = rankService.resolvePlayerId(playerName);
                                    if (id == null) {
                                        sender.sendMessage(styles.error("找不到玩家: " + playerName));
                                        return 1;
                                    }
                                    renderRankResult(sender, rankCommandService.statusOf(id));
                                    return 1;
                                })))
                        // /rank — 玩家查自己
                        .executes(guardedExec("rank", rankInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(styles.error("仅玩家可用"));
                                return 1;
                            }
                            renderRankResult(sender, rankCommandService.status(player));
                            return 1;
                        }))
                        .build(),
                "查询权限组与晋升进度",
                List.of("rank"));
    }

    private void renderReviewResult(CommandSender sender, ReviewCommandService.Result result) {
        if (result instanceof ReviewCommandService.Result.Failure f) {
            sender.sendMessage(f.message());
        } else if (result instanceof ReviewCommandService.Result.Success s) {
            sender.sendMessage(s.message());
        }
    }

    /** 异步审核结果渲染：授权完成后（回同步调度线程）给命令发起者反馈，命令本身立即返回。 */
    private void renderReviewResultAsync(
            CommandSender sender, java.util.concurrent.CompletableFuture<ReviewCommandService.Result> future) {
        future.whenComplete((result, err) -> {
            if (err != null) {
                sender.sendMessage(platform.textStyles()
                        .error("审核处理异常: " + (err.getMessage() == null ? "未知错误" : err.getMessage())));
            } else {
                renderReviewResult(sender, result);
            }
        });
    }

    private void renderRankResult(CommandSender sender, RankCommandService.Result result) {
        if (result instanceof RankCommandService.Result.Failure f) {
            sender.sendMessage(f.message());
        } else if (result instanceof RankCommandService.Result.Success s) {
            sender.sendMessage(s.message());
        }
    }

    private static void listAccessRules(CommandSender sender, AccessRuleService svc, OrzTextStyles styles) {
        List<String> patterns = svc.getIpPatterns();
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (patterns.isEmpty() && rules.isEmpty()) {
            sender.sendMessage(styles.info("访问规则为空"));
            return;
        }
        sender.sendMessage(styles.info("当前访问规则:"));
        if (!patterns.isEmpty()) {
            sender.sendMessage(styles.info("  IP黑名单:"));
            for (String pattern : patterns) {
                sender.sendMessage(styles.info("    " + pattern));
            }
        }
        if (!rules.isEmpty()) {
            sender.sendMessage(styles.info("  玩家名规则:"));
            for (PlayerNameRule rule : rules) {
                sender.sendMessage(styles.info("    " + rule.display()));
            }
        }
    }

    private static void listPlayerRules(CommandSender sender, AccessRuleService svc, OrzTextStyles styles) {
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (rules.isEmpty()) {
            sender.sendMessage(styles.info("玩家名规则为空"));
            return;
        }
        sender.sendMessage(styles.info("当前玩家名规则:"));
        for (PlayerNameRule rule : rules) {
            sender.sendMessage(styles.info("  " + rule.display()));
        }
    }

    private static void handlePlayerRule(
            CommandSender sender,
            AccessRuleService svc,
            OrzTextStyles styles,
            boolean remove,
            String typeRaw,
            String value) {
        if (value == null || value.isBlank()) {
            sender.sendMessage(
                    styles.error("玩家名规则值不能为空: /blacklist " + (remove ? "remove" : "add") + " player <type> <value>"));
            return;
        }
        // 反馈统一走 PlayerNameRuleFeedback（与 bot $d 共用，避免两边实现漂移）
        PlayerNameRuleFeedback.Outcome outcome = PlayerNameRuleFeedback.feedback(svc, typeRaw, value, remove);
        sender.sendMessage(outcome.success() ? styles.success(outcome.message()) : styles.error(outcome.message()));
    }

    /** 游戏侧简写解析（镜像 bot $d）：{@code /blacklist [-player|player] <type> <value>}。 */
    private static void handlePlayerRuleShorthand(
            CommandSender sender, AccessRuleService svc, OrzTextStyles styles, boolean remove, String input) {
        String prefix = remove ? "-player" : "player";
        String rest = input.substring(prefix.length());
        if (rest.isEmpty() || !rest.startsWith(" ")) {
            sender.sendMessage(styles.error("用法: /blacklist " + (remove ? "-" : "") + "player <type> <value>"));
            return;
        }
        String[] parts = rest.trim().split("\\s+", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            sender.sendMessage(styles.error("用法: /blacklist " + (remove ? "-" : "") + "player <type> <value>"));
            return;
        }
        handlePlayerRule(sender, svc, styles, remove, parts[0], parts[1]);
    }

    /** Config: /config list|get|set|reset|dump|reload */
    private void registerConfig(Commands commands) {
        List<CommandInterceptor> interceptors = adminInterceptors("config");
        Predicate<CommandSourceStack> req = requirement(interceptors);
        OrzConfigCommand cfgCmd = orzConfigCommand;
        OrzTextStyles styles = platform.textStyles();
        List<String> configPaths = new ArrayList<>(ConfigPath.all().keySet());

        // Tab suggestion provider for config paths
        SuggestionProvider<CommandSourceStack> pathSuggestions = (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (String path : configPaths) {
                if (path.toLowerCase().startsWith(prefix)) {
                    builder.suggest(path);
                }
            }
            return builder.buildFuture();
        };

        LiteralCommandNode<CommandSourceStack> node = literal("config")
                .requires(req)
                .then(literal("list").executes(guardedExec("config", interceptors, ctx -> {
                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[] {"list"});
                    return 1;
                })))
                .then(literal("get")
                        .then(argument("path", StringArgumentType.greedyString())
                                .suggests(pathSuggestions)
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String path = ctx.getArgument("path", String.class);
                                    cfgCmd.onCommand(
                                            ctx.getSource().getSender(), null, "config", new String[] {"get", path});
                                    return 1;
                                }))))
                .then(literal("set")
                        .then(argument("args", StringArgumentType.greedyString())
                                .suggests(pathSuggestions)
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String rest = ctx.getArgument("args", String.class);
                                    String[] cmdArgs = ("set " + rest).split(" ");
                                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", cmdArgs);
                                    return 1;
                                }))))
                .then(literal("reset")
                        .then(argument("path", StringArgumentType.greedyString())
                                .suggests(pathSuggestions)
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String path = ctx.getArgument("path", String.class);
                                    cfgCmd.onCommand(
                                            ctx.getSource().getSender(), null, "config", new String[] {"reset", path});
                                    return 1;
                                }))))
                .then(literal("dump").executes(guardedExec("config", interceptors, ctx -> {
                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[] {"dump"});
                    return 1;
                })))
                .then(literal("reload")
                        .then(argument("name", StringArgumentType.word())
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String name = ctx.getArgument("name", String.class);
                                    cfgCmd.onCommand(
                                            ctx.getSource().getSender(), null, "config", new String[] {"reload", name});
                                    return 1;
                                })))
                        .executes(guardedExec("config", interceptors, ctx -> {
                            cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[] {"reload"});
                            return 1;
                        })))
                .executes(guardedExec("config", interceptors, ctx -> {
                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[0]);
                    return 1;
                }))
                .build();

        commands.register(node, "配置管理", List.of("cfg"));
    }
}
