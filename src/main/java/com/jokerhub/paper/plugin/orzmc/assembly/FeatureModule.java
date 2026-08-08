package com.jokerhub.paper.plugin.orzmc.assembly;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzConfigCommand;
import com.jokerhub.paper.plugin.orzmc.events.OrzBowShootEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzDebugEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzMenuEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPlayerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPortalEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzServerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTNTEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTPEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzWhiteListEvent;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.AdminOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CooldownInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PlayerOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuCommandService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuEventService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalCommandService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerEventService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerFeedbackService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerLifecycleService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowEventService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import com.jokerhub.paper.plugin.orzmc.features.tnt.TntEventService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistEventService;
import com.jokerhub.paper.plugin.orzmc.infra.binding.EventBinder;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigPath;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * 功能模块。
 *
 * <p>将所有 Feature 服务集中创建，并注册 Bukkit 事件监听器和命令。
 * 依赖所有其他模块创建完毕后才构造。</p>
 */
public final class FeatureModule implements ServiceModule {

    private final GeoIpAccessService geoIpAccessService;
    private final BlacklistService blacklistService;
    private final GuideService guideService;
    /** 在线玩家列表格式化（$l 命令与上下线广播共用，rankService 创建后注入）。 */
    private final com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter listFormatter =
            new com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter();

    private final PlayerEventService playerEventService;
    private final TntEventService tntEventService;
    private final WhitelistEventService whitelistEventService;
    private final MenuEventService menuEventService;
    private final TeleportBowService teleportBowService;
    private final TeleportBowEventService teleportBowEventService;
    private final PortalEventService portalEventService;
    private final ServerFeedbackService serverFeedbackService;
    private final ServerEventService serverEventService;
    private final ServerLifecycleService serverLifecycleService;
    private final MenuCommandService menuCommandService;
    private final PortalCommandService portalCommandService;
    private final OrzConfigCommand orzConfigCommand;
    private final com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService;
    private final com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService rankCommandService;
    private final com.jokerhub.paper.plugin.orzmc.features.review.ReviewService reviewService;
    private final com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService reviewCommandService;
    private com.jokerhub.paper.plugin.orzmc.events.OrzRankEvent rankEventService; // setupEventListeners 中创建

    // 模块引用（供事件/命令注册使用）
    private final PlatformModule platform;
    private final BotModule botModule;
    private final MaintenanceModule maintenanceModule;

    public FeatureModule(
            PlatformModule platform,
            BotModule botModule,
            PortalModule portalModule,
            MaintenanceModule maintenanceModule) {
        // Feature services
        this.geoIpAccessService = new GeoIpAccessService(platform.configs());
        this.blacklistService = new BlacklistService(platform.configService());
        this.guideService = new GuideService(platform.serverFacade(), platform.configService(), platform.textStyles());
        this.playerEventService = new PlayerEventService(
                platform.serverFacade(),
                platform.configs(),
                platform.textStyles(),
                botModule.notifier(),
                platform.throttledNotifier(),
                this.listFormatter);
        this.tntEventService = new TntEventService(
                platform.configs(), platform.textStyles(), botModule.notifier(), platform.serverScheduler());
        this.whitelistEventService =
                new WhitelistEventService(platform.configs(), platform.textStyles(), botModule.notifier());
        this.menuEventService = new MenuEventService(platform.textStyles());
        this.teleportBowService = new TeleportBowService(platform.serverFacade(), platform.textStyles());
        this.teleportBowEventService = new TeleportBowEventService(teleportBowService);
        this.portalEventService = new PortalEventService(platform.serverFacade(), portalModule.portalService());
        this.serverFeedbackService =
                new ServerFeedbackService(platform.serverFacade(), platform.configs(), platform.textStyles());
        this.serverEventService = new ServerEventService(
                serverFeedbackService,
                maintenanceModule.worldMaintenanceService(),
                platform.configs(),
                botModule.notifier());
        this.serverLifecycleService =
                new ServerLifecycleService(platform.serverFacade(), platform.configs(), botModule.notifier());
        this.menuCommandService = new MenuCommandService(platform.textStyles());
        this.portalCommandService = new PortalCommandService(portalModule.portalService(), platform.textStyles());
        this.orzConfigCommand = new OrzConfigCommand(
                platform.configService(), platform.textStyles(), botModule.botMessageService()::reloadConfig);
        // 权限晋升（Rank）模块：时长（读服务器原生 stats 文件）+ 自动晋升 + 通用审核框架
        // permission.yml 两段式统一存储（config 阈值 / reviews 审核记录；权限组状态由 LP track 持有）
        var permissionStore =
                new com.jokerhub.paper.plugin.orzmc.features.rank.PermissionStore(platform.configService());
        var rankPromoter = createRankPromoter(platform);
        // 通用审核框架：通知端口适配现有 Notifier + 模板；玩家解析端口适配 OfflinePlayer
        var reviewNotifier = new com.jokerhub.paper.plugin.orzmc.infra.notify.ReviewNotifierAdapter(
                platform.configs(), botModule.notifier());
        var playerLookup = new com.jokerhub.paper.plugin.orzmc.infra.player.BukkitPlayerLookup();
        this.rankService = new com.jokerhub.paper.plugin.orzmc.features.rank.RankService(
                permissionStore, rankPromoter, permissionStore.memberThresholdHours(), reviewNotifier);
        // 在线列表格式化注入权限组解析（$l 命令与上下线广播共用，一次注入两处生效）
        this.listFormatter.setRankService(this.rankService);
        this.reviewService = new com.jokerhub.paper.plugin.orzmc.features.review.ReviewService(
                permissionStore, reviewNotifier, playerLookup);
        // 注册审核类型 BUILDER_PROMOTION：handler 由 rank 模块注入（LP 授权），框架零 LP 依赖
        this.reviewService.register(new com.jokerhub.paper.plugin.orzmc.features.review.ReviewType(
                "builder-promotion",
                "晋升建造者",
                "builder",
                rawArgs -> {
                    var data = new java.util.LinkedHashMap<String, String>();
                    data.put("target-group", "builder");
                    if (rawArgs != null && !rawArgs.isBlank()) {
                        data.put("reason", rawArgs);
                    }
                    return data;
                },
                playerId -> rankService.currentGroup(playerId).equals("member"),
                data -> "申请晋升 builder"
                        + (data.get("reason") == null || data.get("reason").isBlank() ? "" : "：" + data.get("reason")),
                // 审核通过 = track 升一级（member→builder）；返回 null（链顶/LP 异常）视为授权失败，
                // 保持 PENDING 不落 APPROVED（避免「已通过但未生效」）
                playerId -> rankService.promote(playerId) != null));
        // ADMIN_PROMOTION：builder→admin（四级流转最后一环；审核通过 = track 升一级）
        this.reviewService.register(new com.jokerhub.paper.plugin.orzmc.features.review.ReviewType(
                "admin-promotion",
                "晋升管理员",
                "admin",
                rawArgs -> {
                    var data = new java.util.LinkedHashMap<String, String>();
                    data.put("target-group", "admin");
                    if (rawArgs != null && !rawArgs.isBlank()) {
                        data.put("reason", rawArgs);
                    }
                    return data;
                },
                playerId -> rankService.currentGroup(playerId).equals("builder"),
                data -> "申请晋升 admin"
                        + (data.get("reason") == null || data.get("reason").isBlank() ? "" : "：" + data.get("reason")),
                playerId -> rankService.promote(playerId) != null));
        this.rankCommandService = new com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService(
                rankService, reviewService, platform.textStyles());
        this.reviewCommandService = new com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService(
                reviewService, platform.textStyles());
        this.rankEventService = null; // setupEventListeners 中创建

        // 保留模块引用（供事件/命令注册使用）
        this.platform = platform;
        this.botModule = botModule;
        this.maintenanceModule = maintenanceModule;
    }

    @Override
    public void setup() {
        // 由组合根在 setupAll 中统一触发
    }

    // --- Event Listener Registration ---

    public void setupEventListeners(OrzMC plugin) {
        // $v 群指令依赖的审核/权限服务（BotCommandService 早于 FeatureModule 创建，这里补注入）
        botModule.botCommandService().setReviewService(reviewService);
        botModule.botCommandService().setRankService(rankService);
        Listener[] eventListeners = new Listener[] {
            new OrzBowShootEvent(plugin, teleportBowEventService),
            new OrzPlayerEvent(
                    plugin,
                    geoIpAccessService,
                    blacklistService,
                    playerEventService,
                    guideService,
                    platform.textStyles(),
                    maintenanceModule.worldMaintenanceService()),
            new OrzTPEvent(plugin, platform.serverFacade()),
            new OrzTNTEvent(plugin, tntEventService),
            new OrzMenuEvent(plugin, menuEventService),
            new OrzServerEvent(plugin, serverEventService),
            new OrzWhiteListEvent(plugin, whitelistEventService),
            new OrzDebugEvent(plugin, botModule.botInboundHandler()),
            new OrzPortalEvent(plugin, portalEventService),
            this.rankEventService = new com.jokerhub.paper.plugin.orzmc.events.OrzRankEvent(plugin, rankService)
        };
        EventBinder.bind(plugin, Arrays.asList(eventListeners));
    }

    // --- Command Registration ---

    public void setupCommandHandlers(OrzMC plugin) {
        // ---- Register commands via Paper lifecycle COMMANDS event ----
        // Using direct Brigadier LiteralCommandNode (not BasicCommand wrapper)
        // so that:
        // 1. No-arg commands show as /<name> (no auto-generated [args] in help)
        // 2. Subcommand commands show proper argument structure in help
        // 3. Tab completion suggests subcommand names naturally
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            // Re-read command policies on each fire (supports reload)
            ConfigurationSection cmdSection =
                    platform.configService().getConfig("config").getConfigurationSection("command_policies");
            if (cmdSection == null) {
                FileConfiguration legacy = platform.configService().loadFile("commands.yml");
                cmdSection = legacy != null ? legacy.getConfigurationSection("commands") : null;
            }
            CommandPolicies cp = CommandPolicies.from(cmdSection);

            // ---- No-argument commands (clean literals, no [args] in help) ----
            registerSimple(
                    commands,
                    "guide",
                    "获取新手教程，更快的熟悉服务器",
                    List.of(),
                    cp,
                    false,
                    sender -> guideService.openGuide((Player) sender));
            registerSimple(
                    commands,
                    "menu",
                    "菜单展示",
                    List.of(),
                    cp,
                    false,
                    sender -> menuCommandService.handle((Player) sender));
            registerSimple(
                    commands,
                    "tpbow",
                    "传送弓，射出的箭落地时会把自己传送到箭落地的位置",
                    List.of("tpb"),
                    cp,
                    false,
                    sender -> teleportBowService.giveAndEquip((Player) sender));
            // ---- Bot 健康状态：/bot 显示最简状态，/bot http、/bot ws 查看详情 ----
            registerBotStatus(commands, cp);

            // ---- Portal: /portal [remove] <host> [port] ----
            registerPortal(commands, cp);

            // ---- Blacklist: /blacklist list|add|remove <pattern> ----
            registerBlacklist(commands, cp);

            // ---- Rank: /apply（申请）/ /review（审核）/ /rank（查询）----
            registerRank(commands, cp);

            // ---- Config: /config list|get|set|reset|dump|reload ----
            registerConfig(commands, cp);

            // ---- Debug: /orzdebug <bot-command> 模拟群里用户发 Bot 命令 ----
            // 注：Paper 26 中 Brigadier 命令不触发 ServerCommandEvent，OrzDebugEvent
            // 监听器收不到事件，因此直接在此处调用 BotInboundHandler 完成模拟。
            commands.register(
                    literal("orzdebug")
                            .requires(src -> true)
                            .then(argument("cmd", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String cmd = ctx.getArgument("cmd", String.class);
                                        ctx.getSource().getSender().sendMessage("debug 已受理（模拟 Bot 入站命令）");
                                        var inbound = botModule.botInboundHandler();
                                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
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
                                    }))
                            .executes(ctx -> {
                                ctx.getSource().getSender().sendMessage("用法: /orzdebug <Bot命令>");
                                return 1;
                            })
                            .build(),
                    "模拟群里用户发 Bot 命令（调试用）",
                    List.of());
        });
    }

    // ================================================================
    // Brigadier command builders
    // ================================================================

    /**
     * Register a simple no-argument command as a clean literal (no Brigadier args).
     * The interceptor chain (PlayerOnly, AdminOnly, Cooldown) is applied via
     * {@link #requirement(List)} and {@link #guardedExec(String, List, Command)}.
     */
    private void registerSimple(
            Commands commands,
            String name,
            String description,
            List<String> aliases,
            CommandPolicies cp,
            boolean skipPlayerOnly,
            Consumer<CommandSender> action) {
        List<CommandInterceptor> interceptors = commandInterceptors(name, cp, skipPlayerOnly);
        commands.register(
                literal(name)
                        .requires(requirement(interceptors))
                        .executes(guardedExec(name, interceptors, ctx -> {
                            action.accept(ctx.getSource().getSender());
                            return 1;
                        }))
                        .build(),
                description,
                aliases);
    }

    /** Bot 健康状态：/bot 显示 enabled/http/websocket 三个彩色状态词，/bot http、/bot ws 查看对应详情。 */
    private void registerBotStatus(Commands commands, CommandPolicies cp) {
        List<CommandInterceptor> rootInterceptors = commandInterceptors("bot", cp, true);
        // 详情子命令由点击触发，不套用冷却，避免紧跟 /bot 后点击被冷却拦截
        CommandPolicy botPolicy = cp.policies().getOrDefault("bot", new CommandPolicy(0, false));
        List<CommandInterceptor> detailInterceptors = List.of(new AdminOnlyInterceptor(botPolicy.adminOnly()));
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
    private void registerPortal(Commands commands, CommandPolicies cp) {
        List<CommandInterceptor> interceptors = commandInterceptors("portal", cp, false);
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

    /** Blacklist: /blacklist list|add|remove <pattern> */
    private void registerBlacklist(Commands commands, CommandPolicies cp) {
        List<CommandInterceptor> interceptors = adminInterceptors("blacklist");
        Predicate<CommandSourceStack> req = requirement(interceptors);
        BlacklistService svc = blacklistService;
        OrzTextStyles styles = platform.textStyles();

        commands.register(
                literal("blacklist")
                        .requires(req)
                        .then(literal("list").executes(guardedExec("blacklist", interceptors, ctx -> {
                            listBlacklist(ctx.getSource().getSender(), svc, styles);
                            return 1;
                        })))
                        .then(literal("add")
                                .then(argument("pattern", StringArgumentType.greedyString())
                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                            String pattern = ctx.getArgument("pattern", String.class);
                                            svc.add(pattern);
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("已添加黑名单: " + pattern));
                                            return 1;
                                        }))))
                        .then(literal("remove")
                                .then(argument("pattern", StringArgumentType.greedyString())
                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                            String pattern = ctx.getArgument("pattern", String.class);
                                            svc.remove(pattern);
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("已从黑名单移除: " + pattern));
                                            return 1;
                                        }))))
                        // Shorthand: /blacklist <pattern> → add
                        .then(argument("input", StringArgumentType.greedyString())
                                .executes(guardedExec("blacklist", interceptors, ctx -> {
                                    String input = ctx.getArgument("input", String.class);
                                    if (input.startsWith("-")) {
                                        svc.remove(input.substring(1));
                                        ctx.getSource()
                                                .getSender()
                                                .sendMessage(styles.success("已从黑名单移除: " + input.substring(1)));
                                    } else {
                                        svc.add(input);
                                        ctx.getSource().getSender().sendMessage(styles.success("已添加黑名单: " + input));
                                    }
                                    return 1;
                                })))
                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                            listBlacklist(ctx.getSource().getSender(), svc, styles);
                            return 1;
                        }))
                        .build(),
                "IP黑名单管理",
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
    private void registerRank(Commands commands, CommandPolicies cp) {
        OrzTextStyles styles = platform.textStyles();

        // ---- /apply 通用申请命令 ----
        List<CommandInterceptor> applyInterceptors = commandInterceptors("apply", cp, false);
        commands.register(
                literal("apply")
                        .requires(requirement(applyInterceptors))
                        // /apply — 列出可申请类型
                        .executes(guardedExec("apply", applyInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof org.bukkit.entity.Player player)) {
                                sender.sendMessage(styles.error("仅玩家可用"));
                                return 1;
                            }
                            renderReviewResult(sender, reviewCommandService.listTypes(player));
                            return 1;
                        }))
                        // /apply status — 查看自己的申请
                        .then(literal("status").executes(guardedExec("apply", applyInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof org.bukkit.entity.Player player)) {
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
                                            if (!(sender instanceof org.bukkit.entity.Player player)) {
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
                                    if (!(sender instanceof org.bukkit.entity.Player player)) {
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
                                            if (!(sender instanceof org.bukkit.entity.Player player)) {
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
                                            if (!(sender instanceof org.bukkit.entity.Player admin)) {
                                                sender.sendMessage(styles.error("仅玩家可用"));
                                                return 1;
                                            }
                                            String name = ctx.getArgument("name", String.class);
                                            renderReviewResult(sender, reviewCommandService.review(admin, name, true));
                                            return 1;
                                        }))))
                        .then(literal("reject")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(guardedExec("review", adminReviewInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof org.bukkit.entity.Player admin)) {
                                                sender.sendMessage(styles.error("仅玩家可用"));
                                                return 1;
                                            }
                                            String name = ctx.getArgument("name", String.class);
                                            renderReviewResult(sender, reviewCommandService.review(admin, name, false));
                                            return 1;
                                        }))))
                        .build(),
                "管理员审核申请（/review approve|reject <玩家>）",
                List.of("review"));

        // ---- /rank — 查询自己 / /rank <玩家> — admin 查指定玩家 ----
        List<CommandInterceptor> rankInterceptors = commandInterceptors("rank", cp, false);
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
                            if (!(sender instanceof org.bukkit.entity.Player player)) {
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

    private void renderReviewResult(
            CommandSender sender, com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService.Result result) {
        if (result instanceof com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService.Result.Failure f) {
            sender.sendMessage(f.message());
        } else if (result
                instanceof com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService.Result.Success s) {
            sender.sendMessage(s.message());
        }
    }

    private void renderRankResult(
            CommandSender sender, com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService.Result result) {
        if (result instanceof com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService.Result.Failure f) {
            sender.sendMessage(f.message());
        } else if (result
                instanceof com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService.Result.Success s) {
            sender.sendMessage(s.message());
        }
    }

    private static void listBlacklist(CommandSender sender, BlacklistService svc, OrzTextStyles styles) {
        List<String> patterns = svc.getPatterns();
        if (patterns.isEmpty()) {
            sender.sendMessage(styles.info("黑名单为空"));
        } else {
            sender.sendMessage(styles.info("当前黑名单:"));
            for (String p : patterns) {
                sender.sendMessage(styles.info("  " + p));
            }
        }
    }

    /** Config: /config list|get|set|reset|dump|reload */
    private void registerConfig(Commands commands, CommandPolicies cp) {
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

    // ================================================================
    // Interceptor helpers
    // ================================================================

    /**
     * Build a {@link Predicate} for {@code .requires()} on the command node.
     * Only checks {@link AdminOnlyInterceptor} — non-admin users won't see the command.
     */
    private static Predicate<CommandSourceStack> requirement(List<CommandInterceptor> interceptors) {
        return stack -> {
            for (CommandInterceptor ci : interceptors) {
                if (ci instanceof AdminOnlyInterceptor aoi) {
                    return aoi.canUse(stack.getSender());
                }
            }
            return true;
        };
    }

    /**
     * Wrap a {@link Command} with runtime interceptor checks
     * (PlayerOnly and Cooldown).  AdminOnly is handled by {@link #requirement(List)}.
     */
    private static Command<CommandSourceStack> guardedExec(
            String name, List<CommandInterceptor> interceptors, Command<CommandSourceStack> delegate) {
        return ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            for (CommandInterceptor ci : interceptors) {
                if (ci instanceof AdminOnlyInterceptor) continue;
                Component res = ci.preHandle(sender, name);
                if (res != null) {
                    sender.sendMessage(res);
                    return 1;
                }
            }
            return delegate.run(ctx);
        };
    }

    /**
     * Build interceptors for regular commands from config policies.
     */
    private static List<CommandInterceptor> commandInterceptors(
            String name, CommandPolicies cp, boolean skipPlayerOnly) {
        CommandPolicy p = cp.policies().getOrDefault(name, new CommandPolicy(0, false));
        List<CommandInterceptor> list = new ArrayList<>();
        if (!skipPlayerOnly) {
            list.add(new PlayerOnlyInterceptor());
        }
        list.add(new AdminOnlyInterceptor(p.adminOnly()));
        list.add(new CooldownInterceptor(name, Math.max(0, p.cooldownSeconds())));
        return list;
    }

    /**
     * Build interceptors for hardcoded admin-only commands (blacklist, config).
     */
    private static List<CommandInterceptor> adminInterceptors(String name) {
        return List.of(new AdminOnlyInterceptor(true), new CooldownInterceptor(name, 0));
    }

    // --- Whitelist ---

    public void enableForceWhitelist(OrzMC plugin) {
        boolean forceWhitelist = false;
        try {
            forceWhitelist = platform.configs().whitelist().forceWhitelist();
        } catch (Exception e) {
            plugin.getLogger().warning("读取 forceWhitelist 配置失败: " + e.getMessage());
        }
        plugin.getServer().setWhitelist(forceWhitelist);
        plugin.getServer().setWhitelistEnforced(forceWhitelist);
        plugin.getServer().reloadWhitelist();
        plugin.getServer().setDefaultGameMode(GameMode.SURVIVAL);
        if (forceWhitelist) {
            plugin.getLogger().info("服务端使用强制白名单机制");
        }
    }

    // --- Getters for cross-module references ---

    public BlacklistService blacklistService() {
        return blacklistService;
    }

    // --- Lifecycle ---

    public void notifyServerStop() {
        serverLifecycleService.notifyServerStop();
    }

    /**
     * 创建权限执行器（软依赖条件实例化）。
     *
     * <p>LP 已启用 → 实例化 {@code LuckPermsPromoter}（直接引用 LP API 类型，此时
     * LP 插件提供 API 类，类加载安全）；LP 未启用 → 改用 {@code NoopRankPromoter}
     * 降级。关键：LP 未启用时<b>永不执行</b> {@code new LuckPermsPromoter}，
     * JVM 不会加载该类，因此不会因缺失 LP API 类而 NoClassDefFoundError。</p>
     */
    private com.jokerhub.paper.plugin.orzmc.features.rank.RankPromoter createRankPromoter(
            com.jokerhub.paper.plugin.orzmc.assembly.PlatformModule platform) {
        com.jokerhub.paper.plugin.orzmc.features.rank.PlayerNameResolver resolver = playerId -> {
            // 离线服：UUID→名字，玩家可能不在线（审核时申请者已退出），用 OfflinePlayer 查缓存
            return org.bukkit.Bukkit.getOfflinePlayer(playerId).getName();
        };
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            // 装即用：启动自动补齐 track「rank」+ 四级组骨架（幂等，已有不覆盖）
            new com.jokerhub.paper.plugin.orzmc.features.rank.LuckPermsBootstrap(
                            net.luckperms.api.LuckPermsProvider.get(), org.bukkit.Bukkit.getLogger())
                    .initialize();
            return new com.jokerhub.paper.plugin.orzmc.features.rank.LuckPermsPromoter(
                    resolver, platform.serverFacade()::runSync); // 异步链路回主线程执行 LP 变更
        }
        org.bukkit.Bukkit.getLogger().warning("[OrzMC] 未检测到 LuckPerms，权限管理功能不可用（时长查询/申请记录仍可用）");
        return new com.jokerhub.paper.plugin.orzmc.features.rank.NoopRankPromoter();
    }
}
