package com.jokerhub.paper.plugin.orzmc.features.server;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.Map;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServerLifecycleServiceTest {

    @Mock
    private ServerFacade server;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private Notifier notifier;

    private ServerLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new ServerLifecycleService(server, configs, notifier);
    }

    @Test
    void notifyServerStop_sendsEvent() {
        Server bukkitServer = mock(Server.class);
        when(server.server()).thenReturn(bukkitServer);
        when(bukkitServer.getMinecraftVersion()).thenReturn("1.21.4");
        when(configs.renderEvent(eq("server_stop"), anyMap()))
                .thenReturn(MessageEnvelope.publicMessage("Minecraft 1.21.4"));

        service.notifyServerStop();

        verify(notifier).event(eq("server_stop"), any(MessageEnvelope.class));
    }
}
