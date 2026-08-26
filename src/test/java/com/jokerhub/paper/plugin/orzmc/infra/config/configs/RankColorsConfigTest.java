package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

/** RankColorsConfig 解析测试：缺失默认 / 命名色 / hex 吸附 / 无效回退 / 开关。 */
class RankColorsConfigTest {

    @Test
    void fromNull_returnsDefaults() {
        RankColorsConfig cfg = RankColorsConfig.from(null);
        assertTrue(cfg.enabled());
        assertTrue(cfg.nametagEnabled());
        assertFalse(cfg.tabEnabled());
        assertEquals(NamedTextColor.GOLD, cfg.opColor());
        assertEquals(NamedTextColor.GRAY, cfg.colors().get("default"));
        assertEquals(NamedTextColor.AQUA, cfg.colors().get("member"));
        assertEquals(NamedTextColor.GREEN, cfg.colors().get("builder"));
        assertEquals(NamedTextColor.RED, cfg.colors().get("admin"));
    }

    @Test
    void fromSection_parsesNamedColorsAndFlags() {
        ConfigurationSection colors = mock(ConfigurationSection.class);
        when(colors.getString("admin")).thenReturn("red");
        when(colors.getString("builder")).thenReturn("green");
        when(colors.getString("member")).thenReturn("blue");
        when(colors.getString("default")).thenReturn("gray");
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(false);
        when(cfg.getBoolean("nametag_enabled", true)).thenReturn(false);
        when(cfg.getBoolean("tab_enabled", false)).thenReturn(false);
        when(cfg.getString("op_color")).thenReturn("gold");
        when(cfg.getConfigurationSection("colors")).thenReturn(colors);

        RankColorsConfig parsed = RankColorsConfig.from(cfg);
        assertFalse(parsed.enabled());
        assertFalse(parsed.nametagEnabled());
        assertFalse(parsed.tabEnabled());
        assertEquals(NamedTextColor.GOLD, parsed.opColor());
        assertEquals(NamedTextColor.RED, parsed.colors().get("admin"));
        assertEquals(NamedTextColor.GREEN, parsed.colors().get("builder"));
        assertEquals(NamedTextColor.BLUE, parsed.colors().get("member"));
        assertEquals(NamedTextColor.GRAY, parsed.colors().get("default"));
    }

    @Test
    void fromSection_hexSnapsToNearestNamedColor() {
        ConfigurationSection colors = mock(ConfigurationSection.class);
        when(colors.getString("admin")).thenReturn("#FF5555"); // 与 RED 完全一致
        when(colors.getString("builder")).thenReturn(null);
        when(colors.getString("member")).thenReturn(null);
        when(colors.getString("default")).thenReturn(null);
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(true);
        when(cfg.getBoolean("nametag_enabled", true)).thenReturn(true);
        when(cfg.getBoolean("tab_enabled", false)).thenReturn(true);
        when(cfg.getString("op_color")).thenReturn(null);
        when(cfg.getConfigurationSection("colors")).thenReturn(colors);

        RankColorsConfig parsed = RankColorsConfig.from(cfg);
        assertEquals(NamedTextColor.RED, parsed.colors().get("admin"));
        // 未配置键保留默认
        assertEquals(NamedTextColor.GREEN, parsed.colors().get("builder"));
        assertEquals(NamedTextColor.AQUA, parsed.colors().get("member"));
        assertEquals(NamedTextColor.GRAY, parsed.colors().get("default"));
    }

    @Test
    void fromSection_invalidColor_fallsBackToDefault() {
        ConfigurationSection colors = mock(ConfigurationSection.class);
        when(colors.getString("admin")).thenReturn("not-a-color");
        when(colors.getString("builder")).thenReturn(null);
        when(colors.getString("member")).thenReturn(null);
        when(colors.getString("default")).thenReturn(null);
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        when(cfg.getBoolean("enabled", true)).thenReturn(true);
        when(cfg.getBoolean("nametag_enabled", true)).thenReturn(true);
        when(cfg.getBoolean("tab_enabled", false)).thenReturn(true);
        when(cfg.getString("op_color")).thenReturn("not-a-color");
        when(cfg.getConfigurationSection("colors")).thenReturn(colors);

        RankColorsConfig parsed = RankColorsConfig.from(cfg);
        assertEquals(NamedTextColor.RED, parsed.colors().get("admin")); // 解析失败回退默认
        assertEquals(NamedTextColor.GRAY, parsed.colors().get("default"));
        assertEquals(NamedTextColor.GOLD, parsed.opColor()); // op 解析失败回退默认
    }
}
