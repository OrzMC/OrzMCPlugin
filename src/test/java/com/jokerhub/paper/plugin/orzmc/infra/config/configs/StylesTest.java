package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class StylesTest {

    @Test
    void fromNull_returnsEmptyColors() {
        Styles config = Styles.from(null);
        assertTrue(config.colors().isEmpty());
    }

    @Test
    void fromWithoutColorsSection_returnsEmptyColors() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getConfigurationSection("colors")).thenReturn(null);

        Styles config = Styles.from(cfg);
        assertTrue(config.colors().isEmpty());
    }

    @Test
    void fromWithColorsSection_usesDefaultsForMissingKeys() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection colorsSection = mock(ConfigurationSection.class);

        when(cfg.getConfigurationSection("colors")).thenReturn(colorsSection);
        // Mockito doesn't honor default parameter values, so make unstubbed getString
        // calls return the default (second argument)
        when(colorsSection.getString(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        // Override specific keys
        when(colorsSection.getString("success", "#00FF00")).thenReturn("#FF0000");
        when(colorsSection.getString("error", "#FF5555")).thenReturn("#FF0000");

        Styles config = Styles.from(cfg);
        assertEquals("#FF0000", config.colors().get("success"));
        assertEquals("#55AAFF", config.colors().get("info"));
        assertEquals("#FFAA00", config.colors().get("warn"));
        assertEquals("#FF0000", config.colors().get("error"));
        assertEquals("#55FF55", config.colors().get("coord"));
        assertEquals("#FF5555", config.colors().get("player"));
        assertEquals("#AAAAAA", config.colors().get("unknown"));
        assertEquals("#FF5555", config.colors().get("tnt_alert"));
        assertEquals("#FFAA00", config.colors().get("explosion_alert"));
    }

    @Test
    void fromWithColorsSection_appliesCustomValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        ConfigurationSection colorsSection = mock(ConfigurationSection.class);

        when(cfg.getConfigurationSection("colors")).thenReturn(colorsSection);
        when(colorsSection.getString("success", "#00FF00")).thenReturn("#00AA00");
        when(colorsSection.getString("info", "#55AAFF")).thenReturn("#3366CC");
        when(colorsSection.getString("warn", "#FFAA00")).thenReturn("#FF8800");
        when(colorsSection.getString("error", "#FF5555")).thenReturn("#CC0000");
        when(colorsSection.getString("coord", "#55FF55")).thenReturn("#22FF22");
        when(colorsSection.getString("player", "#FF5555")).thenReturn("#FF0000");
        when(colorsSection.getString("unknown", "#AAAAAA")).thenReturn("#888888");
        when(colorsSection.getString("tnt_alert", "#FF5555")).thenReturn("#FF0000");
        when(colorsSection.getString("explosion_alert", "#FFAA00")).thenReturn("#FF7700");

        Styles config = Styles.from(cfg);
        assertEquals("#00AA00", config.colors().get("success"));
        assertEquals("#3366CC", config.colors().get("info"));
        assertEquals("#FF8800", config.colors().get("warn"));
        assertEquals("#CC0000", config.colors().get("error"));
        assertEquals("#22FF22", config.colors().get("coord"));
        assertEquals("#FF0000", config.colors().get("player"));
        assertEquals("#888888", config.colors().get("unknown"));
        assertEquals("#FF0000", config.colors().get("tnt_alert"));
        assertEquals("#FF7700", config.colors().get("explosion_alert"));
        assertEquals(9, config.colors().size());
    }
}
