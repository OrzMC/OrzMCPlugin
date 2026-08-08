package com.jokerhub.paper.plugin.orzmc.events;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.logging.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class OrzTPEventTest extends ServiceTestBase {

    @Mock
    private OrzMC plugin;

    @Mock
    private ServerFacade server;

    @Mock
    private EntityTeleportEvent event;

    @Mock
    private Logger logger;

    private OrzTPEvent listener;

    @BeforeEach
    void setUp() {
        listener = new OrzTPEvent(plugin, server);
    }

    @Test
    void onEntityTeleport_disallowedEntity_cancelsAndLogs() {
        Entity entity = mock(Entity.class);
        when(event.getEntity()).thenReturn(entity);
        when(entity.getName()).thenReturn("zombie");
        when(server.logger()).thenReturn(logger);

        listener.onEntityTeleport(event);

        verify(event).setCancelled(true);
        verify(logger).info(contains("实体传送被禁用"));
    }

    @Test
    void onEntityTeleport_tameableEntity_doesNothing() {
        Tameable tameable = mock(Tameable.class);
        when(event.getEntity()).thenReturn(tameable);

        listener.onEntityTeleport(event);

        verify(event, never()).setCancelled(anyBoolean());
        verifyNoInteractions(server);
    }
}
