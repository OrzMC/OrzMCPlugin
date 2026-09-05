package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** FeishuPlatformConfig 单测：enabled/凭据齐备 usable 判定、字段缺失/空凭据禁用、null 段禁用。 */
class FeishuPlatformConfigTest {

    @Test
    void usable_requiresEnabledAndCredentials() {
        assertTrue(new FeishuPlatformConfig(true, "cli-a", "s-1").usable());
        assertFalse(new FeishuPlatformConfig(false, "cli-a", "s-1").usable(), "未 enabled → 不可用");
        assertFalse(new FeishuPlatformConfig(true, "", "s-1").usable(), "app_id 空 → 不可用");
        assertFalse(new FeishuPlatformConfig(true, "cli-a", "").usable(), "app_secret 空 → 不可用");
        assertFalse(new FeishuPlatformConfig(true, "  ", "s-1").usable(), "app_id 空白 → 不可用");
    }

    @Test
    void from_readsAppIdAndAppSecret() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("platforms.feishu.enabled", true);
        yaml.set("platforms.feishu.app_id", "cli-test");
        yaml.set("platforms.feishu.app_secret", "sec-test");

        FeishuPlatformConfig cfg = FeishuPlatformConfig.from(yaml.getConfigurationSection("platforms.feishu"));
        assertEquals(true, cfg.enabled());
        assertEquals("cli-test", cfg.appId());
        assertEquals("sec-test", cfg.appSecret());
        assertTrue(cfg.usable());
    }

    @Test
    void from_missingSection_returnsDisabled() {
        assertEquals(FeishuPlatformConfig.DISABLED, FeishuPlatformConfig.from(null));
    }

    @Test
    void platformProxyOverridesGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("app_id", "cli-test");
        section.set("app_secret", "sec-test");
        org.bukkit.configuration.ConfigurationSection platformProxy = section.createSection("proxy");
        platformProxy.set("enabled", true);
        platformProxy.set("host", "plat.proxy");
        platformProxy.set("port", 7891);
        FeishuPlatformConfig cfg = FeishuPlatformConfig.from(section, global);
        assertTrue(cfg.usable());
        assertTrue(cfg.proxy().effective());
        assertEquals("plat.proxy", cfg.proxy().host());
    }

    @Test
    void noPlatformProxy_fallsBackToGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("app_id", "cli-test");
        section.set("app_secret", "sec-test");
        FeishuPlatformConfig cfg = FeishuPlatformConfig.from(section, global);
        assertTrue(cfg.proxy().effective());
        assertEquals("global.proxy", cfg.proxy().host());
    }

    @Test
    void threeArgConvenience_directProxy() {
        FeishuPlatformConfig cfg = new FeishuPlatformConfig(true, "cli-a", "s-1");
        assertTrue(cfg.usable());
        assertFalse(cfg.proxy().effective());
    }
}
