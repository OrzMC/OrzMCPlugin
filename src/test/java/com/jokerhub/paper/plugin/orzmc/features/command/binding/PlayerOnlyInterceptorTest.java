package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerOnlyInterceptorTest {

    private final PlayerOnlyInterceptor interceptor = new PlayerOnlyInterceptor();

    @Test
    void preHandle_player_returnsNull() {
        Player player = mock(Player.class);
        assertNull(interceptor.preHandle(player, "test"));
    }

    @Test
    void preHandle_console_returnsTip() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        Component result = interceptor.preHandle(console, "test");
        assertNotNull(result);
    }

    @Test
    void preHandle_consoleMessage_containsPlayerTip() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(interceptor.preHandle(console, "test"));
        assertTrue(msg.contains("玩家"));
    }
}
