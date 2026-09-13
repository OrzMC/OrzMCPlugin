package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.GuideBookMigrator;
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

        // IM 网关双通道（方案 im-gateway-inhouse.md）：im.yml 为只读通道配置（backend，用户手配）；
        // im_bindings.yml 为运行时绑定数据（/orzmc im bind 维护，预留）。markAlwaysSave 保证绑定写盘不丢。
        configManager.registerConfig("im", "im.yml");
        configManager.registerConfig("im_bindings", "im_bindings.yml");
        configManager.markAlwaysSave("im_bindings");

        // 注：guide_book.yml 不调用 setDefaults——该方法会 saveConfig 回写（YamlConfiguration 不保留注释），
        // 会把服主手写的注释与文档头整段抹掉。它是内容型数据文件，首次创建由 saveResource 完成即可。

        // schema 自动升级（config/templates/easybot）：旧版安装自动备份→补缺→旧默认翻转→写回版本标记，
        // 必须发生在任何 getConfig 消费之前，否则 ConfigHealthCheck 仍会对缺段持续告警。
        upgradeSchemaFiles();

        // v12 键搬迁：业务层 bot 参数（cmd_prompt_char/discord_server_link/qq_group_id）权威位置
        // 从 easybot.yml 迁至 config.yml bot: 段——升级深合并会为 config bot: 段补默认值，若不搬迁会遮蔽
        // easybot.yml 中的老装自定义值。幂等：搬迁后清掉 easybot 旧键，二次启动零动作。
        migrateBotParamsToConfig();

        // guide_book.yml 格式升级（v1 content: → v2 pages:）：运行时数据文件不进 ConfigSchema，
        // 故走代码内专门迁移——启动期、幂等、先备份 .bak 并做回读校验，失败保留原文件（详见 GuideBookMigrator）。
        migrateGuideBookFormat();

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
     * guide_book.yml 旧格式自动迁移（幂等：已是 v2 则零动作）。迁移成功后重载内存缓存，
     * 保证启动期后续消费拿到新格式内容；失败/放弃时不动文件，仅记录日志。
     */
    private void migrateGuideBookFormat() {
        File file = configManager.configFile("guide_book");
        if (file == null) {
            return;
        }
        GuideBookMigrator.Result result = new GuideBookMigrator().migrate(file.toPath(), plugin.getLogger());
        switch (result.outcome()) {
            case MIGRATED -> {
                configManager.reloadConfig("guide_book");
                plugin.getLogger().info("guide_book.yml " + result.detail());
            }
            case SKIPPED_INVALID, FAILED -> plugin.getLogger().warning("guide_book.yml " + result.detail());
            case NOT_NEEDED -> {}
        }
    }

    /**
     * 对 schema 文件执行自动升级。内置默认源取注入的 {@code resourceProvider}（生产 = 插件 jar 资源；
     * 单测可注入 classpath 资源，使「升级补默认」路径可真实复现）。
     */
    /**
     * v12 一次性键搬迁（幂等）：easybot.yml 顶层旧 bot 参数 → config.yml {@code bot:} 段。
     * 规则：config bot 段键缺失或仍为默认值时，若 easybot 旧键存在——非默认值搬入 bot 段，默认/空旧键直接清；
     * config bot 段已被用户手改（非默认）→ 以 config 为准，easybot 旧键一并清除（双读回退防御可移除）。
     */
    private void migrateBotParamsToConfig() {
        FileConfiguration config = configManager.getConfig("config");
        FileConfiguration easybot = configManager.getConfig("easybot");
        if (config == null || easybot == null) {
            return;
        }
        org.bukkit.configuration.ConfigurationSection bot = config.getConfigurationSection("bot");
        if (bot == null) {
            return;
        }
        boolean changed = false;
        changed |= migrateBotKey(bot, easybot, "cmd_prompt_char", "$");
        changed |= migrateBotKey(bot, easybot, "discord_server_link", "");
        changed |= migrateBotKey(bot, easybot, "qq_group_id", "");
        if (changed) {
            if (!configManager.saveConfig("config")) {
                plugin.getLogger().warning("config.yml bot 参数搬迁未能落盘，下次启动将重新搬迁");
            }
            if (!configManager.saveConfig("easybot")) {
                plugin.getLogger().warning("easybot.yml 旧 bot 键清理未能落盘，下次启动将重新清理");
            }
        }
    }

    private static boolean migrateBotKey(
            org.bukkit.configuration.ConfigurationSection bot,
            FileConfiguration easybot,
            String key,
            String defaultValue) {
        if (!easybot.contains(key)) {
            return false; // 旧键本就不在，无搬迁
        }
        Object old = easybot.get(key);
        String oldStr = old == null ? null : String.valueOf(old);
        // bot 段该键处于默认态（缺失或=默认）时，easybot 非默认旧值才有搬迁价值；否则以 config（用户手改）为准
        boolean botIsDefault = !bot.contains(key) || defaultValue.equals(bot.getString(key));
        if (botIsDefault && oldStr != null && !oldStr.equals(defaultValue)) {
            bot.set(key, oldStr); // 老装自定义值 → 搬入 config bot: 段
        }
        easybot.set(key, null); // 删除旧键（无论是否已搬，config 为唯一权威；未搬说明 config 手改优先）
        return true;
    }

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
