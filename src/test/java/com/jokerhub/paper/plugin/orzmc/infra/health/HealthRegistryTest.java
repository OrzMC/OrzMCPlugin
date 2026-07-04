package com.jokerhub.paper.plugin.orzmc.infra.health;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthRegistryTest {

    private HealthRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HealthRegistry();
    }

    @Test
    void get_returnsNewStatusForUnknownService() {
        HealthRegistry.Status status = registry.get("unknown");
        assertNotNull(status);
        assertFalse(status.enabled);
        assertFalse(status.httpOk);
        assertFalse(status.wsConnected);
        assertFalse(status.apiReady);
        assertNull(status.lastError);
        assertEquals(0, status.lastUpdated);
    }

    @Test
    void get_returnsSameInstanceForSameService() {
        HealthRegistry.Status s1 = registry.get("qq");
        HealthRegistry.Status s2 = registry.get("qq");
        assertSame(s1, s2);
    }

    @Test
    void setEnabled_updatesStatus() {
        registry.setEnabled("qq", true);
        assertTrue(registry.get("qq").enabled);
        assertTrue(registry.get("qq").lastUpdated > 0);
    }

    @Test
    void setHttpOk_updatesStatus() {
        registry.setHttpOk("discord", true);
        assertTrue(registry.get("discord").httpOk);
        assertTrue(registry.get("discord").lastUpdated > 0);
    }

    @Test
    void setWsConnected_updatesStatus() {
        registry.setWsConnected("lark", true);
        assertTrue(registry.get("lark").wsConnected);
        assertTrue(registry.get("lark").lastUpdated > 0);
    }

    @Test
    void setApiReady_updatesStatus() {
        registry.setApiReady("qq", true);
        assertTrue(registry.get("qq").apiReady);
        assertTrue(registry.get("qq").lastUpdated > 0);
    }

    @Test
    void setLastError_updatesStatus() {
        registry.setLastError("qq", "connection refused");
        assertEquals("connection refused", registry.get("qq").lastError);
        assertTrue(registry.get("qq").lastUpdated > 0);
    }

    @Test
    void multipleServicesAreIsolated() {
        registry.setEnabled("qq", true);
        registry.setHttpOk("discord", true);
        registry.setWsConnected("lark", true);
        registry.setApiReady("qq", true);
        registry.setLastError("discord", "timeout");

        HealthRegistry.Status qq = registry.get("qq");
        assertTrue(qq.enabled);
        assertFalse(qq.httpOk);
        assertFalse(qq.wsConnected);
        assertTrue(qq.apiReady); // setApiReady was called for "qq"
        assertNull(qq.lastError);

        HealthRegistry.Status discord = registry.get("discord");
        assertFalse(discord.enabled);
        assertTrue(discord.httpOk);
        assertFalse(discord.wsConnected);
        assertFalse(discord.apiReady);
        assertEquals("timeout", discord.lastError);

        HealthRegistry.Status lark = registry.get("lark");
        assertFalse(lark.enabled);
        assertFalse(lark.httpOk);
        assertTrue(lark.wsConnected);
        assertFalse(lark.apiReady);
        assertNull(lark.lastError);
    }

    @Test
    void togglingFieldOffResetsCorrectly() {
        registry.setEnabled("qq", true);
        assertTrue(registry.get("qq").enabled);
        registry.setEnabled("qq", false);
        assertFalse(registry.get("qq").enabled);
    }

    @Test
    void getRaw_returnsSameAsGet() {
        registry.setEnabled("test", true);
        assertTrue(registry.getRaw("test").enabled);
        assertSame(registry.get("test"), registry.getRaw("test"));
    }
}
