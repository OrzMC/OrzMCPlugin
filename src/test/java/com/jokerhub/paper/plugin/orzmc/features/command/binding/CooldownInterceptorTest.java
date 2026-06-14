package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CooldownInterceptorTest {

    @Test
    @Order(1)
    void preHandle_zeroCooldown_returnsNull() {
        CooldownInterceptor interceptor = new CooldownInterceptor("test_cmd", 0);
        Player player = mock(Player.class);
        assertNull(interceptor.preHandle(player, "test"));
    }

    @Test
    @Order(2)
    void preHandle_negativeCooldown_returnsNull() {
        CooldownInterceptor interceptor = new CooldownInterceptor("test_cmd", -1);
        Player player = mock(Player.class);
        assertNull(interceptor.preHandle(player, "test"));
    }

    @Test
    @Order(3)
    void preHandle_firstCall_returnsNull() {
        CooldownInterceptor interceptor = new CooldownInterceptor("fresh_cmd", 5);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");

        assertNull(interceptor.preHandle(player, "fresh"));
    }

    @Test
    @Order(4)
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
