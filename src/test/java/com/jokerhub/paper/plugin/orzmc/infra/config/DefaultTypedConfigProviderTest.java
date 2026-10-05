package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTypedConfigProviderTest {

    private ConfigService configService;
    private FileConfiguration templatesConfig;
    private DefaultTypedConfigProvider provider;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        templatesConfig = mock(FileConfiguration.class);
        when(configService.getConfig("templates")).thenReturn(templatesConfig);
        // Return the default argument for all missing paths, mimicking a real YamlConfiguration
        when(templatesConfig.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        provider = new DefaultTypedConfigProvider(configService);
    }

    @Test
    void bot_returnsBotConfig() {
        FileConfiguration configFile = mock(FileConfiguration.class);
        when(configService.getConfig("config")).thenReturn(configFile);
        // config.yml bot: 段缺失 → 回退默认
        when(configFile.getConfigurationSection("bot")).thenReturn(null);
        BotConfig result = provider.bot();
        assertNotNull(result);
        assertEquals("$", result.cmdPromptChar());
    }

    @Test
    void bot_readsConfigBotSection() {
        FileConfiguration configFile = mock(FileConfiguration.class);
        org.bukkit.configuration.ConfigurationSection botSection =
                mock(org.bukkit.configuration.ConfigurationSection.class);
        when(configService.getConfig("config")).thenReturn(configFile);
        when(configFile.getConfigurationSection("bot")).thenReturn(botSection);
        when(botSection.getString("cmd_prompt_char", "$")).thenReturn("!");
        when(botSection.getString("discord_server_link")).thenReturn("https://discord.gg/new");
        when(botSection.getString("qq_group_id")).thenReturn("123");
        BotConfig result = provider.bot();
        assertEquals("!", result.cmdPromptChar());
        assertEquals("https://discord.gg/new", result.discordServerLink());
        assertEquals("123", result.qqGroupId());
    }

    @Test
    void whitelist_returnsWhitelistConfig() {
        WhitelistConfig result = provider.whitelist();
        assertNotNull(result);
    }

    @Test
    void tnt_returnsTntConfig() {
        TntConfig result = provider.tnt();
        assertNotNull(result);
    }

    @Test
    void templateOptions_returnsTemplateOptions() {
        TemplateOptions result = provider.templateOptions();
        assertNotNull(result);
    }

    @Test
    void rankColors_returnsConfigWithDefaultsWhenSectionMissing() {
        // config 未 stub → getConfig("config") 返回 null → section() 返回 null → RankColorsConfig.from(null) 返回默认
        com.jokerhub.paper.plugin.orzmc.infra.config.configs.RankColorsConfig result = provider.rankColors();
        assertNotNull(result);
        assertTrue(result.enabled());
        assertTrue(result.nametagEnabled());
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GOLD, result.opColor());
    }

    @Test
    void renderEvent_returnsMessageEnvelope() {
        when(configService.getConfig("templates")).thenReturn(templatesConfig);
        com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope result =
                provider.renderEvent("player_join", Map.of("name", "TestPlayer"));
        assertNotNull(result);
    }

    @Test
    void renderTemplate_returnsMessageEnvelope() {
        when(configService.getConfig("templates")).thenReturn(templatesConfig);
        when(templatesConfig.getString(anyString())).thenReturn("template");
        when(templatesConfig.getString(anyString(), anyString())).thenReturn("template");
        com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope result =
                provider.renderTemplate("command_output", Map.of("name", "World"), "fallback");
        assertNotNull(result);
    }

    @Test
    void resolveTemplate_returnsString() {
        when(configService.getConfig("templates")).thenReturn(templatesConfig);
        when(templatesConfig.getString(anyString())).thenReturn("resolved");
        String result = provider.resolveTemplate("command_output", "fallback");
        assertNotNull(result);
    }
}
