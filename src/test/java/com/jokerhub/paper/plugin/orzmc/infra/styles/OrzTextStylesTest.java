package com.jokerhub.paper.plugin.orzmc.infra.styles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrzTextStylesTest {

    private ConfigService configService;
    private FileConfiguration templatesConfig;
    private OrzTextStyles styles;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        templatesConfig = mock(FileConfiguration.class);
        when(configService.getConfig("templates")).thenReturn(templatesConfig);
        when(templatesConfig.getConfigurationSection("styles")).thenReturn(null);
        when(configService.loadFile("styles.yml")).thenReturn(null);
        styles = new OrzTextStyles(configService);
    }

    @Test
    void colorAlertTnt_fallsBackToDefault() {
        TextColor color = styles.colorAlertTnt();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#FF5555"), color);
    }

    @Test
    void colorAlertExplosion_fallsBackToDefault() {
        TextColor color = styles.colorAlertExplosion();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#FFAA00"), color);
    }

    @Test
    void colorCoord_fallsBackToDefault() {
        TextColor color = styles.colorCoord();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#55FF55"), color);
    }

    @Test
    void colorSuccess_fallsBackToDefault() {
        TextColor color = styles.colorSuccess();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#00FF00"), color);
    }

    @Test
    void colorInfo_fallsBackToDefault() {
        TextColor color = styles.colorInfo();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#55AAFF"), color);
    }

    @Test
    void colorWarn_fallsBackToDefault() {
        TextColor color = styles.colorWarn();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#FFAA00"), color);
    }

    @Test
    void colorError_fallsBackToDefault() {
        TextColor color = styles.colorError();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#FF5555"), color);
    }

    @Test
    void colorPlayer_fallsBackToDefault() {
        TextColor color = styles.colorPlayer();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#FF5555"), color);
    }

    @Test
    void colorUnknown_fallsBackToDefault() {
        TextColor color = styles.colorUnknown();
        assertNotNull(color);
        assertEquals(TextColor.fromCSSHexString("#AAAAAA"), color);
    }

    @Test
    void prefix_returnsComponent() {
        TextComponent component = styles.prefix("test", styles.colorInfo());
        assertNotNull(component);
        assertEquals("test", component.content());
    }

    @Test
    void tntPrefix_returnsNonNull() {
        Component component = styles.tntPrefix();
        assertNotNull(component);
    }

    @Test
    void explosionPrefix_returnsNonNull() {
        Component component = styles.explosionPrefix();
        assertNotNull(component);
    }

    @Test
    void tpbowPrefix_returnsNonNull() {
        Component component = styles.tpbowPrefix();
        assertNotNull(component);
    }

    @Test
    void success_returnsComponent() {
        Component component = styles.success("done");
        assertNotNull(component);
    }

    @Test
    void info_returnsComponent() {
        Component component = styles.info("note");
        assertNotNull(component);
    }

    @Test
    void warn_returnsComponent() {
        Component component = styles.warn("caution");
        assertNotNull(component);
    }

    @Test
    void error_returnsComponent() {
        Component component = styles.error("fail");
        assertNotNull(component);
    }

    @Test
    void playerName_returnsComponent() {
        Component component = styles.playerName("Player1");
        assertNotNull(component);
    }

    @Test
    void unknownLabel_returnsNonNull() {
        Component component = styles.unknownLabel();
        assertNotNull(component);
    }
}
