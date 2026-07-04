package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class WhitelistKickMessageTest {

    @Test
    void fromNull_returnsDefaults() {
        WhitelistKickMessage config = WhitelistKickMessage.from(null);
        assertEquals("", config.title());
        assertEquals("", config.qqGroupId());
        assertTrue(config.ups().isEmpty());
    }

    @Test
    void fromWithoutKickMessageSection_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getConfigurationSection("kick_message")).thenReturn(null);

        WhitelistKickMessage config = WhitelistKickMessage.from(cfg);
        assertEquals("", config.title());
        assertEquals("", config.qqGroupId());
        assertTrue(config.ups().isEmpty());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection kickSection = mock(ConfigurationSection.class);

        when(cfg.getConfigurationSection("kick_message")).thenReturn(kickSection);
        when(kickSection.getString("title", "")).thenReturn("您已被踢出服务器");
        when(kickSection.getString("qq_group_id", "")).thenReturn("123456");
        List<Map<?, ?>> upsList = new ArrayList<>();
        upsList.add(Map.of("name", "Player1", "platform", "QQ"));
        when(kickSection.getMapList("ups")).thenReturn(upsList);

        WhitelistKickMessage config = WhitelistKickMessage.from(cfg);
        assertEquals("您已被踢出服务器", config.title());
        assertEquals("123456", config.qqGroupId());
        assertEquals(1, config.ups().size());
        assertEquals("Player1", config.ups().getFirst().name());
        assertEquals("QQ", config.ups().getFirst().platform());
    }

    @Test
    void from_skipsEmptyItems() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection kickSection = mock(ConfigurationSection.class);

        when(cfg.getConfigurationSection("kick_message")).thenReturn(kickSection);
        when(kickSection.getString("title", "")).thenReturn("Title");
        when(kickSection.getString("qq_group_id", "")).thenReturn("123");
        List<Map<?, ?>> upsList = new ArrayList<>();
        upsList.add(Map.of("name", "Player1", "platform", "QQ"));
        upsList.add(Map.of("name", "", "platform", ""));
        upsList.add(Map.of("name", "Player2", "platform", "Discord"));
        when(kickSection.getMapList("ups")).thenReturn(upsList);

        WhitelistKickMessage config = WhitelistKickMessage.from(cfg);
        assertEquals(2, config.ups().size());
        assertEquals("Player1", config.ups().get(0).name());
        assertEquals("Player2", config.ups().get(1).name());
    }

    @Test
    void from_handlesNullMapList() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection kickSection = mock(ConfigurationSection.class);

        when(cfg.getConfigurationSection("kick_message")).thenReturn(kickSection);
        when(kickSection.getString("title", "")).thenReturn("Title");
        when(kickSection.getString("qq_group_id", "")).thenReturn("123");
        when(kickSection.getMapList("ups")).thenReturn(null);

        WhitelistKickMessage config = WhitelistKickMessage.from(cfg);
        assertTrue(config.ups().isEmpty());
    }

    @Test
    void from_handlesNullMapInList() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection kickSection = mock(ConfigurationSection.class);

        when(cfg.getConfigurationSection("kick_message")).thenReturn(kickSection);
        when(kickSection.getString("title", "")).thenReturn("Title");
        when(kickSection.getString("qq_group_id", "")).thenReturn("123");
        List<Map<?, ?>> upsList = new ArrayList<>();
        upsList.add(Map.of("name", "Player1", "platform", "QQ"));
        upsList.add(null);
        when(kickSection.getMapList("ups")).thenReturn(upsList);

        WhitelistKickMessage config = WhitelistKickMessage.from(cfg);
        assertEquals(1, config.ups().size());
    }

    @Test
    void from_handlesMissingNameAndPlatform() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection kickSection = mock(ConfigurationSection.class);

        when(cfg.getConfigurationSection("kick_message")).thenReturn(kickSection);
        when(kickSection.getString("title", "")).thenReturn("Title");
        when(kickSection.getString("qq_group_id", "")).thenReturn("123");
        List<Map<?, ?>> upsList = new ArrayList<>();
        upsList.add(Map.of("name", "Player1"));
        upsList.add(Map.of("platform", "Discord"));
        when(kickSection.getMapList("ups")).thenReturn(upsList);

        WhitelistKickMessage config = WhitelistKickMessage.from(cfg);
        assertEquals(2, config.ups().size());
        assertEquals("Player1", config.ups().get(0).name());
        assertEquals("", config.ups().get(0).platform());
        assertEquals("", config.ups().get(1).name());
        assertEquals("Discord", config.ups().get(1).platform());
    }
}
