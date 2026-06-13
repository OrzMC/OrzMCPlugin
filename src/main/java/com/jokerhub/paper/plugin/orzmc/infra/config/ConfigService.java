package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigService {
    private final OrzMC plugin;
    private final AdvancedConfigManager configManager;

    public ConfigService(OrzMC plugin) {
        this.plugin = plugin;
        this.configManager = new AdvancedConfigManager(plugin);
    }

    public void setup() {
        // Register consolidated config files
        configManager.registerConfig("config", "config.yml");
        configManager.registerConfig("bot", "bot.yml");
        configManager.registerConfig("guide_book", "guide_book.yml");
        configManager.registerConfig("templates", "templates.yml");
        configManager.registerConfig("portals", "portals.yml");
        configManager.markAlwaysSave("portals");

        // Set defaults for bot (used programmatically)
        configManager.setDefaults("bot", config -> {});
        configManager.setDefaults("guide_book", config -> {});

        // 检查 config-version，过旧则发出迁移提醒
        configManager.checkAndUpdateConfigVersion("config", 2.0);
        configManager.checkAndUpdateConfigVersion("templates", 2.0);

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
        configManager.saveDirtyConfigs();
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

    public void reloadAll() {
        for (String name : configManager.getConfigNames()) {
            configManager.reloadConfig(name);
            plugin.getLogger().info("配置已重新加载: " + name);
        }
    }

    public boolean saveConfig(String name) {
        return configManager.saveConfig(name);
    }

    /** Load a YAML file from plugin data folder without registering. For backward-compat fallback. */
    public FileConfiguration loadFile(String fileName) {
        return configManager.loadFile(fileName);
    }

    /**
     * Read a ConfigurationSection from the merged config, with fallback to old individual file.
     * Returns null if neither path has data.
     */
    public ConfigurationSection sectionOrLegacy(String mergedConfigName, String section, String legacyFileName) {
        return configManager.sectionOrLegacy(mergedConfigName, section, legacyFileName);
    }

    private void warnMissingKey(String configName, String path) {
        try {
            FileConfiguration cfg = configManager.getConfig(configName);
            if (cfg == null || !cfg.contains(path)) {
                plugin.getLogger().warning("缺失关键配置: " + configName + "." + path);
                plugin.getLogger().warning("请检查 config.yml 或 templates.yml 中是否存在该键");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("读取配置失败: " + configName + "." + path + " - " + e.getMessage());
        }
    }

    private void validateCriticalConfigs() {
        warnMissingKey("templates", "templates.player_join");
        warnMissingKey("templates", "templates.world_alias.world");
        warnMissingKey("templates", "templates.coord.unit_label");
    }
}
