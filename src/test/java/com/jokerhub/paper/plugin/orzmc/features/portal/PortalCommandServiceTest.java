package com.jokerhub.paper.plugin.orzmc.features.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalInfo;
import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalCommandServiceTest {

    @Mock
    private PortalPort portalService;

    @Mock
    private OrzTextStyles styles;

    @Mock
    private Player player;

    private PortalCommandService service;

    @BeforeEach
    void setUp() {
        service = new PortalCommandService(portalService, styles);
    }

    @Test
    void handle_nonAdmin_returnsFailure() {
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission("orzmc.admin")).thenReturn(false);

        PortalCommandService.Result result = service.handle(player, new String[] {"host"});

        assertInstanceOf(PortalCommandService.Result.Failure.class, result);
    }

    @Test
    void handle_adminNoArgs_returnsFailure() {
        when(player.isOp()).thenReturn(true);

        PortalCommandService.Result result = service.handle(player, new String[0]);

        assertInstanceOf(PortalCommandService.Result.Failure.class, result);
    }

    @Test
    void handle_createPortal_success() {
        when(player.isOp()).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 100, 64, 200);
        when(portalService.createPortal(player, "mc.example.com", 25565))
                .thenReturn(new PortalInfo(loc, org.bukkit.Axis.X));
        when(styles.success(anyString())).thenReturn(Component.text("success"));

        PortalCommandService.Result result = service.handle(player, new String[] {"mc.example.com"});

        assertInstanceOf(PortalCommandService.Result.Success.class, result);
        verify(portalService).createPortal(player, "mc.example.com", 25565);
    }

    @Test
    void handle_createPortal_invalidPort_returnsFailure() {
        when(player.isOp()).thenReturn(true);

        PortalCommandService.Result result = service.handle(player, new String[] {"host", "not_a_port"});

        assertInstanceOf(PortalCommandService.Result.Failure.class, result);
    }

    @Test
    void handle_removePortal_success() {
        when(player.isOp()).thenReturn(true);
        when(portalService.removeByTarget("mc.example.com:25565")).thenReturn(1);
        when(styles.success(anyString())).thenReturn(Component.text("removed"));

        PortalCommandService.Result result = service.handle(player, new String[] {"remove", "mc.example.com"});

        assertInstanceOf(PortalCommandService.Result.Success.class, result);
    }

    @Test
    void handle_removePortal_noMatch() {
        when(player.isOp()).thenReturn(true);
        when(portalService.removeByTarget("mc.example.com:25565")).thenReturn(0);
        when(styles.warn(anyString())).thenReturn(Component.text("no match"));

        PortalCommandService.Result result = service.handle(player, new String[] {"remove", "mc.example.com"});

        assertInstanceOf(PortalCommandService.Result.Success.class, result);
    }
}
