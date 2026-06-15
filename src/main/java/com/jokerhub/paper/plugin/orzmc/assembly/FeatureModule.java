package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzBotStatus;
import com.jokerhub.paper.plugin.orzmc.commands.OrzConfigCommand;
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
import com.jokerhub.paper.plugin.orzmc.features.command.binding.AdminOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CooldownInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.InterceptorExecutor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PlayerOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
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
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicy;
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

/**
 * 功能模块。
 *
 * <p>将所有 Feature 服务集中创建，并注册 Bukkit 事件监听器和命令。
 * 依赖所有其他模块创建完毕后才构造。</p>
 */
public final class FeatureModule implements ServiceModule {

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
        Map<String, CommandExecutor> commandHandlers = Map.of(
                "tpbow",
                new OrzTPBow(platform.serverFacade(), teleportBowService),
                "guide",
                new OrzGuideBook(guideService),
                "menu",
                new OrzMenuCommand(menuCommandService),
                "bot",
                new OrzBotStatus(botModule.botStatusService(), botModule.botMessageService()),
                "portal",
                new OrzPortalCommand(portalCommandService));

        ConfigurationSection cmdSection =
                platform.configService().getConfig("config").getConfigurationSection("command_policies");
        if (cmdSection == null) {
            FileConfiguration legacy = platform.configService().loadFile("commands.yml");
            cmdSection = legacy != null ? legacy.getConfigurationSection("commands") : null;
        }
        Map<String, CommandExecutor> enhanced = new HashMap<>();
        CommandPolicies cp = CommandPolicies.from(cmdSection);
        commandHandlers.forEach((name, exec) -> {
            CommandPolicy p = cp.policies().getOrDefault(name, new CommandPolicy(0, false));
            List<CommandInterceptor> interceptors = new ArrayList<>();
            if (!"bot".equals(name)) {
                interceptors.add(new PlayerOnlyInterceptor());
            }
            interceptors.add(new AdminOnlyInterceptor(p.adminOnly()));
            interceptors.add(new CooldownInterceptor(name, Math.max(0, p.cooldownSeconds())));
            enhanced.put(name, new InterceptorExecutor(name, exec, interceptors));
        });
        // orzmc administration command
        List<CommandInterceptor> orzmcInterceptors = new ArrayList<>();
        orzmcInterceptors.add(new AdminOnlyInterceptor(true));
        enhanced.put(
                "orzmc",
                new InterceptorExecutor(
                        "orzmc",
                        (sender, command, label, args) -> {
                            if (args.length < 1) {
                                sender.sendMessage(platform.textStyles()
                                        .error("用法: /orzmc reload [config-name] | /orzmc config <子命令>"));
                                sender.sendMessage(platform.textStyles()
                                        .info("config 子命令: list / get <路径> / set <路径> <值> / reset <路径> / dump"));
                                return true;
                            }
                            switch (args[0].toLowerCase()) {
                                case "reload" -> {
                                    if (args.length >= 2) {
                                        if (platform.configService().reloadConfig(args[1])) {
                                            sender.sendMessage(
                                                    platform.textStyles().success("配置 " + args[1] + " 已重新加载"));
                                        } else {
                                            sender.sendMessage(
                                                    platform.textStyles().error("配置 " + args[1] + " 不存在"));
                                        }
                                    } else {
                                        platform.configService().reloadAll();
                                        sender.sendMessage(platform.textStyles().success("所有配置已重新加载"));
                                    }
                                }
                                case "config" -> {
                                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                                    orzConfigCommand.onCommand(sender, command, label, subArgs);
                                }
                                default -> {
                                    sender.sendMessage(platform.textStyles().error("未知子命令: " + args[0]));
                                    sender.sendMessage(platform.textStyles().info("可用命令: reload, config"));
                                }
                            }
                            return true;
                        },
                        orzmcInterceptors));
        CommandBinder.bind(plugin, enhanced);
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

    // --- Lifecycle ---

    public void notifyServerStop() {
        serverLifecycleService.notifyServerStop();
    }
}
