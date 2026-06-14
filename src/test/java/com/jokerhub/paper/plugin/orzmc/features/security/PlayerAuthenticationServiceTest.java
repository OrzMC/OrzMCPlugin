package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerAuthenticationServiceTest extends ServiceTestBase {

    private final PlayerAuthenticationService service = new PlayerAuthenticationService();

    @Test
    void isAuthenticated_nullPlayer_returnsFalse() {
        assertFalse(service.isAuthenticated(null));
    }

    @Test
    void isAuthenticated_offlinePlayer_returnsFalse() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);
        assertFalse(service.isAuthenticated(player));
    }

    @Test
    void isAuthenticated_onlinePlayer_noLoginSecurity_returnsTrue() {
        // No LoginSecurity on classpath → defaults to authenticated
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        assertTrue(service.isAuthenticated(player));
    }
}
