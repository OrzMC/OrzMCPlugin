package com.jokerhub.paper.plugin.orzmc.infra.health;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.health.HealthStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthAccessorTest {

    private HealthRegistry registry;
    private HealthAccessor accessor;

    @BeforeEach
    void setUp() {
        registry = mock(HealthRegistry.class);
        accessor = new HealthAccessor(registry);
    }

    @Test
    void get_delegatesToRegistry() {
        HealthRegistry.Status status = new HealthRegistry.Status();
        status.enabled = true;
        status.httpOk = true;
        status.httpChecked = true;
        status.wsConnected = false;
        status.apiReady = true;
        status.lastError = "test error";
        status.deliveryFailed = 2;
        status.deliveryTotal = 5;
        status.deliveryTargets = List.of("telegram:player-chat", "qq:group-a");
        status.lastUpdated = 12345L;

        when(registry.getRaw("qq")).thenReturn(status);

        HealthStatus.Entry entry = accessor.get("qq");

        assertTrue(entry.enabled());
        assertTrue(entry.httpOk());
        assertTrue(entry.httpChecked());
        assertFalse(entry.wsConnected());
        assertTrue(entry.apiReady());
        assertEquals("test error", entry.lastError());
        assertEquals(2, entry.deliveryFailed());
        assertEquals(5, entry.deliveryTotal());
        assertEquals(List.of("telegram:player-chat", "qq:group-a"), entry.deliveryTargets());
        assertEquals(12345L, entry.lastUpdated());

        verify(registry).getRaw("qq");
    }

    @Test
    void get_withDifferentServiceNames() {
        HealthRegistry.Status qqStatus = new HealthRegistry.Status();
        qqStatus.enabled = true;

        HealthRegistry.Status discordStatus = new HealthRegistry.Status();
        discordStatus.enabled = false;

        when(registry.getRaw("qq")).thenReturn(qqStatus);
        when(registry.getRaw("discord")).thenReturn(discordStatus);

        assertTrue(accessor.get("qq").enabled());
        assertFalse(accessor.get("discord").enabled());

        verify(registry).getRaw("qq");
        verify(registry).getRaw("discord");
    }

    @Test
    void get_returnsDefaultsForFreshStatus() {
        when(registry.getRaw("fresh")).thenReturn(new HealthRegistry.Status());

        HealthStatus.Entry entry = accessor.get("fresh");
        assertFalse(entry.enabled());
        assertFalse(entry.httpOk());
        assertFalse(entry.httpChecked());
        assertFalse(entry.wsConnected());
        assertFalse(entry.apiReady());
        assertNull(entry.lastError());
        assertEquals(0, entry.deliveryFailed());
        assertEquals(0, entry.deliveryTotal());
        assertTrue(entry.deliveryTargets().isEmpty());
        assertEquals(0L, entry.lastUpdated());
    }
}
