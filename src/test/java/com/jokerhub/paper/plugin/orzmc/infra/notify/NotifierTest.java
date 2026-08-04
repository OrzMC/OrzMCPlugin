package com.jokerhub.paper.plugin.orzmc.infra.notify;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotifierTest extends ServiceTestBase {

    private ServerAccess serverAccess;
    private BotMessageService botMessageService;
    private Notifier notifier;

    @BeforeEach
    void setUp() {
        serverAccess = mock(ServerAccess.class);
        botMessageService = mock(BotMessageService.class);
        notifier = new Notifier(serverAccess, botMessageService);
    }

    // ---- registerSink ----

    @Test
    void registerSink_replacesDefault() {
        CapturingSink sink = new CapturingSink();
        notifier.registerSink(sink);

        notifier.server(Component.text("hello"));
        assertEquals(1, sink.serverMessages.size());
    }

    @Test
    void registerSink_null_keepsExisting() {
        CapturingSink sink = new CapturingSink();
        notifier.registerSink(sink);
        notifier.registerSink(null); // should not replace
        notifier.server(Component.text("keep"));
        assertEquals(1, sink.serverMessages.size());
    }

    // ---- routeEvent ----

    @Test
    void routeEvent_nullEnvelope_doesNothing() {
        notifier.routeEvent("test_event", null);
        verifyNoInteractions(botMessageService);
    }

    @Test
    void routeEvent_nullKey_doesNothing() {
        notifier.routeEvent(null, MessageEnvelope.publicMessage("ignored"));
        verifyNoInteractions(botMessageService);
    }

    @Test
    void routeEvent_publicEventsUsePublicTarget() {
        MessageEnvelope env = MessageEnvelope.publicMessage("test");
        notifier.routeEvent("tnt_alert", env);
        notifier.routeEvent("geoip_block", env);
        notifier.routeEvent("whitelist_block", env);
        notifier.routeEvent("whitelist_toggle_alert", env);

        verify(botMessageService, times(4))
                .send(argThat(message -> message.targetType() == MessageEnvelope.TargetType.PUBLIC));
    }

    @Test
    void routeEvent_privateEventsUsePrivateTarget() {
        MessageEnvelope env = MessageEnvelope.publicMessage("alert");
        notifier.routeEvent("exception_alert", env);
        notifier.routeEvent("maintenance_backup_error", env);
        notifier.routeEvent("maintenance_optimize_error", env);
        notifier.routeEvent("server_maintenance_hint", env);

        verify(botMessageService, times(4))
                .send(argThat(message -> message.targetType() == MessageEnvelope.TargetType.PRIVATE));
    }

    // ---- server ----

    @Test
    void serverMessage_broadcastsViaSink() {
        Server server = mock(Server.class);
        when(serverAccess.server()).thenReturn(server);

        notifier.server(Component.text("broadcast"));
        verify(server).sendMessage(any(Component.class));
    }

    // ---- CapturingSink ----

    private static final class CapturingSink implements NotifierSink {
        private final List<String> keys = new ArrayList<>();
        private final List<MessageEnvelope> envelopes = new ArrayList<>();
        private final List<Component> serverMessages = new ArrayList<>();

        @Override
        public void server(Component message) {
            serverMessages.add(message);
        }

        @Override
        public void event(String key, MessageEnvelope envelope) {
            keys.add(key);
            envelopes.add(envelope);
        }
    }
}
