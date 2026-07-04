package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigPathTest {

    @Test
    void all_containsExpectedEntries() {
        Map<String, ConfigPath> all = ConfigPath.all();
        assertNotNull(all);
        assertEquals(24, all.size());
    }

    @Test
    void all_containsWhitelistPaths() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath forceWhitelist = all.get("whitelist.force_whitelist");
        assertNotNull(forceWhitelist);
        assertEquals("config", forceWhitelist.configName());
        assertEquals(Boolean.class, forceWhitelist.type());
        assertEquals(true, forceWhitelist.defaultValue());

        ConfigPath cleanupDays = all.get("whitelist.cleanup_inactive_days");
        assertNotNull(cleanupDays);
        assertEquals(Integer.class, cleanupDays.type());
        assertEquals(90, cleanupDays.defaultValue());
    }

    @Test
    void all_containsTntPaths() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath tntEnable = all.get("tnt.enable");
        assertNotNull(tntEnable);
        assertEquals("config", tntEnable.configName());
        assertEquals(Boolean.class, tntEnable.type());
        assertEquals(false, tntEnable.defaultValue());

        ConfigPath tntCooldown = all.get("tnt.place_cooldown");
        assertNotNull(tntCooldown);
        assertEquals(Integer.class, tntCooldown.type());
        assertEquals(5, tntCooldown.defaultValue());
    }

    @Test
    void all_containsNullDefaultValues() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath discordLink = all.get("discord_server_link");
        assertNotNull(discordLink);
        assertEquals(String.class, discordLink.type());
        assertNull(discordLink.defaultValue());

        ConfigPath qqGroupId = all.get("qq_group_id");
        assertNotNull(qqGroupId);
        assertEquals(String.class, qqGroupId.type());
        assertNull(qqGroupId.defaultValue());
    }

    @Test
    void all_containsCommandPolicyPaths() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath tpbowCooldown = all.get("command_policies.tpbow.cooldown_secs");
        assertNotNull(tpbowCooldown);
        assertEquals(Integer.class, tpbowCooldown.type());
        assertEquals(3, tpbowCooldown.defaultValue());

        ConfigPath portalAdminOnly = all.get("command_policies.portal.admin_only");
        assertNotNull(portalAdminOnly);
        assertEquals(Boolean.class, portalAdminOnly.type());
        assertEquals(true, portalAdminOnly.defaultValue());
    }

    @Test
    void all_containsTemplatesPaths() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath locale = all.get("templates.locale");
        assertNotNull(locale);
        assertEquals("templates", locale.configName());
        assertEquals(String.class, locale.type());
        assertEquals("zh-CN", locale.defaultValue());

        ConfigPath scale = all.get("templates.coord.scale");
        assertNotNull(scale);
        assertEquals(Double.class, scale.type());
        assertEquals(1.0, scale.defaultValue());

        ConfigPath precision = all.get("templates.coord.precision");
        assertNotNull(precision);
        assertEquals(Integer.class, precision.type());
        assertEquals(2, precision.defaultValue());

        ConfigPath unitLabel = all.get("templates.coord.unit_label");
        assertNotNull(unitLabel);
        assertEquals(String.class, unitLabel.type());
        assertEquals("block", unitLabel.defaultValue());
    }

    @Test
    void all_entriesHaveDescriptions() {
        Map<String, ConfigPath> all = ConfigPath.all();
        for (ConfigPath entry : all.values()) {
            assertNotNull(entry.description());
            assertFalse(entry.description().isEmpty());
        }
    }

    @Test
    void all_returnsUnmodifiableMap() {
        Map<String, ConfigPath> all = ConfigPath.all();
        assertThrows(UnsupportedOperationException.class, () -> all.put("new", null));
    }

    @Test
    void all_isOrderedByConfigGroup() {
        Map<String, ConfigPath> all = ConfigPath.all();
        String[] keys = all.keySet().toArray(new String[0]);

        // whitelist entries come first
        assertTrue(keys[0].startsWith("whitelist."));
        // maintenance entries come after whitelist
        assertTrue(keys[3].startsWith("maintenance."));
        // tnt entries come after maintenance
        assertTrue(keys[7].startsWith("tnt."));
        // bot entries after command_policies
        assertTrue(keys[17].startsWith("cmd_prompt_char") || keys[17].startsWith("discord_server_link"));
    }

    @Test
    void all_containsMaintenancePaths() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath motd = all.get("maintenance.backup_maintenance_motd");
        assertNotNull(motd);
        assertEquals(String.class, motd.type());
        assertEquals("服务器维护中，稍后再试", motd.defaultValue());

        ConfigPath retention = all.get("maintenance.backup_retention_count");
        assertNotNull(retention);
        assertEquals(Integer.class, retention.type());
        assertEquals(5, retention.defaultValue());
    }
}
