package com.jokerhub.paper.plugin.orzmc;

import com.jokerhub.paper.plugin.orzmc.commands.OrzBotStatus;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class OrzMC extends JavaPlugin implements Listener {
    private ServerFacade serverFacade;
    private ServerLifecycleService serverLifecycleService;
    private WorldMaintenanceService worldMaintenanceService;
    private BotInboundHandler botInboundHandler;
    private ConfigService configService;
    private TypedConfigProvider configs;
    private PortalService portalService;
    private BotMessageService botMessageService;
    private OrzTextStyles textStyles;
    private ThrottledLogger throttledLogger;
    private ThrottledNotifier throttledNotifier;
    private Notifier notifier;
    private GeoIpAccessService geoIpAccessService;
    private GuideService guideService;
    private PlayerEventService playerEventService;
    private TntEventService tntEventService;
    private WhitelistEventService whitelistEventService;
    private MenuEventService menuEventService;
    private TeleportBowService teleportBowService;
    private TeleportBowEventService teleportBowEventService;
    private PortalEventService portalEventService;
    private ServerFeedbackService serverFeedbackService;
    private ServerEventService serverEventService;
    private MenuCommandService menuCommandService;
    private PortalCommandService portalCommandService;
    private BotStatusService botStatusService;

    @Override
    public void onEnable() {
        getLogger().info("插件生效!");
        serverFacade = new ServerFacade(this);
        configService = new ConfigService(this);
        configService.setup();
        configs = new DefaultTypedConfigProvider(configService);
        textStyles = new OrzTextStyles(configService);
        throttledLogger = new ThrottledLogger(configService, getLogger());
        throttledNotifier = new ThrottledNotifier(configService);
        botInboundHandler = new BotCommandService(serverFacade, configs);
        portalService = new PortalService(configService);
        botMessageService = BotMessageServiceProvider.create(
                serverFacade, serverFacade, serverFacade, configService, throttledLogger, botInboundHandler);
        notifier = new Notifier(serverFacade, configService, botMessageService);
        serverLifecycleService = new ServerLifecycleService(serverFacade, configs, notifier);
        worldMaintenanceService = new WorldMaintenanceService(serverFacade, configs, textStyles, notifier);
        if (botInboundHandler instanceof BotCommandService service) {
            service.setNotifier(notifier);
            service.setMaintenanceService(worldMaintenanceService);
        }
        initServices();
        botMessageService.setup();
        portalService.setup();
        setupEventListener();
        setupCommandHandler();
        setupServerForceWhitelist();
    }

    @Override
    public void onDisable() {
        serverLifecycleService.notifyServerStop();

        botMessageService.tearDown();
        portalService.tearDown();
        configService.tearDown();
        getLogger().info("插件失效!");
    }

    private void setupServerForceWhitelist() {
        boolean forceWhitelist = false;
        try {
            forceWhitelist = configs.whitelist().forceWhitelist();
        } catch (Exception ignored) {
        }
        getServer().setWhitelist(forceWhitelist);
        getServer().setWhitelistEnforced(forceWhitelist);
        getServer().reloadWhitelist();
        getServer().setDefaultGameMode(GameMode.SURVIVAL);
        if (forceWhitelist) {
            getLogger().info("服务端使用强制白名单机制");
        }
    }

    private void initServices() {
        geoIpAccessService = new GeoIpAccessService(configs);
        guideService = new GuideService(serverFacade, configService, textStyles);
        playerEventService = new PlayerEventService(serverFacade, configs, textStyles, notifier, throttledNotifier);
        tntEventService = new TntEventService(configs, textStyles, notifier, throttledNotifier);
        whitelistEventService = new WhitelistEventService(configs, textStyles, notifier);
        menuEventService = new MenuEventService(textStyles);
        teleportBowService = new TeleportBowService(serverFacade, textStyles);
        teleportBowEventService = new TeleportBowEventService(teleportBowService);
        portalEventService = new PortalEventService(serverFacade, portalService);
        serverFeedbackService = new ServerFeedbackService(serverFacade, configs, textStyles);
        serverEventService = new ServerEventService(serverFeedbackService, worldMaintenanceService, configs, notifier);
        menuCommandService = new MenuCommandService(textStyles);
        portalCommandService = new PortalCommandService(portalService, textStyles);
        botStatusService = new BotStatusService(textStyles);
    }

    private void setupEventListener() {
        Listener[] eventListeners = new Listener[] {
            new OrzBowShootEvent(this, teleportBowEventService),
            new OrzPlayerEvent(
                    this, geoIpAccessService, playerEventService, guideService, textStyles, worldMaintenanceService),
            new OrzTPEvent(this, serverFacade),
            new OrzTNTEvent(this, tntEventService),
            new OrzMenuEvent(this, menuEventService),
            new OrzServerEvent(this, serverEventService),
            new OrzWhiteListEvent(this, whitelistEventService),
            new OrzDebugEvent(this, botInboundHandler),
            new OrzPortalEvent(this, portalEventService)
        };
        EventBinder.bind(this, Arrays.asList(eventListeners));
    }

    private void setupCommandHandler() {
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
        FileConfiguration cmdsCfg = configService.getConfig("commands");
        Map<String, CommandExecutor> enhanced = new HashMap<>();
        TypedConfigs.CommandPolicies cp = TypedConfigs.CommandPolicies.from(cmdsCfg);
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
        CommandBinder.bind(this, enhanced);
    }
}
