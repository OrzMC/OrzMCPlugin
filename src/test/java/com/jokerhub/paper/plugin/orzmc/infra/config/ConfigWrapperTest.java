package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigWrapperTest {

    private FileConfiguration config;

    @BeforeEach
    void setUp() {
        config = mock(FileConfiguration.class);
    }

    @Test
    void getString_withoutPrefix_delegates() {
        when(config.getString("key")).thenReturn("value");
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals("value", wrapper.getString("key"));
    }

    @Test
    void getString_withPrefix_delegates() {
        when(config.getString("prefix.key")).thenReturn("prefixed");
        ConfigWrapper wrapper = new ConfigWrapper(config, "prefix");
        assertEquals("prefixed", wrapper.getString("key"));
    }

    @Test
    void getString_withDefault_delegates() {
        when(config.getString("key", "default")).thenReturn("fallback");
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals("fallback", wrapper.getString("key", "default"));
    }

    @Test
    void getInt_withoutPrefix_delegates() {
        when(config.getInt("key")).thenReturn(42);
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals(42, wrapper.getInt("key"));
    }

    @Test
    void getInt_withPrefix_delegates() {
        when(config.getInt("prefix.key")).thenReturn(10);
        ConfigWrapper wrapper = new ConfigWrapper(config, "prefix");
        assertEquals(10, wrapper.getInt("key"));
    }

    @Test
    void getInt_withDefault_delegates() {
        when(config.getInt("key", 0)).thenReturn(7);
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals(7, wrapper.getInt("key", 0));
    }

    @Test
    void getBoolean_withoutPrefix_delegates() {
        when(config.getBoolean("key")).thenReturn(true);
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertTrue(wrapper.getBoolean("key"));
    }

    @Test
    void getBoolean_withPrefix_delegates() {
        when(config.getBoolean("prefix.key")).thenReturn(false);
        ConfigWrapper wrapper = new ConfigWrapper(config, "prefix");
        assertFalse(wrapper.getBoolean("key"));
    }

    @Test
    void getBoolean_withDefault_delegates() {
        when(config.getBoolean("key", true)).thenReturn(true);
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertTrue(wrapper.getBoolean("key", true));
    }

    @Test
    void getDouble_withoutPrefix_delegates() {
        when(config.getDouble("key")).thenReturn(3.14);
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals(3.14, wrapper.getDouble("key"), 0.001);
    }

    @Test
    void getDouble_withPrefix_delegates() {
        when(config.getDouble("prefix.key")).thenReturn(2.5);
        ConfigWrapper wrapper = new ConfigWrapper(config, "prefix");
        assertEquals(2.5, wrapper.getDouble("key"), 0.001);
    }

    @Test
    void getDouble_withDefault_delegates() {
        when(config.getDouble("key", 1.0)).thenReturn(1.5);
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals(1.5, wrapper.getDouble("key", 1.0), 0.001);
    }

    @Test
    void getStringList_delegates() {
        when(config.getStringList("key")).thenReturn(List.of("a", "b"));
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals(List.of("a", "b"), wrapper.getStringList("key"));
    }

    @Test
    void getObject_delegates() {
        when(config.get("key")).thenReturn("value");
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertEquals("value", wrapper.getObject("key", String.class));
    }

    @Test
    void set_delegates() {
        ConfigWrapper wrapper = new ConfigWrapper(config);
        wrapper.set("key", "value");
        verify(config).set("key", "value");
    }

    @Test
    void contains_delegates() {
        when(config.contains("key")).thenReturn(true);
        ConfigWrapper wrapper = new ConfigWrapper(config);
        assertTrue(wrapper.contains("key"));
    }

    @Test
    void getSection_returnsNestedWrapper() {
        ConfigWrapper wrapper = new ConfigWrapper(config, "outer");
        ConfigWrapper section = wrapper.getSection("inner");
        section.getString("test");
        verify(config).getString("outer.inner.test");
    }
}
