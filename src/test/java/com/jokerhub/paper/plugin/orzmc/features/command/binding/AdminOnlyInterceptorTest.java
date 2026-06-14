package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class AdminOnlyInterceptorTest {

    @Test
    void preHandle_notAdminOnly_returnsNull() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(false);
        Player player = mock(Player.class);
        assertNull(interceptor.preHandle(player, "test"));
    }

    @Test
    void preHandle_adminOnly_opPlayer_returnsNull() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(true);
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        assertNull(interceptor.preHandle(player, "test"));
    }

    @Test
    void preHandle_adminOnly_nonOpPlayer_returnsMessage() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(true);
        Player player = mock(Player.class);
        assertNotNull(interceptor.preHandle(player, "test"));
    }

    @Test
    void preHandle_adminOnly_console_returnsNull() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(true);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        assertNull(interceptor.preHandle(console, "test"));
    }
}
