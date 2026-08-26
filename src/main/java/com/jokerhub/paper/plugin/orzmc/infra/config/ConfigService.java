package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
        configManager.registerConfig("access_rules", "access_rules.yml");
        configManager.markAlwaysSave("access_rules");

        // 权限模块统一配置（两段式：config 阈值 / reviews 审核记录），
        // markAlwaysSave 保证频繁写不丢；替代原 ranks.yml 单文件存储（权限状态由 LP track 持有）
        configManager.registerConfig("permission", "permission.yml");
        configManager.markAlwaysSave("permission");

        // Register the unified bot gateway config.
        configManager.registerConfig("easybot", "easybot.yml");

        configManager.setDefaults("guide_book", config -> {});

        // 玩家名颜色（按权限等级）：仅缺失键写入默认值，不覆盖管理员修改（幂等）
        configManager.getOrSetDefault("config", "rank_colors.enabled", true);
        configManager.getOrSetDefault("config", "rank_colors.nametag_enabled", true);
        configManager.getOrSetDefault("config", "rank_colors.tab_enabled", false);
        configManager.getOrSetDefault("config", "rank_colors.op_color", "gold");
        configManager.getOrSetDefault("config", "rank_colors.colors.admin", "red");
        configManager.getOrSetDefault("config", "rank_colors.colors.builder", "green");
        configManager.getOrSetDefault("config", "rank_colors.colors.member", "aqua");
        configManager.getOrSetDefault("config", "rank_colors.colors.default", "gray");

        // 升级提示（审查 D 组）：默认值已调整的键只对新装服生效，存量 config.yml 已写入的旧值
        // 不会被覆盖。仅当存量值仍是旧默认时才提示，避免对已手动调整的服务器产生噪声。
        warnStaleDefaults();
        // 升级提示（审查 D 组）：遗留的按功能拆分 YAML 不再读取，全部合并到 config.yml。
        // 文件仍在磁盘时配置会静默失效，须显式告警。
        warnLegacyConfigFiles();

        List<String> issues = ConfigHealthCheck.validateAll(configManager);
        if (!issues.isEmpty()) {
            plugin.getLogger().warning("配置健康检查发现问题:");
            for (String s : issues) {
                plugin.getLogger().warning(" - " + s);
            }
        }
    }

    /** 旧默认值已调整的键（键 → 旧默认值）：存量服务器值仍等于旧默认时提示手动迁移。 */
    private void warnStaleDefaults() {
        FileConfiguration cfg = configManager.getConfig("config");
        if (cfg == null) return;
        Map<String, String> flipped = Map.of(
                "rank_colors.tab_enabled", "true",
                "chat.max_messages_per_minute", "6",
                "login_rate_limit.max_login_attempts_per_minute", "5",
                "login_rate_limit.max_concurrent_per_ip", "3",
                "player_notify.window_ms", "3000");
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> e : flipped.entrySet()) {
            Object v = cfg.get(e.getKey());
            if (v != null && String.valueOf(v).equals(e.getValue())) {
                stale.add(e.getKey() + "（旧值 " + e.getValue() + "）");
            }
        }
        if (!stale.isEmpty()) {
            plugin.getLogger().warning("检测到配置项仍为旧默认值（新默认见 CHANGELOG，代码不覆盖已有配置）:");
            for (String s : stale) {
                plugin.getLogger().warning(" - " + s + " —— 如需应用新默认请在 config.yml 手动修改");
            }
        }
    }

    /** 已废弃的按功能拆分配置文件名：存在即提示迁移到 config.yml 对应分段。 */
    private void warnLegacyConfigFiles() {
        String[] legacy = {
            "maintenance.yml",
            "whitelist.yml",
            "tnt.yml",
            "player_notify.yml",
            "ip_whitelist.yml",
            "guard.yml",
            "chat.yml",
            "login_rate_limit.yml",
            "exploit_hardening.yml",
            "rank_colors.yml"
        };
        java.io.File dir = configManager.dataFolder();
        if (dir == null) return;
        List<String> found = new ArrayList<>();
        for (String name : legacy) {
            if (new java.io.File(dir, name).exists()) {
                found.add(name);
            }
        }
        if (!found.isEmpty()) {
            plugin.getLogger().warning("检测到已废弃的按功能拆分配置文件（新版已全部合并到 config.yml，这些文件不再读取）: " + String.join(", ", found));
            plugin.getLogger().warning("请将其中仍需要的配置迁移到 config.yml 对应分段后删除，避免配置静默失效。");
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

    /**
     * 原子地「取配置→变更→落盘」并与并发写/重载互斥（见 {@link ConfigManager#updateConfig}）。
     * 返回是否成功落盘。
     */
    public boolean updateConfig(String name, Consumer<FileConfiguration> updater) {
        return configManager.updateConfig(name, updater);
    }

    /** 插件数据目录。 */
    public java.io.File dataFolder() {
        return configManager.dataFolder();
    }
}
