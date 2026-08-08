package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class TntConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        TntConfig config = TntConfig.from(null);
        assertFalse(config.enable());
        assertFalse(config.enableRespawnAnchor());
        assertEquals(5, config.placeCooldownSeconds());
        assertEquals(3000L, config.notifyAggregateMs());
        assertTrue(config.whitelistRegions().isEmpty());
        assertTrue(config.exemptEntities().isEmpty());
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        // Mockito returns 0/0L for primitive types, not the default parameter values
        when(cfg.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        when(cfg.getInt(anyString(), anyInt())).thenReturn(5);
        when(cfg.getLong(anyString(), anyLong())).thenReturn(1000L);
        when(cfg.getLong("notify_aggregate_ms", 3000L)).thenReturn(3000L);

        TntConfig config = TntConfig.from(cfg);
        assertFalse(config.enable());
        assertFalse(config.enableRespawnAnchor());
        assertEquals(5, config.placeCooldownSeconds());
        assertEquals(3000L, config.notifyAggregateMs());
        assertTrue(config.whitelistRegions().isEmpty());
        assertTrue(config.exemptEntities().isEmpty());
    }

    @Test
    void fromSection_nonPositiveAggregateWindow_fallsBackToDefault() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getLong("notify_aggregate_ms", 3000L)).thenReturn(0L);

        TntConfig config = TntConfig.from(cfg);

        // 0/负数窗口会让聚合退化为 1 tick → 静默关闭防刷屏，必须回退默认
        assertEquals(3000L, config.notifyAggregateMs());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enable", false)).thenReturn(true);
        when(cfg.getBoolean("enable_respawn_anchor", false)).thenReturn(true);
        when(cfg.getInt("place_cooldown", 5)).thenReturn(10);
        when(cfg.getLong("notify_aggregate_ms", 3000L)).thenReturn(4000L);

        Map<String, Object> region =
                Map.of("minX", 0, "maxX", 10, "minY", 0, "maxY", 255, "minZ", 0, "maxZ", 10, "world", "world");
        when(cfg.get("whitelist")).thenReturn(List.of(region));
        when(cfg.get("exempt_entities")).thenReturn(List.of("CREEPER", "FIREBALL"));

        TntConfig config = TntConfig.from(cfg);
        assertTrue(config.enable());
        assertTrue(config.enableRespawnAnchor());
        assertEquals(10, config.placeCooldownSeconds());
        assertEquals(4000L, config.notifyAggregateMs());
        assertEquals(1, config.whitelistRegions().size());
        assertEquals(10, config.whitelistRegions().getFirst().get("maxX"));
        assertEquals(List.of("CREEPER", "FIREBALL"), config.exemptEntities());
    }

    @Test
    void fromSection_handlesNullRawLists() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.get("whitelist")).thenReturn(null);
        when(cfg.get("exempt_entities")).thenReturn(null);

        TntConfig config = TntConfig.from(cfg);
        assertTrue(config.whitelistRegions().isEmpty());
        assertTrue(config.exemptEntities().isEmpty());
    }
}
