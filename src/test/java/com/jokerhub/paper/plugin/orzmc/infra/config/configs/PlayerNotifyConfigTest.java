package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class PlayerNotifyConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        PlayerNotifyConfig config = PlayerNotifyConfig.from(null);
        assertTrue(config.enabledJoin());
        assertTrue(config.enabledQuit());
        assertTrue(config.enabledKick());
        assertEquals(1000L, config.windowMs());
        assertEquals(6, config.maxListItems());
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled_join", true)).thenReturn(true);
        when(cfg.getBoolean("enabled_quit", true)).thenReturn(true);
        when(cfg.getBoolean("enabled_kick", true)).thenReturn(true);
        when(cfg.getLong("window_ms", 1000L)).thenReturn(1000L);
        when(cfg.getInt("max_list_items", 6)).thenReturn(6);

        PlayerNotifyConfig config = PlayerNotifyConfig.from(cfg);
        assertTrue(config.enabledJoin());
        assertTrue(config.enabledQuit());
        assertTrue(config.enabledKick());
        assertEquals(1000L, config.windowMs());
        assertEquals(6, config.maxListItems());
    }

    @Test
    void fromSection_nonPositiveWindow_fallsBackToDefault() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getLong("window_ms", 1000L)).thenReturn(0L);

        PlayerNotifyConfig config = PlayerNotifyConfig.from(cfg);

        // 0/负数窗口会让聚合退化为 1 tick → 静默关闭防刷屏，必须回退默认
        assertEquals(1000L, config.windowMs());
    }

    @Test
    void fromSection_zeroMaxListItems_clampsToOne() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getInt("max_list_items", 6)).thenReturn(0);

        PlayerNotifyConfig config = PlayerNotifyConfig.from(cfg);

        assertEquals(1, config.maxListItems());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled_join", true)).thenReturn(false);
        when(cfg.getBoolean("enabled_quit", true)).thenReturn(false);
        when(cfg.getBoolean("enabled_kick", true)).thenReturn(false);
        when(cfg.getLong("window_ms", 1000L)).thenReturn(5000L);
        when(cfg.getInt("max_list_items", 6)).thenReturn(10);

        PlayerNotifyConfig config = PlayerNotifyConfig.from(cfg);
        assertFalse(config.enabledJoin());
        assertFalse(config.enabledQuit());
        assertFalse(config.enabledKick());
        assertEquals(5000L, config.windowMs());
        assertEquals(10, config.maxListItems());
    }
}
