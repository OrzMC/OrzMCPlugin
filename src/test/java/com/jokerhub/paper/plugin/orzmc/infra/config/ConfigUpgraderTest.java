package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigUpgraderTest {

    @TempDir
    File tempDir;

    private ConfigUpgrader upgrader;

    @BeforeEach
    void setUp() {
        upgrader = new ConfigUpgrader(Logger.getLogger("OrzMC-Test"));
    }

    private static InputStream bundledResource(String name) {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        assertNotNull(in, "classpath resource missing: " + name);
        return in;
    }

    private File writeConfig(String name, String content) throws Exception {
        File file = new File(tempDir, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void legacy_noMarker_migratesAddsMissingKeysStampsAndBacksUp() throws Exception {
        File file = writeConfig("config.yml", "whitelist:\n  force_whitelist: true\n");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigUpgrader.Outcome outcome;
        try (InputStream in = bundledResource("config.yml")) {
            outcome = upgrader.upgrade(cfg, file, in);
        }

        assertEquals(ConfigUpgrader.Outcome.MIGRATED, outcome);
        assertEquals(ConfigSchema.LATEST_VERSION, cfg.getInt(ConfigSchema.VERSION_KEY));
        assertTrue(cfg.contains("chat.enabled"), "缺失的 chat 段应被补全");
        assertTrue(cfg.contains("rank_colors.colors.admin"), "缺失的 rank_colors 段应被补全");
        assertTrue(cfg.getBoolean("whitelist.force_whitelist"), "已有值不得被覆盖");

        cfg.save(file); // 模拟调用方落盘
        assertTrue(new File(tempDir, "config.yml.bak").exists(), "升级前应生成 .bak 备份");
        assertEquals(
                ConfigSchema.LATEST_VERSION,
                YamlConfiguration.loadConfiguration(file).getInt(ConfigSchema.VERSION_KEY));
    }

    @Test
    void upToDate_whenVersionMatches_doesNotTouchFile() throws Exception {
        File file = writeConfig(
                "config.yml",
                "config-version: " + ConfigSchema.LATEST_VERSION + "\nwhitelist:\n  force_whitelist: true\n");
        String before = Files.readString(file.toPath());
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = bundledResource("config.yml")) {
            assertEquals(ConfigUpgrader.Outcome.UP_TO_DATE, upgrader.upgrade(cfg, file, in));
        }

        assertFalse(new File(tempDir, "config.yml.bak").exists(), "无需升级时不应产生备份");
        assertEquals(before, Files.readString(file.toPath()), "无需升级时文件应保持不变");
    }

    @Test
    void legacy_oldMarkerTwo_isReconciledNotDowngraded() throws Exception {
        // #238 之前的老版本内置 config-version: 2，须按 legacy 对账而非误判「插件降级」
        File file = writeConfig("config.yml", "config-version: 2\nwhitelist:\n  force_whitelist: true\n");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = bundledResource("config.yml")) {
            assertEquals(ConfigUpgrader.Outcome.MIGRATED, upgrader.upgrade(cfg, file, in));
        }

        assertEquals(ConfigSchema.LATEST_VERSION, cfg.getInt(ConfigSchema.VERSION_KEY));
        assertTrue(cfg.contains("chat.enabled"));
    }

    @Test
    void downgrade_whenDiskVersionNewer_isSkippedUntouched() throws Exception {
        File file = writeConfig("config.yml", "config-version: 99\nchat:\n  enabled: false\n");
        String before = Files.readString(file.toPath());
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = bundledResource("config.yml")) {
            assertEquals(ConfigUpgrader.Outcome.DOWNGRADE_SKIPPED, upgrader.upgrade(cfg, file, in));
        }

        assertFalse(new File(tempDir, "config.yml.bak").exists());
        assertEquals(before, Files.readString(file.toPath()));
    }

    @Test
    void noDefaults_whenBundledMissing_skipsUntouched() throws Exception {
        File file = writeConfig("config.yml", "chat:\n  enabled: false\n");
        String before = Files.readString(file.toPath());
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        assertEquals(ConfigUpgrader.Outcome.NO_DEFAULTS, upgrader.upgrade(cfg, file, null));

        assertEquals(before, Files.readString(file.toPath()));
        assertFalse(cfg.contains(ConfigSchema.VERSION_KEY));
    }

    @Test
    void parseFailed_whenFileNonEmptyButConfigEmpty_skipsWithoutBackup() throws Exception {
        File file = writeConfig("config.yml", ":::: not-yaml ::::\n\tbroken");

        // 模拟 YamlConfiguration.loadConfiguration 在 YAML 损坏后得到的空配置
        FileConfiguration cfg = new YamlConfiguration();

        try (InputStream in = bundledResource("config.yml")) {
            assertEquals(ConfigUpgrader.Outcome.PARSE_FAILED, upgrader.upgrade(cfg, file, in));
        }
        assertFalse(new File(tempDir, "config.yml.bak").exists(), "疑似损坏文件不应被备份/迁移覆盖");
    }

    @Test
    void merge_keepsExistingValuesIncludingEmptyLists() throws Exception {
        // chat.max=10 不在翻转表（非旧默认非新默认，视为自定义）→ merge/flip 都应保留
        String old = "whitelist:\n"
                + "  force_whitelist: true\n"
                + "tnt:\n"
                + "  enable: false\n"
                + "  whitelist: []\n"
                + "chat:\n"
                + "  enabled: true\n"
                + "  max_messages_per_minute: 10\n";
        File file = writeConfig("config.yml", old);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = bundledResource("config.yml")) {
            assertEquals(ConfigUpgrader.Outcome.MIGRATED, upgrader.upgrade(cfg, file, in));
        }

        assertFalse(cfg.getBoolean("tnt.enable"), "已有 false 不得被覆盖");
        assertTrue(cfg.getStringList("tnt.whitelist").isEmpty(), "显式空列表不得被覆盖");
        assertTrue(cfg.getBoolean("chat.enabled"), "已有 true 不得被覆盖");
        assertEquals(10, cfg.getInt("chat.max_messages_per_minute"), "自定义值不得被 merge/翻转覆盖");
    }

    @Test
    void legacy_flipsOldDefaultsWhenEqualToOldDefault() throws Exception {
        String old = "rank_colors:\n"
                + "  tab_enabled: true\n"
                + "chat:\n"
                + "  max_messages_per_minute: 6\n"
                + "login_rate_limit:\n"
                + "  max_login_attempts_per_minute: 5\n"
                + "  max_concurrent_per_ip: 3\n"
                + "player_notify:\n"
                + "  window_ms: 3000\n";
        File file = writeConfig("config.yml", old);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = bundledResource("config.yml")) {
            assertEquals(ConfigUpgrader.Outcome.MIGRATED, upgrader.upgrade(cfg, file, in));
        }

        assertFalse(cfg.getBoolean("rank_colors.tab_enabled"), "旧默认 true 应翻转为 false");
        assertEquals(20, cfg.getInt("chat.max_messages_per_minute"), "旧默认 6 应翻转为 20");
        assertEquals(20, cfg.getInt("login_rate_limit.max_login_attempts_per_minute"), "旧默认 5 应翻转为 20");
        assertEquals(5, cfg.getInt("login_rate_limit.max_concurrent_per_ip"), "旧默认 3 应翻转为 5");
        assertEquals(1000, cfg.getInt("player_notify.window_ms"), "旧默认 3000 应翻转为 1000");
    }

    @Test
    void legacy_keepsCustomizedValues_whileStillFlippingUntouchedOnes() throws Exception {
        String old = "rank_colors:\n"
                + "  tab_enabled: true\n" // 未自定义 → 翻转
                + "chat:\n"
                + "  max_messages_per_minute: 10\n" // 已自定义 → 保留
                + "login_rate_limit:\n"
                + "  max_concurrent_per_ip: 8\n"; // 已自定义 → 保留
        File file = writeConfig("config.yml", old);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = bundledResource("config.yml")) {
            assertEquals(ConfigUpgrader.Outcome.MIGRATED, upgrader.upgrade(cfg, file, in));
        }

        assertFalse(cfg.getBoolean("rank_colors.tab_enabled"), "未自定义的旧默认应翻转");
        assertEquals(10, cfg.getInt("chat.max_messages_per_minute"), "自定义值 10 不得被翻转/覆盖");
        assertEquals(8, cfg.getInt("login_rate_limit.max_concurrent_per_ip"), "自定义值 8 不得被翻转/覆盖");
    }

    @Test
    void legacy_extendsEntityTeleportWhitelistOnlyWhenEqualToOldDefault() throws Exception {
        // 未自定义（恰为旧 4 项默认）→ 补全为新默认白名单
        File untouched = writeConfig(
                "config.yml",
                "entity_teleport_whitelist:\n"
                        + "  - TAMEABLE\n"
                        + "  - ENDERMAN\n"
                        + "  - ARMOR_STAND\n"
                        + "  - SHULKER\n");
        FileConfiguration cfgUntouched = YamlConfiguration.loadConfiguration(untouched);
        try (InputStream in = bundledResource("config.yml")) {
            upgrader.upgrade(cfgUntouched, untouched, in);
        }
        assertTrue(cfgUntouched.getStringList("entity_teleport_whitelist").size() > 4, "旧 4 项默认应补全为新默认白名单");
        assertTrue(cfgUntouched.getStringList("entity_teleport_whitelist").contains("IRON_GOLEM"));

        // 已自定义（只有 1 项）→ 保留不覆盖
        File customized = writeConfig("custom.yml", "entity_teleport_whitelist:\n  - TAMEABLE\n");
        FileConfiguration cfgCustomized = YamlConfiguration.loadConfiguration(customized);
        try (InputStream in = bundledResource("config.yml")) {
            upgrader.upgrade(cfgCustomized, customized, in);
        }
        assertEquals(java.util.List.of("TAMEABLE"), cfgCustomized.getStringList("entity_teleport_whitelist"));
    }

    @Test
    void legacy_templates_missingTemplateKeysAreBackfilled() throws Exception {
        File file = writeConfig("templates.yml", "templates:\n  coord:\n    scale: 1.0\n");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = bundledResource("templates.yml")) {
            assertEquals(ConfigUpgrader.Outcome.MIGRATED, upgrader.upgrade(cfg, file, in));
        }

        assertTrue(cfg.contains("templates.player_join"), "缺失模板键应被补全");
        assertTrue(cfg.contains("templates.coord.precision"), "缺失的子键应被补全");
        assertEquals(1.0, cfg.getDouble("templates.coord.scale"), "已有值不得被覆盖");
        assertEquals(ConfigSchema.LATEST_VERSION, cfg.getInt(ConfigSchema.VERSION_KEY));
    }
}
