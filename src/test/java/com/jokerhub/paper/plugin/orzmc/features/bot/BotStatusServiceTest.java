package com.jokerhub.paper.plugin.orzmc.features.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.health.HealthStatus;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class BotStatusServiceTest {

    @Test
    void buildStatusMessage_containsBotNames() {
        OrzTextStyles styles = mock(OrzTextStyles.class);
        when(styles.warn(anyString())).then(i -> Component.text((String) i.getArgument(0)));
        when(styles.success(anyString())).then(i -> Component.text((String) i.getArgument(0)));
        when(styles.error(anyString())).then(i -> Component.text((String) i.getArgument(0)));

        HealthStatus health = mock(HealthStatus.class);
        when(health.get("qq")).thenReturn(new HealthStatus.Entry(true, true, true, false, null, 0));
        when(health.get("discord")).thenReturn(new HealthStatus.Entry(false, false, false, false, "err", 0));
        when(health.get("lark")).thenReturn(new HealthStatus.Entry(true, true, false, false, null, 0));

        BotStatusService service = new BotStatusService(styles, health);
        Component msg = service.buildStatusMessage();
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);

        assertTrue(plain.contains("QQBot"), plain);
        assertTrue(plain.contains("DiscordBot"), plain);
        assertTrue(plain.contains("LarkBot"), plain);
    }
}
