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
        configManager.registerConfig("guide_book", "guide_book.yml");
        configManager.registerConfig("templates", "templates.yml");
        configManager.registerConfig("portals", "portals.yml");
        configManager.markAlwaysSave("portals");
        configManager.registerConfig("ip_blacklist", "ip_blacklist.yml");
        configManager.markAlwaysSave("ip_blacklist");

        // 权限模块统一配置（两段式：config 阈值 / reviews 审核记录），
        // markAlwaysSave 保证频繁写不丢；替代原 ranks.yml 单文件存储（权限状态由 LP track 持有）
        configManager.registerConfig("permission", "permission.yml");
        configManager.markAlwaysSave("permission");

        // Register the unified bot gateway config.
        configManager.registerConfig("easybot", "easybot.yml");

        configManager.setDefaults("guide_book", config -> {});

        // 检查 config-version，过旧则发出迁移提醒
        configManager.checkAndUpdateConfigVersion("config", 2.0);
        configManager.checkAndUpdateConfigVersion("templates", 2.0);

        // 玩家名颜色（按权限等级）：仅缺失键写入默认值，不覆盖管理员修改（幂等）
        configManager.getOrSetDefault("config", "rank_colors.enabled", true);
        configManager.getOrSetDefault("config", "rank_colors.nametag_enabled", true);
        configManager.getOrSetDefault("config", "rank_colors.tab_enabled", true);
        configManager.getOrSetDefault("config", "rank_colors.op_color", "gold");
        configManager.getOrSetDefault("config", "rank_colors.colors.admin", "red");
        configManager.getOrSetDefault("config", "rank_colors.colors.builder", "green");
        configManager.getOrSetDefault("config", "rank_colors.colors.member", "aqua");
        configManager.getOrSetDefault("config", "rank_colors.colors.default", "gray");

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

    /** 插件数据目录（定位遗留文件用）。 */
    public java.io.File dataFolder() {
        return configManager.dataFolder();
    }

    /**
     * Read a ConfigurationSection from the merged config, with fallback to old individual file.
     * Returns null if neither path has data.
     */
    public ConfigurationSection sectionOrLegacy(String mergedConfigName, String section, String legacyFileName) {
        return configManager.sectionOrLegacy(mergedConfigName, section, legacyFileName);
    }
}
