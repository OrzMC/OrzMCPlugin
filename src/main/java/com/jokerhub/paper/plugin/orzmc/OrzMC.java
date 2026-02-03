package com.jokerhub.paper.plugin.orzmc;

import com.jokerhub.paper.plugin.orzmc.commands.*;
import com.jokerhub.paper.plugin.orzmc.events.*;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalService;
import com.jokerhub.paper.plugin.orzmc.infra.binding.CommandBinder;
import com.jokerhub.paper.plugin.orzmc.infra.binding.EventBinder;
import com.jokerhub.paper.plugin.orzmc.infra.bot.OrzBotManager;
import com.jokerhub.paper.plugin.orzmc.infra.config.AdvancedConfigManager;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigHealthCheck;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.core.ServiceRegistry;
import com.jokerhub.paper.plugin.orzmc.utils.OrzMessageParser;
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
    public AdvancedConfigManager configManager;
    private OrzBotManager botManager;

    @Override
    public void onEnable() {
        getLogger().info("插件生效!");
        setupConfigManager();
        setupBotManager();

        ServiceRegistry.registerPortal(PortalService.defaultImpl());
        setupEventListener();
        setupCommandHandler();
        setupServerForceWhitelist();
    }

    @Override
    public void onDisable() {
        optimizeWorldOnShutdownIfNeed();
        notifyServerStop();

        tearDownBotManager();
        tearDownConfigManager();
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
    public void sendPublicMessage(String message) {
        OrzMC.debugInfo(message);
        botManager.sendMessage(message, false);
    }

    public void sendPrivateMessage(String message) {
        debugInfo(message);
        botManager.sendMessage(message, true);
    }

    public void sendToChannel(String channelKey, String message) {
        debugInfo(message);
        botManager.sendToChannel(channelKey, message);
    }

    public static void debugInfo(String msg) {
        if (!OrzDebugEvent.debug) {
            return;
        }
        OrzMC.logger().info(msg);
    }

    private void notifyServerStop() {
        String minecraftVersion = getServer().getMinecraftVersion();
        String stringBuilder =
                "Minecraft " + minecraftVersion + "\n" + "------" + "\n" + "服务停止" + "\n\n" + "停止状态无法响应命令消息";
        sendPublicMessage(stringBuilder);
    }

    private void setupServerForceWhitelist() {
        boolean forceWhitelist = false;
        try {
            forceWhitelist = configManager.getConfig("whitelist").getBoolean("force_whitelist");
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
            new OrzBowShootEvent(this),
            new OrzPlayerEvent(this),
            new OrzTPEvent(this),
            new OrzTNTEvent(this),
            new OrzMenuEvent(this),
            new OrzServerEvent(this),
            new OrzWhiteListEvent(this),
            new OrzDebugEvent(this),
            new OrzPortalEvent(this)
        };
        EventBinder.bind(this, java.util.Arrays.asList(eventListeners));
    }

    private void setupCommandHandler() {
        Map<String, CommandExecutor> commandHandlers = Map.of(
                "tpbow",
                new OrzTPBow(),
                "guide",
                new OrzGuideBook(),
                "menu",
                new OrzMenuCommand(),
                "bot",
                new OrzBotStatus(),
                "portal",
                new OrzPortalCommand());
        FileConfiguration cmdsCfg = configManager.getConfig("commands");
        Map<String, CommandExecutor> enhanced = new HashMap<>();
        TypedConfigs.CommandPolicies cp = TypedConfigs.CommandPolicies.from(cmdsCfg);
        commandHandlers.forEach((name, exec) -> {
            TypedConfigs.CommandPolicy p = cp.policies().getOrDefault(name, new TypedConfigs.CommandPolicy(0, false));
            java.util.List<com.jokerhub.paper.plugin.orzmc.infra.binding.CommandInterceptor> interceptors =
                    new java.util.ArrayList<>();
            interceptors.add(new com.jokerhub.paper.plugin.orzmc.infra.binding.AdminOnlyInterceptor(p.adminOnly()));
            interceptors.add(new com.jokerhub.paper.plugin.orzmc.infra.binding.CooldownInterceptor(
                    name, Math.max(0, p.cooldownSeconds())));
            enhanced.put(
                    name,
                    new com.jokerhub.paper.plugin.orzmc.infra.binding.InterceptorExecutor(name, exec, interceptors));
        });
        CommandBinder.bind(this, enhanced);
    }

    private void setupBotManager() {
        botManager = new OrzBotManager(this);
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                botManager.setup();
            } catch (Exception e) {
                getLogger().severe("BotManager 初始化失败: " + e.getMessage());
            }
        });
    }

    private void tearDownBotManager() {
        botManager.tearDown();
    }

    private void setupConfigManager() {
        configManager = new AdvancedConfigManager(this);
        configManager.registerConfig("config");
        configManager.registerConfig("bot");
        configManager.setDefaults("bot", config -> {
            // 配置默认值
        });
        configManager.registerConfig("guide_book");
        configManager.setDefaults("guide_book", config -> {
            // 配置默认值
        });
        configManager.registerConfig("tnt");
        configManager.setDefaults("tnt", config -> {
            if (!config.contains("notify_throttle_ms")) {
                config.set("notify_throttle_ms", 1000);
            }
        });
        configManager.registerConfig("templates");
        configManager.registerConfig("notifications");
        configManager.registerConfig("commands");
        configManager.registerConfig("maintenance");
        configManager.registerConfig("whitelist");
        configManager.registerConfig("styles");
        configManager.registerConfig("ip_whitelist");
        configManager.registerConfig("portals");
        PortalService.defaultImpl().loadFromStorage();
        configManager.setDefaults("whitelist", cfg -> {
            if (!cfg.contains("force_whitelist")) cfg.set("force_whitelist", true);
            if (!cfg.contains("cleanup_inactive_days")) cfg.set("cleanup_inactive_days", 90);
            if (!cfg.contains("pagination_delay_ticks")) cfg.set("pagination_delay_ticks", 5);
        });
        configManager.setDefaults("maintenance", cfg -> {
            if (!cfg.contains("optimize_enabled")) cfg.set("optimize_enabled", false);
            if (!cfg.contains("optimize_on_shutdown")) cfg.set("optimize_on_shutdown", false);
            if (!cfg.contains("optimize_tick_time_threshold")) cfg.set("optimize_tick_time_threshold", 300);
            if (!cfg.contains("backup_retention_count")) cfg.set("backup_retention_count", 5);
            if (!cfg.contains("backup_maintenance_motd")) cfg.set("backup_maintenance_motd", "服务器维护中，稍后再试");
        });
        validateCriticalConfigs();
        List<String> issues = ConfigHealthCheck.validateAll(configManager);
        if (!issues.isEmpty()) {
            getLogger().warning("配置健康检查发现问题:");
            for (String s : issues) {
                getLogger().warning(" - " + s);
            }
        }
    }

    private void tearDownConfigManager() {
        for (String configName : configManager.getConfigNames()) {
            configManager.saveConfig(configName);
        }
    }

    private void warnMissingKey(String configName, String path) {
        try {
            FileConfiguration cfg = configManager.getConfig(configName);
            if (cfg == null || !cfg.contains(path)) {
                String filePath = new java.io.File(getDataFolder(), configName + ".yml").getAbsolutePath();
                getLogger().warning("缺失关键配置: " + configName + "." + path);
                getLogger().warning("文件: " + filePath);
                getLogger().warning("请参考 README 的“配置拆分指南/实操示例”修复该键");
            }
        } catch (Exception e) {
            getLogger().warning("读取配置失败: " + configName + "." + path + " - " + e.getMessage());
        }
    }

    private void validateCriticalConfigs() {
        warnMissingKey("templates", "templates.player_join");
        warnMissingKey("templates", "templates.world_alias.world");
        warnMissingKey("templates", "templates.coord.unit_label");
        warnMissingKey("notifications", "notifications.tnt_alert.public.enabled");
        warnMissingKey("commands", "commands.tpbow.cooldown_secs");
        warnMissingKey("whitelist", "force_whitelist");
        warnMissingKey("whitelist", "cleanup_inactive_days");
        warnMissingKey("whitelist", "pagination_delay_ticks");
        warnMissingKey("maintenance", "optimize_enabled");
        warnMissingKey("maintenance", "optimize_tick_time_threshold");
        warnMissingKey("maintenance", "backup_retention_count");
    }

    private void optimizeWorldOnShutdownIfNeed() {
        boolean optimizeOnShutdown = false;
        boolean optimizeEnabled = false;
        try {
            optimizeOnShutdown = configManager.getConfig("maintenance").getBoolean("optimize_on_shutdown");
            optimizeEnabled = configManager.getConfig("maintenance").getBoolean("optimize_enabled");
        } catch (Exception ignored) {
        }
        if (optimizeEnabled && optimizeOnShutdown) {
            OrzMessageParser.optimizeWorldOnShutdown(msg -> getLogger().info(msg));
        }
    }
}
