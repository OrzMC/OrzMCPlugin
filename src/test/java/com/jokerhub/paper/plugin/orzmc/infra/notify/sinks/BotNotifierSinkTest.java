package com.jokerhub.paper.plugin.orzmc.infra.notify.sinks;

import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotNotifierSinkTest {

    private ServerAccess server;
    private Notifier notifier;
    private Server bukkitServer;
    private BotNotifierSink sink;

    @BeforeEach
    void setUp() {
        server = mock(ServerAccess.class);
        notifier = mock(Notifier.class);
        bukkitServer = mock(Server.class);
        lenient().when(server.server()).thenReturn(bukkitServer);
        sink = new BotNotifierSink(server, notifier);
    }

    @Test
    void server_delegatesToBukkitServer() {
        Component msg = Component.text("test");
        sink.server(msg);
        verify(bukkitServer).sendMessage(msg);
    }

    @Test
    void event_delegatesToNotifier() {
        var env = MessageEnvelope.publicMessage("m");
        sink.event("player_join", env);
        verify(notifier).routeEvent("player_join", env);
    }
}
