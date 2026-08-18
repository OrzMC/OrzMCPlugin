package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class SecurityGuardConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        SecurityGuardConfig config = SecurityGuardConfig.from(null);
        assertTrue(config.enabled());
        assertEquals(SecurityGuardConfig.DEFAULT_BLOCKED_COMMANDS, config.blockedCommands());
        assertTrue(config.notifyAdmins());
        assertTrue(config.auditEnabled());
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(true);
        when(cfg.getBoolean("notify_admins", true)).thenReturn(true);
        when(cfg.getBoolean("audit_enabled", true)).thenReturn(true);
        when(cfg.get("blocked_commands")).thenReturn(null);

        SecurityGuardConfig config = SecurityGuardConfig.from(cfg);
        assertTrue(config.enabled());
        assertTrue(config.blockedCommands().isEmpty());
        assertTrue(config.notifyAdmins());
        assertTrue(config.auditEnabled());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(false);
        when(cfg.getBoolean("notify_admins", true)).thenReturn(false);
        when(cfg.getBoolean("audit_enabled", true)).thenReturn(false);
        when(cfg.get("blocked_commands")).thenReturn(List.of("op", "reload", "plugman reload", " stop "));

        SecurityGuardConfig config = SecurityGuardConfig.from(cfg);
        assertFalse(config.enabled());
        assertFalse(config.notifyAdmins());
        assertFalse(config.auditEnabled());
        // 去空白 + 小写归一
        assertEquals(List.of("op", "reload", "plugman reload", "stop"), config.blockedCommands());
    }

    @Test
    void fromSection_handlesNullAndBlankListItems() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.get("blocked_commands")).thenReturn(Arrays.asList("op", null, "  ", "give"));

        SecurityGuardConfig config = SecurityGuardConfig.from(cfg);
        assertEquals(List.of("op", "give"), config.blockedCommands());
    }

    @Test
    void emptyBlockedList_isRespectedNotFallback() {
        // 显式清空 deny-list 是合法配置（用户只想用目标选择器守护），不回退默认
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.get("blocked_commands")).thenReturn(List.of());

        SecurityGuardConfig config = SecurityGuardConfig.from(cfg);
        assertTrue(config.blockedCommands().isEmpty());
    }
}
