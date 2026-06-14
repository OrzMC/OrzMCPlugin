package com.jokerhub.paper.plugin.orzmc.features.portal;

import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalEventServiceTest {

    @Mock private ServerFacade server;
    @Mock private PortalPort portalService;
    @Mock private PlayerPortalEvent event;
    @Mock private Player player;
    @Mock private Location location;

    private PortalEventService service;

    @BeforeEach
    void setUp() {
        service = new PortalEventService(server, portalService);
    }

    @Test
    void handle_unauthenticatedPlayer_cancelsEvent() {
        when(event.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(false);

        service.handle(event);

        verify(event).setCancelled(true);
    }

    @Test
    void handle_noPortalTarget_doesNothing() {
        when(event.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(event.getFrom()).thenReturn(location);
        when(portalService.findTarget(location)).thenReturn(null);

        service.handle(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void handle_hasPortalTarget_executesTransfer() {
        when(event.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Steve");
        when(event.getFrom()).thenReturn(location);
        when(portalService.findTarget(location)).thenReturn("hub:25565");

        service.handle(event);

        verify(event).setCancelled(true);
        verify(server).executeConsoleCommands(any(), eq("transfer hub 25565 Steve"));
    }
}
