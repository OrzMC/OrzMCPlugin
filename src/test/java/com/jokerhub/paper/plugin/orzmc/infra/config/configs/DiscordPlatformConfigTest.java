package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DiscordPlatformConfigTest {

    @Test
    void missingSection_isDisabled() {
        assertFalse(DiscordPlatformConfig.from(null).usable());
    }

    @Test
    void disabledByDefault_whenKeysMissing() {
        assertFalse(DiscordPlatformConfig.from(new YamlConfiguration()).usable());
    }

    @Test
    void enabledButBlankToken_notUsable() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        assertFalse(DiscordPlatformConfig.from(yaml).usable());
    }

    @Test
    void enabledWithToken_usable() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("token", "MTIzNDU2Nzg5.Ok_ABC");
        assertTrue(DiscordPlatformConfig.from(yaml).usable());
    }

    @Test
    void platformProxyOverridesGlobal() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("enabled", true);
        global.set("host", "global.proxy");
        global.set("port", 7890);
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("token", "tok");
        ConfigurationSection platformProxy = section.createSection("proxy");
        platformProxy.set("enabled", true);
        platformProxy.set("host", "plat.proxy");
        platformProxy.set("port", 7891);
        DiscordPlatformConfig cfg = DiscordPlatformConfig.from(section, global);
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
        section.set("token", "tok");
        DiscordPlatformConfig cfg = DiscordPlatformConfig.from(section, global);
        assertTrue(cfg.proxy().effective());
        assertEquals("global.proxy", cfg.proxy().host());
    }

    @Test
    void noGlobalNoPlatform_direct() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("enabled", true);
        section.set("token", "tok");
        DiscordPlatformConfig cfg = DiscordPlatformConfig.from(section, null);
        assertTrue(cfg.usable());
        assertFalse(cfg.proxy().effective());
    }
}
