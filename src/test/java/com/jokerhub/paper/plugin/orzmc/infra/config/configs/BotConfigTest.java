package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class BotConfigTest {

    @Test
    void fromNull_throwsNpe() {
        assertThrows(NullPointerException.class, () -> BotConfig.from(null));
    }

    @Test
    void fromEmpty_returnsDefaults() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        // Mockito 默认返回 null 而非参数默认值
        when(cfg.getString(anyString(), anyString())).thenReturn("$");
        when(cfg.getString(anyString())).thenReturn(null);

        BotConfig config = BotConfig.from(cfg);
        assertEquals("$", config.cmdPromptChar());
        assertNull(config.discordServerLink());
        assertNull(config.qqGroupId());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getString("cmd_prompt_char", "$")).thenReturn("!");
        when(cfg.getString("discord_server_link")).thenReturn("https://discord.gg/example");
        when(cfg.getString("qq_group_id")).thenReturn("12345");

        BotConfig config = BotConfig.from(cfg);
        assertEquals("!", config.cmdPromptChar());
        assertEquals("https://discord.gg/example", config.discordServerLink());
        assertEquals("12345", config.qqGroupId());
    }
}
