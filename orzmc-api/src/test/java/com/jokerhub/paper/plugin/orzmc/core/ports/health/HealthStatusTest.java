package com.jokerhub.paper.plugin.orzmc.core.ports.health;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealthStatusTest {

    @Test
    void entry_constructor_setsAllFields() {
        HealthStatus.Entry entry = new HealthStatus.Entry(true, false, true, true, "timeout", 1000L);
        assertTrue(entry.enabled());
        assertFalse(entry.httpOk());
        assertTrue(entry.wsConnected());
        assertTrue(entry.apiReady());
        assertEquals("timeout", entry.lastError());
        assertEquals(1000L, entry.lastUpdated());
    }

    @Test
    void entry_defaultValues() {
        HealthStatus.Entry entry = new HealthStatus.Entry(false, false, false, false, "", 0L);
        assertFalse(entry.enabled());
        assertEquals("", entry.lastError());
        assertEquals(0L, entry.lastUpdated());
    }

    @Test
    void entry_equality_sameValuesAreEqual() {
        HealthStatus.Entry e1 = new HealthStatus.Entry(true, false, true, false, "", 42L);
        HealthStatus.Entry e2 = new HealthStatus.Entry(true, false, true, false, "", 42L);
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void entry_equality_differentValuesAreNotEqual() {
        HealthStatus.Entry e1 = new HealthStatus.Entry(true, false, false, false, "", 0L);
        HealthStatus.Entry e2 = new HealthStatus.Entry(true, true, false, false, "", 0L);
        assertNotEquals(e1, e2);
    }

    @Test
    void entry_toString_containsFields() {
        HealthStatus.Entry entry = new HealthStatus.Entry(true, true, false, true, null, 99L);
        String str = entry.toString();
        assertTrue(str.contains("enabled=true"));
        assertTrue(str.contains("httpOk=true"));
        assertTrue(str.contains("lastError=null"));
    }

    @Test
    void interface_contract_getReturnsEntry() {
        // 验证接口合约：任何实现必须为任意服务名返回非 null 的 Entry
        HealthStatus status = service -> new HealthStatus.Entry(false, false, false, false, null, 0L);
        assertNotNull(status.get("qq"));
        assertNotNull(status.get("discord"));
        assertNotNull(status.get("lark"));
    }
}
