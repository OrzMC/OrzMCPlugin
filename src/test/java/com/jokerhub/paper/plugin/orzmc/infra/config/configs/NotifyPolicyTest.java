package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class NotifyPolicyTest {

    @Test
    void fromNull_returnsEmptyPolicies() {
        NotifyPolicy.Notifications notifications = NotifyPolicy.Notifications.from(null);
        assertTrue(notifications.policies().isEmpty());
    }

    @Test
    void fromEmpty_returnsEmptyPolicies() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getKeys(false)).thenReturn(Set.of());

        NotifyPolicy.Notifications notifications = NotifyPolicy.Notifications.from(cfg);
        assertTrue(notifications.policies().isEmpty());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection eventSection = mock(ConfigurationSection.class);

        when(cfg.getKeys(false)).thenReturn(Set.of("player_join"));
        when(cfg.getConfigurationSection("player_join")).thenReturn(eventSection);
        when(eventSection.getBoolean("private.enabled", false)).thenReturn(true);
        when(eventSection.getBoolean("private.admin_only", true)).thenReturn(false);
        when(eventSection.getBoolean("public.enabled", true)).thenReturn(false);
        when(eventSection.getString("channel_key", "")).thenReturn("ingame");

        NotifyPolicy.Notifications notifications = NotifyPolicy.Notifications.from(cfg);
        assertEquals(1, notifications.policies().size());

        NotifyPolicy policy = notifications.policies().get("player_join");
        assertNotNull(policy);
        assertTrue(policy.privateEnabled());
        assertFalse(policy.privateAdminOnly());
        assertFalse(policy.publicEnabled());
        assertEquals("ingame", policy.channelKey());
    }

    @Test
    void from_skipsNullSections() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getKeys(false)).thenReturn(Set.of("missing_section"));
        when(cfg.getConfigurationSection("missing_section")).thenReturn(null);

        NotifyPolicy.Notifications notifications = NotifyPolicy.Notifications.from(cfg);
        assertTrue(notifications.policies().isEmpty());
    }
}
