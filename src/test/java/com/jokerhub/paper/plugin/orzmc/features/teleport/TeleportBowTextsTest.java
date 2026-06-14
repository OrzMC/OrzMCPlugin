package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class TeleportBowTextsTest extends ServiceTestBase {

    @Test
    void logText_withContent_includesPrefix() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        when(styles.tpbowPrefix()).thenReturn(Component.text("[传送弓]"));

        TeleportBowTexts texts = new TeleportBowTexts(styles);
        Component result = texts.logText("传送完成!");

        String plain = PlainTextComponentSerializer.plainText().serialize(result);
        assertTrue(plain.contains("[传送弓]"));
        assertTrue(plain.contains("传送完成!"));
    }

    @Test
    void logText_emptyContent_returnsEmpty() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        TeleportBowTexts texts = new TeleportBowTexts(styles);

        assertEquals(Component.empty(), texts.logText(""));
    }
}
