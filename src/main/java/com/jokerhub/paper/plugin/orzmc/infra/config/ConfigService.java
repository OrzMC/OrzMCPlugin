package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigService {
    private final OrzMC plugin;
    private final AdvancedConfigManager configManager;

    public ConfigService(OrzMC plugin) {
        this.plugin = plugin;
        this.configManager = new AdvancedConfigManager(plugin);
    }

    public void setup() {
        configManager.registerConfig("config");
        configManager.registerConfig("bot");
        configManager.setDefaults("bot", config -> {});
        configManager.registerConfig("guide_book");
        configManager.setDefaults("guide_book", config -> {});
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
            plugin.getLogger().warning("配置健康检查发现问题:");
            for (String s : issues) {
                plugin.getLogger().warning(" - " + s);
            }
        }
    }

    public void tearDown() {
        for (String configName : configManager.getConfigNames()) {
            configManager.saveConfig(configName);
        }
    }

    public FileConfiguration getConfig(String name) {
        return configManager.getConfig(name);
    }

    public AdvancedConfigManager manager() {
        return configManager;
    }

    public boolean reloadConfig(String name) {
        return configManager.reloadConfig(name);
    }

    public boolean saveConfig(String name) {
        return configManager.saveConfig(name);
    }

    private void warnMissingKey(String configName, String path) {
        try {
            FileConfiguration cfg = configManager.getConfig(configName);
            if (cfg == null || !cfg.contains(path)) {
                String filePath = new java.io.File(plugin.getDataFolder(), configName + ".yml").getAbsolutePath();
                plugin.getLogger().warning("缺失关键配置: " + configName + "." + path);
                plugin.getLogger().warning("文件: " + filePath);
                plugin.getLogger().warning("请参考 README 的“配置拆分指南/实操示例”修复该键");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("读取配置失败: " + configName + "." + path + " - " + e.getMessage());
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
}
