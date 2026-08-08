package com.jokerhub.paper.plugin.orzmc.events;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import org.bukkit.Server;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * orzdebug RCON 通道测试：游戏内/控制台走 Brigadier executes 直调（FeatureModule），
 * 本类仅监听 RemoteServerCommandEvent（RCON 不走 Brigadier）。
 */
class OrzDebugEventTest extends ServiceTestBase {

    @Mock
    private OrzMC plugin;

    @Mock
    private BotInboundHandler inboundHandler;

    @Mock
    private RemoteServerCommandEvent event;

    @Mock
    private Server server;

    @Mock
    private BukkitScheduler scheduler;

    private OrzDebugEvent listener;

    @BeforeEach
    void setUp() {
        listener = new OrzDebugEvent(plugin, inboundHandler);
    }

    @Test
    void rconDebugHandler_nonDebugCommand_ignored() {
        when(event.getCommand()).thenReturn("say hello");

        listener.rconDebugHandler(event);

        verifyNoInteractions(inboundHandler, plugin);
    }

    @Test
    void rconDebugHandler_debugCommand_dispatchesAsync() {
        when(event.getCommand()).thenReturn("orzdebug hello $a");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        listener.rconDebugHandler(event);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(eq(plugin), captor.capture());
        captor.getValue().run();
        verify(inboundHandler).handleMessage(eq("hello $a"), eq(true), eq("RCON"), any());
    }

    @Test
    void rconDebugHandler_leadingSlash_stripped() {
        when(event.getCommand()).thenReturn("/orzdebug $l");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        listener.rconDebugHandler(event);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(eq(plugin), captor.capture());
        captor.getValue().run();
        verify(inboundHandler).handleMessage(eq("$l"), eq(true), eq("RCON"), any());
    }

    @Test
    void rconDebugHandler_emptyBody_dispatchesEmpty() {
        when(event.getCommand()).thenReturn("orzdebug   ");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        listener.rconDebugHandler(event);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(eq(plugin), captor.capture());
        captor.getValue().run();
        verify(inboundHandler).handleMessage(eq(""), eq(true), eq("RCON"), any());
    }
}
