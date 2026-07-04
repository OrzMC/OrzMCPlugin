package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class MaintenanceConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        MaintenanceConfig config = MaintenanceConfig.from(null);
        assertFalse(config.optimizeEnabled());
        assertEquals(300L, config.optimizeTickTimeThreshold());
        assertEquals(5, config.backupRetentionCount());
        assertEquals("服务器维护中，稍后再试", config.backupMaintenanceMotd());
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        // Mockito returns 0/0L/null for primitive/Object types, not the default parameter values
        when(cfg.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        when(cfg.getLong(anyString(), anyLong())).thenReturn(300L);
        when(cfg.getInt(anyString(), anyInt())).thenReturn(5);
        when(cfg.getString(anyString(), anyString())).thenReturn("服务器维护中，稍后再试");

        MaintenanceConfig config = MaintenanceConfig.from(cfg);
        assertFalse(config.optimizeEnabled());
        assertEquals(300L, config.optimizeTickTimeThreshold());
        assertEquals(5, config.backupRetentionCount());
        assertEquals("服务器维护中，稍后再试", config.backupMaintenanceMotd());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("optimize_enabled", false)).thenReturn(true);
        when(cfg.getLong("optimize_tick_time_threshold", 300L)).thenReturn(600L);
        when(cfg.getInt("backup_retention_count", 5)).thenReturn(10);
        when(cfg.getString("backup_maintenance_motd", "服务器维护中，稍后再试")).thenReturn("维护中，请稍候");

        MaintenanceConfig config = MaintenanceConfig.from(cfg);
        assertTrue(config.optimizeEnabled());
        assertEquals(600L, config.optimizeTickTimeThreshold());
        assertEquals(10, config.backupRetentionCount());
        assertEquals("维护中，请稍候", config.backupMaintenanceMotd());
    }
}
