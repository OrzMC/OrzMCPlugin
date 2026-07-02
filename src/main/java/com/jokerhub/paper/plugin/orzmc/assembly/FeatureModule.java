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
                platform.throttledNotifier());
        this.tntEventService = new TntEventService(
                platform.configs(), platform.textStyles(), botModule.notifier(), platform.throttledNotifier());
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
        this.orzConfigCommand = new OrzConfigCommand(platform.configService(), platform.textStyles());

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
            new OrzPortalEvent(plugin, portalEventService)
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
            registerSimple(commands, "bot", "查看机器人健康状态", List.of(), cp, true, sender -> {
                botModule.botMessageService().tryReconnectQqWsIfDisconnected();
                sender.sendMessage(botModule.botStatusService().buildStatusMessage());
            });

            // ---- Portal: /portal [remove] <host> [port] ----
            registerPortal(commands, cp);

            // ---- Blacklist: /blacklist list|add|remove <pattern> ----
            registerBlacklist(commands, cp);

            // ---- Config: /config list|get|set|reset|dump|reload ----
            registerConfig(commands, cp);
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
        return List.of(new PlayerOnlyInterceptor(), new AdminOnlyInterceptor(true), new CooldownInterceptor(name, 0));
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
}
