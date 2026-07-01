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
import com.jokerhub.paper.plugin.orzmc.features.command.binding.BasicCommandAdapter;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CooldownInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PlayerOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.TabCompleterDelegate;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.bukkit.GameMode;
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
        ConfigurationSection cmdSection =
                platform.configService().getConfig("config").getConfigurationSection("command_policies");
        if (cmdSection == null) {
            FileConfiguration legacy = platform.configService().loadFile("commands.yml");
            cmdSection = legacy != null ? legacy.getConfigurationSection("commands") : null;
        }
        CommandPolicies cp = CommandPolicies.from(cmdSection);

        // ---- Regular commands (tpbow, guide, menu, bot, portal) ----

        registerCommand(
                plugin,
                "tpbow",
                "传送弓，射出的箭落地时会把自己传送到箭落地的位置",
                List.of("tpb"),
                new OrzTPBow(platform.serverFacade(), teleportBowService),
                cp);
        registerCommand(plugin, "guide", "获取新手教程，更快的熟悉服务器", List.of(), new OrzGuideBook(guideService), cp);
        registerCommand(plugin, "menu", "菜单展示", List.of(), new OrzMenuCommand(menuCommandService), cp);
        registerCommand(
                plugin,
                "bot",
                "查看机器人健康状态",
                List.of(),
                new OrzBotStatus(botModule.botStatusService(), botModule.botMessageService()),
                cp);
        registerCommand(
                plugin,
                "portal",
                "创建或移除传送门",
                List.of(),
                new OrzPortalCommand(portalCommandService),
                cp,
                TabCompleterDelegate.of(List.of("remove")));

        // ---- Admin commands (blacklist, config) ----

        List<CommandInterceptor> adminOnly = List.of(new AdminOnlyInterceptor(true));

        // blacklist
        plugin.registerCommand(
                "blacklist",
                "IP黑名单管理",
                List.of("bl"),
                new BasicCommandAdapter(
                        "blacklist",
                        (sender, command, label, args) -> {
                            if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
                                List<String> patterns = blacklistService.getPatterns();
                                if (patterns.isEmpty()) {
                                    sender.sendMessage(platform.textStyles().info("黑名单为空"));
                                } else {
                                    sender.sendMessage(platform.textStyles().info("当前黑名单:"));
                                    for (String p : patterns) {
                                        sender.sendMessage(platform.textStyles().info("  " + p));
                                    }
                                }
                                return true;
                            }
                            String input = args[0];
                            if (input.startsWith("-")) {
                                String pattern = input.substring(1);
                                blacklistService.remove(pattern);
                                sender.sendMessage(platform.textStyles().success("已从黑名单移除: " + pattern));
                            } else if ("add".equalsIgnoreCase(input) && args.length >= 2) {
                                blacklistService.add(args[1]);
                                sender.sendMessage(platform.textStyles().success("已添加黑名单: " + args[1]));
                            } else if ("remove".equalsIgnoreCase(input) && args.length >= 2) {
                                blacklistService.remove(args[1]);
                                sender.sendMessage(platform.textStyles().success("已从黑名单移除: " + args[1]));
                            } else {
                                blacklistService.add(input);
                                sender.sendMessage(platform.textStyles().success("已添加黑名单: " + input));
                            }
                            return true;
                        },
                        adminOnly,
                        TabCompleterDelegate.of(List.of("list", "add", "remove"))));

        // config
        List<String> configPaths = new ArrayList<>(ConfigPath.all().keySet());
        plugin.registerCommand(
                "config",
                "配置管理",
                List.of("cfg"),
                new BasicCommandAdapter("config", orzConfigCommand, adminOnly, TabCompleterDelegate.of(args -> {
                    if (args.length == 1) {
                        return List.of("list", "get", "set", "reset", "dump", "reload");
                    }
                    if (args.length == 2
                            && ("get".equals(args[0]) || "set".equals(args[0]) || "reset".equals(args[0]))) {
                        return filterPrefix(configPaths, args[1]);
                    }
                    return List.of();
                })));
    }

    private static List<String> filterPrefix(Collection<String> items, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(items);
        }
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : items) {
            if (s.startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }

    private void registerCommand(
            OrzMC plugin,
            String name,
            String description,
            List<String> aliases,
            org.bukkit.command.CommandExecutor executor,
            CommandPolicies cp) {
        registerCommand(plugin, name, description, aliases, executor, cp, null);
    }

    private void registerCommand(
            OrzMC plugin,
            String name,
            String description,
            List<String> aliases,
            org.bukkit.command.CommandExecutor executor,
            CommandPolicies cp,
            org.bukkit.command.TabCompleter tabCompleter) {
        CommandPolicy p = cp.policies().getOrDefault(name, new CommandPolicy(0, false));
        List<CommandInterceptor> interceptors = new ArrayList<>();
        if (!"bot".equals(name)) {
            interceptors.add(new PlayerOnlyInterceptor());
        }
        interceptors.add(new AdminOnlyInterceptor(p.adminOnly()));
        interceptors.add(new CooldownInterceptor(name, Math.max(0, p.cooldownSeconds())));
        plugin.registerCommand(
                name, description, aliases, new BasicCommandAdapter(name, executor, interceptors, tabCompleter));
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
