package com.jokerhub.paper.plugin.orzmc.infra.health;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.health.HealthStatus;
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
        status.wsConnected = false;
        status.apiReady = true;
        status.lastError = "test error";
        status.lastUpdated = 12345L;

        when(registry.getRaw("qq")).thenReturn(status);

        HealthStatus.Entry entry = accessor.get("qq");

        assertTrue(entry.enabled());
        assertTrue(entry.httpOk());
        assertFalse(entry.wsConnected());
        assertTrue(entry.apiReady());
        assertEquals("test error", entry.lastError());
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
        assertFalse(entry.wsConnected());
        assertFalse(entry.apiReady());
        assertNull(entry.lastError());
        assertEquals(0L, entry.lastUpdated());
    }
}
