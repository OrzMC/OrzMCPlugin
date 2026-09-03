package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** {@link UpdateConfig} 解析测试：config.yml {@code update} 段的默认值、通道归一化与负间隔裁剪。 */
class UpdateConfigTest {

    private static UpdateConfig parse(String yaml) {
        YamlConfiguration root = YamlConfiguration.loadConfiguration(new StringReader(yaml == null ? "" : yaml));
        ConfigurationSection section = root.getConfigurationSection("update");
        return UpdateConfig.from(section);
    }

    @Test
    void fromNull_returnsDefaults() {
        UpdateConfig cfg = UpdateConfig.from(null);
        assertTrue(cfg.enabled(), "默认开启自更新");
        assertEquals("release", cfg.channel(), "默认走正式版通道");
        assertEquals(12L, cfg.checkIntervalHours(), "默认 12 小时检查一次");
        assertFalse(cfg.autoDownload(), "默认不自动下载（先提示，管理员 /update now 手动）");
    }

    @Test
    void fromMissingSection_returnsDefaults() {
        UpdateConfig cfg = parse("");
        assertTrue(cfg.enabled());
        assertEquals("release", cfg.channel());
        assertEquals(12L, cfg.checkIntervalHours());
        assertFalse(cfg.autoDownload());
    }

    @Test
    void fromFullSection_parsesAllFields() {
        UpdateConfig cfg = parse("update:\n"
                + "  enabled: false\n"
                + "  channel: beta\n"
                + "  check_interval_hours: 6\n"
                + "  auto_download: true\n");
        assertFalse(cfg.enabled());
        assertEquals("beta", cfg.channel());
        assertEquals(6L, cfg.checkIntervalHours());
        assertTrue(cfg.autoDownload());
    }

    @Test
    void fromEmptySection_returnsDefaults() {
        UpdateConfig cfg = parse("update:\n");
        assertTrue(cfg.enabled());
        assertEquals("release", cfg.channel());
        assertEquals(12L, cfg.checkIntervalHours());
        assertFalse(cfg.autoDownload());
    }

    @Test
    void channel_upperBeta_normalizesToLowercaseBeta() {
        UpdateConfig cfg = parse("update:\n  channel: BETA\n");
        assertEquals("beta", cfg.channel());
    }

    @Test
    void channel_unknown_fallsBackToRelease() {
        UpdateConfig cfg = parse("update:\n  channel: nightly\n");
        assertEquals("release", cfg.channel(), "未知通道回退正式版");
    }

    @Test
    void channel_null_fallsBackToRelease() {
        UpdateConfig cfg = parse("update:\n  channel:\n");
        assertEquals("release", cfg.channel());
    }

    @Test
    void negativeInterval_clampedToZero() {
        UpdateConfig cfg = parse("update:\n  check_interval_hours: -5\n");
        assertEquals(0L, cfg.checkIntervalHours(), "负间隔裁剪为 0（只检查一次）");
    }
}
