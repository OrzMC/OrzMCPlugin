package com.jokerhub.paper.plugin.orzmc.features.player;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PlayerEventServiceTest extends ServiceTestBase {

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

    @Test
    void handleGeoIpPreLogin_blocked_disallows() {
        when(configs.renderEvent(eq("geoip_block"), anyMap())).thenReturn(MessageEnvelope.publicMessage("blocked"));
        when(styles.error(anyString())).thenReturn(net.kyori.adventure.text.Component.text("error"));
        CompletableFuture<GeoIpAccessService.Decision> future =
                CompletableFuture.completedFuture(new GeoIpAccessService.Decision(false, "US", List.of("CN"), "{}"));

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", future, 1000);

        verify(loginEvent).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("geoip_block"), any(MessageEnvelope.class));
    }

    @Test
    void handleGeoIpPreLogin_allowed_doesNothing() {
        CompletableFuture<GeoIpAccessService.Decision> future =
                CompletableFuture.completedFuture(new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}"));

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", future, 1000);

        verifyNoInteractions(loginEvent, notifier, configs, styles);
    }

    @Test
    void handleGeoIpPreLogin_timeout_allowsAndWarns() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));
        CompletableFuture<GeoIpAccessService.Decision> never = new CompletableFuture<>();

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", never, 50);

        verify(logger).warning(contains("超时"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpPreLogin_lookupError_allowsAndWarns() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("error"));
        CompletableFuture<GeoIpAccessService.Decision> failed =
                CompletableFuture.failedFuture(new RuntimeException("boom"));

        service.handleGeoIpPreLogin(loginEvent, "player1", "1.2.3.4", failed, 1000);

        verify(logger).warning(contains("boom"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
        verifyNoInteractions(loginEvent);
    }

    @Test
    void handleGeoIpTimeout_logsWarningAndNotifies() {
        when(server.logger()).thenReturn(logger);
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("timeout"));

        service.handleGeoIpTimeout("player1", "1.2.3.4", 3000);

        verify(logger).warning(contains("超时"));
        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
    }

    @Test
    void handleGeoIpDecision_blocked_prettyPrintsAddressInfo() {
        when(configs.renderEvent(eq("geoip_block"), anyMap())).thenReturn(MessageEnvelope.publicMessage("blocked"));
        when(styles.error(anyString())).thenReturn(net.kyori.adventure.text.Component.text("error"));
        String compact = "{\"ip\":\"1.2.3.4\",\"country_code\":\"US\"}";

        service.handleGeoIpDecision(
                loginEvent, "player1", "1.2.3.4", new GeoIpAccessService.Decision(false, "US", List.of("CN"), compact));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs).renderEvent(eq("geoip_block"), captor.capture());
        String addressInfo = captor.getValue().get("address_info");
        assertNotNull(addressInfo, "address_info should be present");
        assertTrue(addressInfo.contains("\n  \"ip\""), "JSON should be pretty-printed, got: " + addressInfo);
    }
}
