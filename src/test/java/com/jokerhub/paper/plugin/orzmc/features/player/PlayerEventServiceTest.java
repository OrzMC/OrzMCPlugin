package com.jokerhub.paper.plugin.orzmc.features.player;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerEventServiceTest {

    @Mock
    private ServerFacade server;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private OrzTextStyles styles;

    @Mock
    private Notifier notifier;

    @Mock
    private ThrottledNotifier throttledNotifier;

    @Mock
    private AsyncPlayerPreLoginEvent loginEvent;

    @Mock
    private Logger logger;

    private PlayerEventService service;

    @BeforeEach
    void setUp() {
        service = new PlayerEventService(server, configs, styles, notifier, throttledNotifier);
    }

    @Test
    void handleGeoIpDecision_allowed_doesNothing() {
        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}"));

        verifyNoInteractions(notifier, configs, styles);
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpDecision_blocked_sendsNotification() {
        when(configs.renderEvent(eq("geoip_block"), anyMap())).thenReturn(MessageEnvelope.publicMessage("blocked"));
        when(styles.error(anyString())).thenReturn(net.kyori.adventure.text.Component.text("error"));

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(false, "US", List.of("CN"), "{}"));

        verify(notifier).event(eq("geoip_block"), any(MessageEnvelope.class));
        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void handleGeoIpException_logsWarning() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));

        service.handleGeoIpException(new RuntimeException("lookup failed"));

        verify(logger).warning(contains("lookup failed"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
    }
}
