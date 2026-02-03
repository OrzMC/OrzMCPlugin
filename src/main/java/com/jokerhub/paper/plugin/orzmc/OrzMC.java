package com.jokerhub.paper.plugin.orzmc;

import com.jokerhub.paper.plugin.orzmc.commands.OrzBotStatus;
import com.jokerhub.paper.plugin.orzmc.commands.OrzGuideBook;
import com.jokerhub.paper.plugin.orzmc.commands.OrzMenuCommand;
import com.jokerhub.paper.plugin.orzmc.commands.OrzPortalCommand;
import com.jokerhub.paper.plugin.orzmc.commands.OrzTPBow;
import com.jokerhub.paper.plugin.orzmc.events.OrzBowShootEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzDebugEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzMenuEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPlayerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzPortalEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzServerEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTNTEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzTPEvent;
import com.jokerhub.paper.plugin.orzmc.events.OrzWhiteListEvent;
import com.jokerhub.paper.plugin.orzmc.features.botcommands.BotCommandService;
import com.jokerhub.paper.plugin.orzmc.features.botcommands.OrzUserCmd;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.AdminOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CooldownInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.InterceptorExecutor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PlayerOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerLifecycleService;
import com.jokerhub.paper.plugin.orzmc.infra.binding.CommandBinder;
import com.jokerhub.paper.plugin.orzmc.infra.binding.EventBinder;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageServiceProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.portal.PortalService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrzMC extends JavaPlugin implements Listener {
    private ServerLifecycleService serverLifecycleService;
    private WorldMaintenanceService worldMaintenanceService;
    private BotInboundHandler botInboundHandler;
    private ConfigService configService;
    private PortalService portalService;
    private BotMessageService botMessageService;
    private OrzTextStyles textStyles;
    private ThrottledLogger throttledLogger;
    private ThrottledNotifier throttledNotifier;
    private Notifier notifier;

    @Override
    public void onEnable() {
        getLogger().info("插件生效!");
        configService = new ConfigService(this);
        textStyles = new OrzTextStyles(configService);
        throttledLogger = new ThrottledLogger(configService, getLogger());
        throttledNotifier = new ThrottledNotifier(configService);
        botInboundHandler = new BotCommandService(configService, textStyles);
        portalService = new PortalService(configService);
        botMessageService = BotMessageServiceProvider.create(this, configService, throttledLogger, botInboundHandler);
        OrzUserCmd.setConfigService(configService);
        notifier = new Notifier(configService, botMessageService);
        if (botInboundHandler instanceof BotCommandService service) {
            service.setNotifier(notifier);
        }
        serverLifecycleService = new ServerLifecycleService(configService, notifier);
        worldMaintenanceService = new WorldMaintenanceService(configService, textStyles, notifier);
        configService.setup();
        botMessageService.setup();
        portalService.setup();
        setupEventListener();
        setupCommandHandler();
        setupServerForceWhitelist();
    }

    @Override
    public void onDisable() {
        optimizeWorldOnShutdownIfNeed();
        serverLifecycleService.notifyServerStop();

        botMessageService.tearDown();
        portalService.tearDown();
        configService.tearDown();
        getLogger().info("插件失效!");
    }

    // 公共静态成员
    public static OrzMC plugin() {
        return JavaPlugin.getPlugin(OrzMC.class);
    }

    public static Server server() {
        return plugin().getServer();
    }

    public static Logger logger() {
        return OrzMC.plugin().getLogger();
    }

    // 公共方法

    public static void debugInfo(String msg) {
        if (!OrzDebugEvent.debug) {
            return;
        }
        OrzMC.logger().info(msg);
    }

    private void setupServerForceWhitelist() {
        boolean forceWhitelist = false;
        try {
            forceWhitelist = configService.getConfig("whitelist").getBoolean("force_whitelist");
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

    private void setupEventListener() {
        Listener[] eventListeners = new Listener[] {
            new OrzBowShootEvent(this, textStyles),
            new OrzPlayerEvent(this, configService, textStyles, notifier, throttledNotifier),
            new OrzTPEvent(this),
            new OrzTNTEvent(this, configService, textStyles, notifier, throttledNotifier),
            new OrzMenuEvent(this, textStyles),
            new OrzServerEvent(this, configService, textStyles, notifier),
            new OrzWhiteListEvent(this, configService, textStyles, notifier),
            new OrzDebugEvent(this, botInboundHandler),
            new OrzPortalEvent(this, portalService)
        };
        EventBinder.bind(this, Arrays.asList(eventListeners));
    }

    private void setupCommandHandler() {
        Map<String, CommandExecutor> commandHandlers = Map.of(
                "tpbow",
                new OrzTPBow(textStyles),
                "guide",
                new OrzGuideBook(configService, textStyles),
                "menu",
                new OrzMenuCommand(textStyles),
                "bot",
                new OrzBotStatus(textStyles),
                "portal",
                new OrzPortalCommand(portalService, textStyles));
        FileConfiguration cmdsCfg = configService.getConfig("commands");
        Map<String, CommandExecutor> enhanced = new HashMap<>();
        TypedConfigs.CommandPolicies cp = TypedConfigs.CommandPolicies.from(cmdsCfg);
        commandHandlers.forEach((name, exec) -> {
            TypedConfigs.CommandPolicy p = cp.policies().getOrDefault(name, new TypedConfigs.CommandPolicy(0, false));
            List<CommandInterceptor> interceptors = new ArrayList<>();
            interceptors.add(new PlayerOnlyInterceptor());
            interceptors.add(new AdminOnlyInterceptor(p.adminOnly()));
            interceptors.add(new CooldownInterceptor(name, Math.max(0, p.cooldownSeconds())));
            enhanced.put(name, new InterceptorExecutor(name, exec, interceptors));
        });
        CommandBinder.bind(this, enhanced);
    }

    private void optimizeWorldOnShutdownIfNeed() {
        boolean optimizeOnShutdown = false;
        boolean optimizeEnabled = false;
        try {
            optimizeOnShutdown = configService.getConfig("maintenance").getBoolean("optimize_on_shutdown");
            optimizeEnabled = configService.getConfig("maintenance").getBoolean("optimize_enabled");
        } catch (Exception ignored) {
        }
        if (optimizeEnabled && optimizeOnShutdown) {
            long tickTimeThreshold =
                    configService.getConfig("maintenance").getLong("optimize_tick_time_threshold", 300L);
            worldMaintenanceService.optimizeOnShutdown(tickTimeThreshold);
            getLogger().info("开始执行地图优化(关服阶段)");
        }
    }
}
