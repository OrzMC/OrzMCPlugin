package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigService {
    private final OrzMC plugin;
    private final AdvancedConfigManager configManager;
    private final ConfigUpgrader configUpgrader;
    private final Function<String, InputStream> resourceProvider;

    public ConfigService(OrzMC plugin) {
        this(plugin, plugin::getResource);
    }

    /**
     * 测试可注入的默认资源读取器（默认为插件 jar 资源）。生产只走 {@link #ConfigService(OrzMC)}。
     */
    ConfigService(OrzMC plugin, Function<String, InputStream> resourceProvider) {
        this.plugin = plugin;
        this.configManager = new AdvancedConfigManager(plugin);
        this.configUpgrader = new ConfigUpgrader(plugin.getLogger());
        this.resourceProvider = resourceProvider;
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

        // schema 自动升级（config/templates/easybot）：旧版安装自动备份→补缺→旧默认翻转→写回版本标记，
        // 必须发生在任何 getConfig 消费之前，否则 ConfigHealthCheck 仍会对缺段持续告警。
        upgradeSchemaFiles();

        // 遗留的按功能拆分 YAML 不再读取，全部合并到 config.yml。
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

    /**
     * 对 schema 文件执行自动升级。内置默认源取注入的 {@code resourceProvider}（生产 = 插件 jar 资源；
     * 单测可注入 classpath 资源，使「升级补默认」路径可真实复现）。
     */
    private void upgradeSchemaFiles() {
        for (Map.Entry<String, String> entry : ConfigSchema.SCHEMA_FILES.entrySet()) {
            String name = entry.getKey();
            FileConfiguration cfg = configManager.getConfig(name);
            File file = configManager.configFile(name);
            if (cfg == null || file == null) {
                continue;
            }
            try (InputStream bundled = resourceProvider.apply(entry.getValue())) {
                ConfigUpgrader.Outcome outcome = configUpgrader.upgrade(cfg, file, bundled);
                if (outcome == ConfigUpgrader.Outcome.MIGRATED && !configManager.saveConfig(name)) {
                    // 磁盘未落盘则下次启动会重新执行迁移（幂等，不丢数据），但需显式告知避免误以为已生效
                    plugin.getLogger().warning("配置升级结果未能落盘（" + entry.getValue() + "），下次启动将重新执行升级");
                }
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("配置升级失败（" + entry.getValue() + "）: " + e.getMessage());
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
