package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CooldownInterceptorTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 5})
    @Order(1)
    void preHandle_noCooldown_cooldownDisabled_returnsNull(int cooldownSecs) {
        CooldownInterceptor interceptor = new CooldownInterceptor("nocd_cmd", cooldownSecs);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        assertNull(interceptor.preHandle(player, "nocd"));
    }

    @Test
    @Order(2)
    void preHandle_firstCall_returnsNull() {
        CooldownInterceptor interceptor = new CooldownInterceptor("fresh_cmd", 5);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");

        assertNull(interceptor.preHandle(player, "fresh"));
    }

    @Test
    @Order(3)
    void preHandle_secondCallWithinCooldown_returnsTip() {
        CooldownInterceptor interceptor = new CooldownInterceptor("quick_cmd", 10);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Bob");

        // First call — warms the cache
        assertNull(interceptor.preHandle(player, "quick"));
        // Second call immediately — should be within cooldown
        assertNotNull(interceptor.preHandle(player, "quick"));
    }
}
