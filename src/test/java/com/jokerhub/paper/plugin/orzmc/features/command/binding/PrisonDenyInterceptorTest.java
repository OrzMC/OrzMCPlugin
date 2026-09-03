package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** {@code /guide /menu /tpbow /portal /apply /rank} 等开放命令的坐牢拒绝拦截。 */
class PrisonDenyInterceptorTest {

    @SuppressWarnings("unchecked")
    private final Predicate<Player> prisonCheck = mock(Predicate.class);

    @Test
    void prisonPlayer_blockedWithDenyTip() {
        Player player = mock(Player.class);
        when(prisonCheck.test(player)).thenReturn(true);

        Component res = new PrisonDenyInterceptor(prisonCheck).preHandle(player, "guide");

        assertNotNull(res, "坐牢玩家应收到拒绝提示（非 null 触发短路）");
        verify(prisonCheck).test(player);
    }

    @Test
    void freePlayer_allowed() {
        Player player = mock(Player.class);
        when(prisonCheck.test(player)).thenReturn(false);

        Component res = new PrisonDenyInterceptor(prisonCheck).preHandle(player, "guide");

        assertNull(res, "非坐牢玩家放行（null = 不拦截）");
    }

    @Test
    void consoleSender_alwaysAllowed() {
        CommandSender console = mock(CommandSender.class);

        Component res = new PrisonDenyInterceptor(p -> true).preHandle(console, "guide");

        assertNull(res, "控制台不适用坐牢拦截");
        verifyNoInteractions(prisonCheck);
    }
}
