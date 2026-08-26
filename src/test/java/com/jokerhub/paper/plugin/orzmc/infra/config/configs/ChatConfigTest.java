package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class ChatConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        ChatConfig config = ChatConfig.from(null);
        assertTrue(config.enabled());
        assertEquals(20, config.maxMessagesPerMinute());
        assertTrue(config.detectLinks());
        assertTrue(config.detectRepeat());
        assertEquals(ChatConfig.DEFAULT_MESSAGE, config.message());
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(true);
        when(cfg.getInt("max_messages_per_minute", 20)).thenReturn(20);
        when(cfg.getBoolean("detect_links", true)).thenReturn(true);
        when(cfg.getBoolean("detect_repeat", true)).thenReturn(true);
        when(cfg.getString("message", ChatConfig.DEFAULT_MESSAGE)).thenReturn(null);

        ChatConfig config = ChatConfig.from(cfg);
        assertTrue(config.enabled());
        assertEquals(20, config.maxMessagesPerMinute());
        assertTrue(config.detectLinks());
        assertTrue(config.detectRepeat());
        assertEquals(ChatConfig.DEFAULT_MESSAGE, config.message());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(false);
        when(cfg.getInt("max_messages_per_minute", 20)).thenReturn(3);
        when(cfg.getBoolean("detect_links", true)).thenReturn(false);
        when(cfg.getBoolean("detect_repeat", true)).thenReturn(false);
        when(cfg.getString("message", ChatConfig.DEFAULT_MESSAGE)).thenReturn("自定义提示");

        ChatConfig config = ChatConfig.from(cfg);
        assertFalse(config.enabled());
        assertEquals(3, config.maxMessagesPerMinute());
        assertFalse(config.detectLinks());
        assertFalse(config.detectRepeat());
        assertEquals("自定义提示", config.message());
    }

    @Test
    void fromSection_clampsNonPositiveMaxToOne() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getInt("max_messages_per_minute", 20)).thenReturn(0);

        ChatConfig config = ChatConfig.from(cfg);
        assertEquals(1, config.maxMessagesPerMinute());
    }

    @Test
    void fromSection_blankMessageFallsBackToDefault() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getString("message", ChatConfig.DEFAULT_MESSAGE)).thenReturn("   ");

        ChatConfig config = ChatConfig.from(cfg);
        assertEquals(ChatConfig.DEFAULT_MESSAGE, config.message());
    }
}
