package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CommandPermissionServiceTest extends ServiceTestBase {

    private final CommandPermissionService service = new CommandPermissionService();

    @Test
    void requireAdmin_opPlayer_allowed() {
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);

        CommandPermissionService.PermissionResult r = service.requireAdmin(player);
        assertTrue(r.allowed());
    }

    @Test
    void requireAdmin_hasPermission_allowed() {
        Player player = mock(Player.class);
        when(player.hasPermission("orzmc.admin")).thenReturn(true);

        CommandPermissionService.PermissionResult r = service.requireAdmin(player);
        assertTrue(r.allowed());
    }

    @Test
    void requireAdmin_regularPlayer_denied() {
        Player player = mock(Player.class);

        CommandPermissionService.PermissionResult r = service.requireAdmin(player);
        assertFalse(r.allowed());
        assertNotNull(r.message());
    }
}
