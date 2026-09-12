package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class QqPlatformConfigTest {

    @Test
    void missingSection_isDisabled() {
        assertFalse(QqPlatformConfig.from(null).usable());
    }

    @Test
    void disabledByDefault_whenKeysMissing() {
        assertFalse(QqPlatformConfig.from(new YamlConfiguration()).usable());
    }

    @Test
    void enabledButBlankCredentials_notUsable() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);

        QqPlatformConfig cfg = QqPlatformConfig.from(yaml);
        assertFalse(cfg.usable(), "凭据缺失不可用");
    }

    @Test
    void enabledWithCredentials_usable() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("app_id", "app-1");
        yaml.set("client_secret", "secret-1");

        assertTrue(QqPlatformConfig.from(yaml).usable());
    }

    @Test
    void maxTextBytes_defaultsAndOverrides() {
        YamlConfiguration base = new YamlConfiguration();
        base.set("enabled", true);
        base.set("app_id", "app-1");
        base.set("client_secret", "secret-1");
        // 未配置 → 默认防御性上限（2026-09 实测 ≥150KB，32KB 仅为异常模板兜底）
        assertEquals(
                QqPlatformConfig.DEFAULT_MAX_TEXT_BYTES,
                QqPlatformConfig.from(base).maxTextBytes());

        // 实测后可调；显式 0 表示不分段
        base.set("max_text_bytes", 1200);
        assertEquals(1200, QqPlatformConfig.from(base).maxTextBytes());
        base.set("max_text_bytes", 0);
        assertEquals(0, QqPlatformConfig.from(base).maxTextBytes());
    }

    @Test
    void platformProxyOverridesGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("app_id", "app-1");
        section.set("client_secret", "secret-1");
        org.bukkit.configuration.ConfigurationSection platformProxy = section.createSection("proxy");
        platformProxy.set("enabled", true);
        platformProxy.set("host", "plat.proxy");
        platformProxy.set("port", 7891);
        QqPlatformConfig cfg = QqPlatformConfig.from(section, global);
        assertTrue(cfg.usable());
        assertTrue(cfg.proxy().effective());
        assertEquals("plat.proxy", cfg.proxy().host());
        assertEquals(7891, cfg.proxy().port());
    }

    @Test
    void noPlatformProxy_fallsBackToGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("app_id", "app-1");
        section.set("client_secret", "secret-1");
        QqPlatformConfig cfg = QqPlatformConfig.from(section, global);
        assertTrue(cfg.proxy().effective());
        assertEquals("global.proxy", cfg.proxy().host());
    }

    @Test
    void noGlobalNoPlatform_direct() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("app_id", "app-1");
        section.set("client_secret", "secret-1");
        QqPlatformConfig cfg = QqPlatformConfig.from(section, null);
        assertTrue(cfg.usable());
        assertFalse(cfg.proxy().effective());
    }

    @Test
    void singleArgFrom_parsesPlatformProxy() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("app_id", "app-1");
        section.set("client_secret", "secret-1");
        org.bukkit.configuration.ConfigurationSection platformProxy = section.createSection("proxy");
        platformProxy.set("enabled", true);
        platformProxy.set("host", "plat.proxy");
        platformProxy.set("port", 7892);
        QqPlatformConfig cfg = QqPlatformConfig.from(section);
        assertTrue(cfg.proxy().effective());
        assertEquals("plat.proxy", cfg.proxy().host());
    }

    @Test
    void threeArgConvenience_directProxy() {
        QqPlatformConfig cfg = new QqPlatformConfig(true, "app-1", "secret-1");
        assertTrue(cfg.usable());
        assertFalse(cfg.proxy().effective());
    }
}
