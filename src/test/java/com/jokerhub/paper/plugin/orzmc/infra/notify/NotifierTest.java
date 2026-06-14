package com.jokerhub.paper.plugin.orzmc.infra.notify;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotifierTest extends ServiceTestBase {

    private ServerAccess serverAccess;
    private ConfigService configService;
    private BotMessageService botMessageService;
    private Notifier notifier;

    @BeforeEach
    void setUp() {
        serverAccess = mock(ServerAccess.class);
        configService = mock(ConfigService.class);
        botMessageService = mock(BotMessageService.class);
        notifier = new Notifier(serverAccess, configService, botMessageService);
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
    void routeEvent_noNotificationsConfig_sendsDefaultPublic() {
        // Config returns null for templates → empty notifications → publicEnabled=true by default
        YamlConfiguration cfg = new YamlConfiguration();
        when(configService.getConfig("templates")).thenReturn(cfg);

        MessageEnvelope env = MessageEnvelope.publicMessage("test");
        notifier.routeEvent("unknown_event", env);

        verify(botMessageService).send(any(MessageEnvelope.class));
    }

    @Test
    void routeEvent_notificationPolicyDisablesPublic() {
        YamlConfiguration cfg = new YamlConfiguration();
        // Configure notifications with public disabled for tnt_alert
        cfg.set("notifications.tnt_alert.public.enabled", false);
        cfg.set("notifications.tnt_alert.private.enabled", false);
        when(configService.getConfig("templates")).thenReturn(cfg);

        MessageEnvelope env = MessageEnvelope.publicMessage("alert");
        notifier.routeEvent("tnt_alert", env);

        // No sends because both public and private are disabled
        verifyNoInteractions(botMessageService);
    }

    @Test
    void routeEvent_sendsToChannel() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("notifications.tnt_alert.public.enabled", false);
        cfg.set("notifications.tnt_alert.private.enabled", false);
        cfg.set("notifications.tnt_alert.channel_key", "admin_channel");
        when(configService.getConfig("templates")).thenReturn(cfg);

        MessageEnvelope env = MessageEnvelope.publicMessage("alert");
        notifier.routeEvent("tnt_alert", env);

        // Should send to channel
        verify(botMessageService).send(any(MessageEnvelope.class));
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
