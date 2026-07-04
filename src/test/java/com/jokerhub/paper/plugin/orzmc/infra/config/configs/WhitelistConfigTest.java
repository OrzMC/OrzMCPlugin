package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class WhitelistConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        WhitelistConfig config = WhitelistConfig.from(null);
        assertTrue(config.forceWhitelist());
        assertEquals(90, config.cleanupInactiveDays());
        assertEquals(5, config.paginationDelayTicks());
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        // Mockito 默认返回 false/0 而非参数默认值
        when(cfg.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(cfg.getInt(anyString(), anyInt())).thenReturn(90).thenReturn(5);

        WhitelistConfig config = WhitelistConfig.from(cfg);
        assertTrue(config.forceWhitelist());
        assertEquals(90, config.cleanupInactiveDays());
        assertEquals(5, config.paginationDelayTicks());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("force_whitelist", true)).thenReturn(false);
        when(cfg.getInt("cleanup_inactive_days", 90)).thenReturn(30);
        when(cfg.getInt("pagination_delay_ticks", 5)).thenReturn(10);

        WhitelistConfig config = WhitelistConfig.from(cfg);
        assertFalse(config.forceWhitelist());
        assertEquals(30, config.cleanupInactiveDays());
        assertEquals(10, config.paginationDelayTicks());
    }
}
