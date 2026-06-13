package com.jokerhub.paper.plugin.orzmc;

import com.jokerhub.paper.plugin.orzmc.commands.OrzBotStatus;
import com.jokerhub.paper.plugin.orzmc.commands.OrzConfigCommand;
import com.jokerhub.paper.plugin.orzmc.commands.OrzGuideBook;
import com.jokerhub.paper.plugin.orzmc.commands.OrzMenuCommand;
import com.jokerhub.paper.plugin.orzmc.commands.OrzPortalCommand;
import com.jokerhub.paper.plugin.orzmc.commands.OrzTPBow;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.events.OrzBowShootEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzDebugEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzMenuEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPlayerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPortalEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzServerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTNTEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTPEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzWhiteListEvent;
import com.jokerhub.paper.plugin.orzmc.features.bot.BotStatusService;
import com.jokerhub.paper.plugin.orzmc.features.botcommands.BotCommandService;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.AdminOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CooldownInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.InterceptorExecutor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PlayerOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuCommandService;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuEventService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalCommandService;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerEventService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerFeedbackService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerLifecycleService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowEventService;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import com.jokerhub.paper.plugin.orzmc.features.tnt.TntEventService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistEventService;
import com.jokerhub.paper.plugin.orzmc.infra.binding.CommandBinder;
import com.jokerhub.paper.plugin.orzmc.infra.binding.EventBinder;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageServiceProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.DefaultTypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.portal.PortalService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;

public final class OrzServices {
    private final ServerFacade serverFacade;
    private final ConfigService configService;
    private final TypedConfigProvider configs;
    private final OrzTextStyles textStyles;
    private final ThrottledLogger throttledLogger;
    private final ThrottledNotifier throttledNotifier;
    private final BotInboundHandler botInboundHandler;
    private final PortalService portalService;
    private final BotMessageService botMessageService;
    private final Notifier notifier;
    private final ServerLifecycleService serverLifecycleService;
    private final WorldMaintenanceService worldMaintenanceService;
    private final GeoIpAccessService geoIpAccessService;
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
    private final MenuCommandService menuCommandService;
    private final PortalCommandService portalCommandService;
    private final BotStatusService botStatusService;
    private final OrzConfigCommand orzConfigCommand;

    private OrzServices(
            ServerFacade serverFacade,
            ConfigService configService,
            TypedConfigProvider configs,
            OrzTextStyles textStyles,
            ThrottledLogger throttledLogger,
            ThrottledNotifier throttledNotifier,
            BotInboundHandler botInboundHandler,
            PortalService portalService,
            BotMessageService botMessageService,
            Notifier notifier,
            ServerLifecycleService serverLifecycleService,
            WorldMaintenanceService worldMaintenanceService,
            GeoIpAccessService geoIpAccessService,
            GuideService guideService,
            PlayerEventService playerEventService,
            TntEventService tntEventService,
            WhitelistEventService whitelistEventService,
            MenuEventService menuEventService,
            TeleportBowService teleportBowService,
            TeleportBowEventService teleportBowEventService,
            PortalEventService portalEventService,
            ServerFeedbackService serverFeedbackService,
            ServerEventService serverEventService,
            MenuCommandService menuCommandService,
            PortalCommandService portalCommandService,
            BotStatusService botStatusService,
            OrzConfigCommand orzConfigCommand) {
        this.serverFacade = serverFacade;
        this.configService = configService;
        this.configs = configs;
        this.textStyles = textStyles;
        this.throttledLogger = throttledLogger;
        this.throttledNotifier = throttledNotifier;
        this.botInboundHandler = botInboundHandler;
        this.portalService = portalService;
        this.botMessageService = botMessageService;
        this.notifier = notifier;
        this.serverLifecycleService = serverLifecycleService;
        this.worldMaintenanceService = worldMaintenanceService;
        this.geoIpAccessService = geoIpAccessService;
        this.guideService = guideService;
        this.playerEventService = playerEventService;
        this.tntEventService = tntEventService;
        this.whitelistEventService = whitelistEventService;
        this.menuEventService = menuEventService;
        this.teleportBowService = teleportBowService;
        this.teleportBowEventService = teleportBowEventService;
        this.portalEventService = portalEventService;
        this.serverFeedbackService = serverFeedbackService;
        this.serverEventService = serverEventService;
        this.menuCommandService = menuCommandService;
        this.portalCommandService = portalCommandService;
        this.botStatusService = botStatusService;
        this.orzConfigCommand = orzConfigCommand;
    }

    public static OrzServices assemble(OrzMC plugin) {
        // Phase 1: core infrastructure (no cross-dependencies)
        ServerFacade serverFacade = new ServerFacade(plugin);
        ConfigService configService = new ConfigService(plugin);
        configService.setup();
        DefaultTypedConfigProvider configs = new DefaultTypedConfigProvider(configService);
        OrzTextStyles textStyles = new OrzTextStyles(configService);
        ThrottledLogger throttledLogger = new ThrottledLogger(configService, plugin.getLogger());
        ThrottledNotifier throttledNotifier = new ThrottledNotifier(configService);

        // Phase 2: bot and portal (core services)
        BotCommandService botInboundHandler = new BotCommandService(serverFacade, configs);
        PortalService portalService = new PortalService(configService);
        BotMessageService botMessageService = BotMessageServiceProvider.create(
                serverFacade, serverFacade, serverFacade, configService, throttledLogger, botInboundHandler);

        // Phase 3: notification and lifecycle (depends on bot + config)
        Notifier notifier = new Notifier(serverFacade, configService, botMessageService);
        ServerLifecycleService serverLifecycleService = new ServerLifecycleService(serverFacade, configs, notifier);
        WorldMaintenanceService worldMaintenanceService =
                new WorldMaintenanceService(serverFacade, configs, textStyles, notifier);

        // Phase 4: inject back-references into BotCommandService
        botInboundHandler.setNotifier(notifier);
        botInboundHandler.setMaintenanceService(worldMaintenanceService);

        // Phase 5: feature services
        GeoIpAccessService geoIpAccessService = new GeoIpAccessService(configs);
        GuideService guideService = new GuideService(serverFacade, configService, textStyles);
        PlayerEventService playerEventService =
                new PlayerEventService(serverFacade, configs, textStyles, notifier, throttledNotifier);
        TntEventService tntEventService = new TntEventService(configs, textStyles, notifier, throttledNotifier);
        WhitelistEventService whitelistEventService = new WhitelistEventService(configs, textStyles, notifier);
        MenuEventService menuEventService = new MenuEventService(textStyles);
        TeleportBowService teleportBowService = new TeleportBowService(serverFacade, textStyles);
        TeleportBowEventService teleportBowEventService = new TeleportBowEventService(teleportBowService);
        PortalEventService portalEventService = new PortalEventService(serverFacade, portalService);
        ServerFeedbackService serverFeedbackService = new ServerFeedbackService(serverFacade, configs, textStyles);
        ServerEventService serverEventService =
                new ServerEventService(serverFeedbackService, worldMaintenanceService, configs, notifier);
        MenuCommandService menuCommandService = new MenuCommandService(textStyles);
        PortalCommandService portalCommandService = new PortalCommandService(portalService, textStyles);
        BotStatusService botStatusService = new BotStatusService(textStyles);
        OrzConfigCommand orzConfigCommand = new OrzConfigCommand(configService, textStyles);

        return new OrzServices(
                serverFacade,
                configService,
                configs,
                textStyles,
                throttledLogger,
                throttledNotifier,
                botInboundHandler,
                portalService,
                botMessageService,
                notifier,
                serverLifecycleService,
                worldMaintenanceService,
                geoIpAccessService,
                guideService,
                playerEventService,
                tntEventService,
                whitelistEventService,
                menuEventService,
                teleportBowService,
                teleportBowEventService,
                portalEventService,
                serverFeedbackService,
                serverEventService,
                menuCommandService,
                portalCommandService,
                botStatusService,
                orzConfigCommand);
    }

    public void setupAll(OrzMC plugin) {
        botMessageService.setup();
        portalService.setup();
        setupEventListener(plugin);
        setupCommandHandler(plugin);
        enableForceWhitelist(plugin);
    }

    public void shutdownAll() {
        serverLifecycleService.notifyServerStop();
        botMessageService.tearDown();
        portalService.tearDown();
        configService.tearDown();
    }

    private void setupEventListener(OrzMC plugin) {
        Listener[] eventListeners = new Listener[] {
            new OrzBowShootEvent(plugin, teleportBowEventService),
            new OrzPlayerEvent(
                    plugin, geoIpAccessService, playerEventService, guideService, textStyles, worldMaintenanceService),
            new OrzTPEvent(plugin, serverFacade),
            new OrzTNTEvent(plugin, tntEventService),
            new OrzMenuEvent(plugin, menuEventService),
            new OrzServerEvent(plugin, serverEventService),
            new OrzWhiteListEvent(plugin, whitelistEventService),
            new OrzDebugEvent(plugin, botInboundHandler),
            new OrzPortalEvent(plugin, portalEventService)
        };
        EventBinder.bind(plugin, Arrays.asList(eventListeners));
    }

    private void setupCommandHandler(OrzMC plugin) {
        Map<String, CommandExecutor> commandHandlers = Map.of(
                "tpbow",
                new OrzTPBow(serverFacade, teleportBowService),
                "guide",
                new OrzGuideBook(guideService),
                "menu",
                new OrzMenuCommand(menuCommandService),
                "bot",
                new OrzBotStatus(botStatusService, botMessageService),
                "portal",
                new OrzPortalCommand(portalCommandService));
        ConfigurationSection cmdSection = configService.getConfig("config").getConfigurationSection("command_policies");
        if (cmdSection == null) {
            FileConfiguration legacy = configService.loadFile("commands.yml");
            cmdSection = legacy != null ? legacy.getConfigurationSection("commands") : null;
        }
        Map<String, CommandExecutor> enhanced = new HashMap<>();
        TypedConfigs.CommandPolicies cp = TypedConfigs.CommandPolicies.from(cmdSection);
        commandHandlers.forEach((name, exec) -> {
            TypedConfigs.CommandPolicy p = cp.policies().getOrDefault(name, new TypedConfigs.CommandPolicy(0, false));
            List<CommandInterceptor> interceptors = new ArrayList<>();
            if (!"bot".equals(name)) {
                interceptors.add(new PlayerOnlyInterceptor());
            }
            interceptors.add(new AdminOnlyInterceptor(p.adminOnly()));
            interceptors.add(new CooldownInterceptor(name, Math.max(0, p.cooldownSeconds())));
            enhanced.put(name, new InterceptorExecutor(name, exec, interceptors));
        });
        // orzmc 管理命令：/orzmc reload [config-name] | /orzmc config <subcommand>
        List<CommandInterceptor> orzmcInterceptors = new ArrayList<>();
        orzmcInterceptors.add(new AdminOnlyInterceptor(true));
        enhanced.put(
                "orzmc",
                new InterceptorExecutor(
                        "orzmc",
                        (sender, command, label, args) -> {
                            if (args.length < 1) {
                                sender.sendMessage(
                                        textStyles.error("用法: /orzmc reload [config-name] | /orzmc config <子命令>"));
                                sender.sendMessage(textStyles.info(
                                        "config 子命令: list / get <路径> / set <路径> <值> / reset <路径> / dump"));
                                return true;
                            }
                            switch (args[0].toLowerCase()) {
                                case "reload" -> {
                                    if (args.length >= 2) {
                                        if (configService.reloadConfig(args[1])) {
                                            sender.sendMessage(textStyles.success("配置 " + args[1] + " 已重新加载"));
                                        } else {
                                            sender.sendMessage(textStyles.error("配置 " + args[1] + " 不存在"));
                                        }
                                    } else {
                                        configService.reloadAll();
                                        sender.sendMessage(textStyles.success("所有配置已重新加载"));
                                    }
                                }
                                case "config" -> {
                                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                                    orzConfigCommand.onCommand(sender, command, label, subArgs);
                                }
                                default -> {
                                    sender.sendMessage(textStyles.error("未知子命令: " + args[0]));
                                    sender.sendMessage(textStyles.info("可用命令: reload, config"));
                                }
                            }
                            return true;
                        },
                        orzmcInterceptors));
        CommandBinder.bind(plugin, enhanced);
    }

    private void enableForceWhitelist(OrzMC plugin) {
        boolean forceWhitelist = false;
        try {
            forceWhitelist = configs.whitelist().forceWhitelist();
        } catch (Exception ignored) {
        }
        plugin.getServer().setWhitelist(forceWhitelist);
        plugin.getServer().setWhitelistEnforced(forceWhitelist);
        plugin.getServer().reloadWhitelist();
        plugin.getServer().setDefaultGameMode(GameMode.SURVIVAL);
        if (forceWhitelist) {
            plugin.getLogger().info("服务端使用强制白名单机制");
        }
    }
}
