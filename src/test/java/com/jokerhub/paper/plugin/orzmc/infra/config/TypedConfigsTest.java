package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TypedConfigsTest {
    @Test
    public void testMainConfigMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("force_whitelist", true);
        cfg.set("whitelist_cleanup_inactive_days", 30);
        cfg.set("whitelist_pagination_delay_ticks", 7);
        cfg.set("cmd_prompt_char", "!");
        cfg.set("optimize_enabled", true);
        cfg.set("optimize_on_shutdown", true);
        cfg.set("optimize_tick_time_threshold", 500L);
        cfg.set("backup_retention_count", 9);
        cfg.set("backup_maintenance_motd", "维护中");
        cfg.set("allow_country_code", List.of("CN", "JP"));

        TypedConfigs.MainConfig mc = TypedConfigs.MainConfig.from(cfg);
        Assertions.assertTrue(mc.forceWhitelist());
        Assertions.assertEquals(30, mc.whitelistCleanupInactiveDays());
        Assertions.assertEquals(7, mc.whitelistPaginationDelayTicks());
        Assertions.assertEquals("!", mc.cmdPromptChar());
        Assertions.assertTrue(mc.optimizeEnabled());
        Assertions.assertTrue(mc.optimizeOnShutdown());
        Assertions.assertEquals(500L, mc.optimizeTickTimeThreshold());
        Assertions.assertEquals(9, mc.backupRetentionCount());
        Assertions.assertEquals("维护中", mc.backupMaintenanceMotd());
        Assertions.assertEquals(List.of("CN", "JP"), mc.allowCountryCode());
    }

    @Test
    public void testTntConfigMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("enable", true);
        cfg.set("enable_respawn_anchor", true);
        cfg.set("place_cooldown", 10);
        cfg.set("notify_throttle_ms", 2000L);
        cfg.set(
                "whitelist",
                List.of(java.util.Map.of(
                        "minX", 0, "maxX", 10, "minY", 0, "maxY", 255, "minZ", 0, "maxZ", 10, "world", "world")));
        cfg.set("exempt_entities", List.of("CREEPER", "FIREBALL"));

        TypedConfigs.TntConfig tc = TypedConfigs.TntConfig.from(cfg);
        Assertions.assertTrue(tc.enable());
        Assertions.assertTrue(tc.enableRespawnAnchor());
        Assertions.assertEquals(10, tc.placeCooldownSeconds());
        Assertions.assertEquals(2000L, tc.notifyThrottleMs());
        Assertions.assertEquals(1, tc.whitelistRegions().size());
        Assertions.assertEquals(List.of("CREEPER", "FIREBALL"), tc.exemptEntities());
    }
}
