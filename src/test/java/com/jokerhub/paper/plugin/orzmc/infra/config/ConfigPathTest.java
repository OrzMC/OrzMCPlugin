package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigPathTest {

    @Test
    void all_containsExpectedEntries() {
        Map<String, ConfigPath> all = ConfigPath.all();
        assertNotNull(all);
        assertEquals(68, all.size());
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

        ConfigPath tntAggregate = all.get("tnt.notify_aggregate_ms");
        assertNotNull(tntAggregate);
        assertEquals(Long.class, tntAggregate.type());
        assertEquals(3000L, tntAggregate.defaultValue());
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

        // 各组起始键按注册顺序单调递增：whitelist 最早，随后 maintenance/tnt/player_notify/
        // command_policies/easybot/templates/rank_colors/gamemode-correction/prison，
        // 末尾为后补齐的 chat/guard/login_rate_limit/exploit_hardening/geoip/entity_teleport_enabled。
        String[] groupStartPrefixes = {
            "whitelist.",
            "maintenance.",
            "tnt.",
            "player_notify.",
            "command_policies.",
            "cmd_prompt_char",
            "templates.",
            "rank_colors.",
            "gamemode-correction.",
            "prison.",
            "chat.",
            "guard.",
            "login_rate_limit.",
            "exploit_hardening.",
            "geoip.",
            "entity_teleport_enabled",
            "update."
        };
        int prev = -1;
        for (String prefix : groupStartPrefixes) {
            int idx = -1;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].startsWith(prefix)) {
                    idx = i;
                    break;
                }
            }
            assertTrue(idx > prev, prefix + " 分组应出现在上一分组之后，实际 idx=" + idx);
            prev = idx;
        }
        assertTrue(keys[0].startsWith("whitelist."), "whitelist 应为第一个分组");
    }

    @Test
    void all_containsPlayerNotifyPaths() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath window = all.get("player_notify.window_ms");
        assertNotNull(window);
        assertEquals(Long.class, window.type());
        assertEquals(1000L, window.defaultValue());

        ConfigPath maxList = all.get("player_notify.max_list_items");
        assertNotNull(maxList);
        assertEquals(Integer.class, maxList.type());
        assertEquals(6, maxList.defaultValue());
    }

    @Test
    void all_containsMaintenancePaths() {
        Map<String, ConfigPath> all = ConfigPath.all();

        ConfigPath retention = all.get("maintenance.backup_retention_count");
        assertNotNull(retention);
        assertEquals(Integer.class, retention.type());
        assertEquals(5, retention.defaultValue());

        // 维护场景文案/进度行已迁 templates.yml（maintenance_motd_*），不再是 config.yml 可注册路径
        assertNull(all.get("maintenance.backup_maintenance_motd"));
    }
}
