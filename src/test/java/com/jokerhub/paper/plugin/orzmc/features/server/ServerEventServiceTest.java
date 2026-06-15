package com.jokerhub.paper.plugin.orzmc.features.server;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.exception.ServerException;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import net.kyori.adventure.text.Component;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerEventServiceTest extends ServiceTestBase {

    @Mock
    private ServerFeedbackService feedbackService;

    @Mock
    private WorldMaintenanceService maintenanceService;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private Notifier notifier;

    @Mock
    private ServerLoadEvent loadEvent;

    private ServerEventService service;

    @BeforeEach
    void setUp() {
        service = new ServerEventService(feedbackService, maintenanceService, configs, notifier);
    }

    @Test
    void handleException_forwardsToAlertService() {
        ServerException exception = new ServerException("err", new RuntimeException("cause"));

        // handleException creates ExceptionAlertService internally; it delegates to notifier
        when(configs.renderEvent(eq("exception_alert"), anyMap())).thenReturn(MessageEnvelope.publicMessage("err"));

        service.handleException(exception);

        verify(notifier).event(eq("exception_alert"), any(MessageEnvelope.class));
    }

    @Test
    void handleServerLoad_sendsNotification() {
        when(feedbackService.buildServerLoadMessage(loadEvent)).thenReturn("server loaded");
        when(configs.renderEvent(eq("server_load"), anyMap()))
                .thenReturn(MessageEnvelope.publicMessage("server loaded"));

        service.handleServerLoad(loadEvent);

        verify(notifier).event(eq("server_load"), any(MessageEnvelope.class));
    }

    @Test
    void applyMaintenanceMotd_whenNotRunning_doesNothing() {
        when(maintenanceService.isRunning()).thenReturn(false);

        ServerListPingEvent pingEvent = mock(ServerListPingEvent.class);
        service.applyMaintenanceMotd(pingEvent);

        verify(pingEvent, never()).motd(any());
    }

    @Test
    void applyMaintenanceMotd_whenRunning_setsMotd() {
        when(maintenanceService.isRunning()).thenReturn(true);
        when(feedbackService.buildMaintenanceMotd()).thenReturn(Component.text("维护中"));

        ServerListPingEvent pingEvent = mock(ServerListPingEvent.class);
        service.applyMaintenanceMotd(pingEvent);

        verify(pingEvent).motd(any(Component.class));
    }
}
