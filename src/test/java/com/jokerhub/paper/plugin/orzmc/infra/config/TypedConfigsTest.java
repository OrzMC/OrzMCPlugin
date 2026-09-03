package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EntityTeleportConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TypedConfigsTest {
    @Test
    public void testEntityTeleportConfigMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("entity_teleport_enabled", true);
        cfg.set("entity_teleport_whitelist", List.of("VILLAGER", "TAMEABLE"));

        EntityTeleportConfig ec = EntityTeleportConfig.from(cfg);
        Assertions.assertTrue(ec.enabled());
        Assertions.assertEquals(List.of("VILLAGER", "TAMEABLE"), ec.whitelist());
    }

    @Test
    public void testEntityTeleportConfigEmptyWhitelistFallsBackToDefaults() {
        // 根级 false + 空白名单 → 回退内置 16 项默认白名单（config.yml 未配置/清空时语义）
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("entity_teleport_enabled", false);
        cfg.set("entity_teleport_whitelist", List.of());

        EntityTeleportConfig ec = EntityTeleportConfig.from(cfg);
        Assertions.assertFalse(ec.enabled());
        Assertions.assertEquals(EntityTeleportConfig.DEFAULT_ENTITY_TELEPORT_WHITELIST, ec.whitelist());
        Assertions.assertEquals(16, ec.whitelist().size());
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

        TntConfig tc = TntConfig.from(cfg);
        Assertions.assertTrue(tc.enable());
        Assertions.assertTrue(tc.enableRespawnAnchor());
        Assertions.assertEquals(10, tc.placeCooldownSeconds());
        // notify_throttle_ms 已移出 TntConfig（ThrottledNotifier 直接读原始配置），此处仅验证未知 key 不破坏解析
        Assertions.assertEquals(1, tc.whitelistRegions().size());
        Assertions.assertEquals(List.of("CREEPER", "FIREBALL"), tc.exemptEntities());
    }

    @Test
    public void testBotConfigMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("cmd_prompt_char", "!");
        cfg.set("discord_server_link", "https://discord.gg/example");
        cfg.set("qq_group_id", "123");

        BotConfig bot = BotConfig.from(cfg);
        Assertions.assertEquals("!", bot.cmdPromptChar());
        Assertions.assertEquals("https://discord.gg/example", bot.discordServerLink());
        Assertions.assertEquals("123", bot.qqGroupId());
    }

    @Test
    public void testMaintenanceConfigMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("optimize_enabled", true);
        cfg.set("optimize_tick_time_threshold", 600L);
        cfg.set("backup_retention_count", 12);

        MaintenanceConfig maintenance = MaintenanceConfig.from(cfg);
        Assertions.assertTrue(maintenance.optimizeEnabled());
        Assertions.assertEquals(600L, maintenance.optimizeTickTimeThreshold());
        Assertions.assertEquals(12, maintenance.backupRetentionCount());
    }

    @Test
    public void testWhitelistConfigMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("force_whitelist", false);
        cfg.set("cleanup_inactive_days", 30);
        cfg.set("pagination_delay_ticks", 9);

        WhitelistConfig whitelist = WhitelistConfig.from(cfg);
        Assertions.assertFalse(whitelist.forceWhitelist());
        Assertions.assertEquals(30, whitelist.cleanupInactiveDays());
        Assertions.assertEquals(9, whitelist.paginationDelayTicks());
    }
}
