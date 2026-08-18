package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class LoginRateLimitConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        LoginRateLimitConfig config = LoginRateLimitConfig.from(null);
        assertTrue(config.enabled());
        assertEquals(5, config.maxLoginAttemptsPerMinute());
        assertEquals(3, config.maxConcurrentPerIp());
        assertTrue(config.notifyAdmins());
        assertEquals(LoginRateLimitConfig.DEFAULT_MESSAGE, config.message());
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(true);
        when(cfg.getInt("max_login_attempts_per_minute", 5)).thenReturn(5);
        when(cfg.getInt("max_concurrent_per_ip", 3)).thenReturn(3);
        when(cfg.getBoolean("notify_admins", true)).thenReturn(true);
        when(cfg.getString("message", LoginRateLimitConfig.DEFAULT_MESSAGE)).thenReturn(null);

        LoginRateLimitConfig config = LoginRateLimitConfig.from(cfg);
        assertTrue(config.enabled());
        assertEquals(5, config.maxLoginAttemptsPerMinute());
        assertEquals(3, config.maxConcurrentPerIp());
        assertTrue(config.notifyAdmins());
        assertEquals(LoginRateLimitConfig.DEFAULT_MESSAGE, config.message());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(false);
        when(cfg.getInt("max_login_attempts_per_minute", 5)).thenReturn(2);
        when(cfg.getInt("max_concurrent_per_ip", 3)).thenReturn(1);
        when(cfg.getBoolean("notify_admins", true)).thenReturn(false);
        when(cfg.getString("message", LoginRateLimitConfig.DEFAULT_MESSAGE)).thenReturn("自定义提示");

        LoginRateLimitConfig config = LoginRateLimitConfig.from(cfg);
        assertFalse(config.enabled());
        assertEquals(2, config.maxLoginAttemptsPerMinute());
        assertEquals(1, config.maxConcurrentPerIp());
        assertFalse(config.notifyAdmins());
        assertEquals("自定义提示", config.message());
    }

    @Test
    void fromSection_clampsNonPositiveToMin() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getInt("max_login_attempts_per_minute", 5)).thenReturn(0);
        when(cfg.getInt("max_concurrent_per_ip", 3)).thenReturn(0);

        LoginRateLimitConfig config = LoginRateLimitConfig.from(cfg);
        assertEquals(1, config.maxLoginAttemptsPerMinute());
        assertEquals(1, config.maxConcurrentPerIp());
    }

    @Test
    void fromSection_blankMessageFallsBackToDefault() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getString("message", LoginRateLimitConfig.DEFAULT_MESSAGE)).thenReturn("   ");

        LoginRateLimitConfig config = LoginRateLimitConfig.from(cfg);
        assertEquals(LoginRateLimitConfig.DEFAULT_MESSAGE, config.message());
    }
}
