package com.jokerhub.paper.plugin.orzmc.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import org.bukkit.Server;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class OrzDebugEventTest extends ServiceTestBase {

    @Mock
    private OrzMC plugin;

    @Mock
    private BotInboundHandler inboundHandler;

    @Mock
    private ServerCommandEvent event;

    @Mock
    private Server server;

    @Mock
    private BukkitScheduler scheduler;

    private OrzDebugEvent listener;

    @BeforeEach
    void setUp() {
        listener = new OrzDebugEvent(plugin, inboundHandler);
        OrzDebugEvent.debug = false;
    }

    @AfterEach
    void tearDown() {
        OrzDebugEvent.debug = false;
    }

    @Test
    void cmdDebugHandler_nonDebugCommand_ignored() {
        when(event.getCommand()).thenReturn("say hello");

        listener.cmdDebugHandler(event);

        assertFalse(OrzDebugEvent.debug);
        verifyNoInteractions(inboundHandler, plugin);
    }

    @Test
    void cmdDebugHandler_debugCommand_setsFlagAndDispatchesAsync() {
        when(event.getCommand()).thenReturn("orzdebug hello $a");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        listener.cmdDebugHandler(event);

        assertTrue(OrzDebugEvent.debug);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(eq(plugin), captor.capture());
        captor.getValue().run();
        verify(inboundHandler).handleMessage(eq("hello $a"), eq(true), any());
    }

    @Test
    void cmdDebugHandler_debugCommandEmptyBody_dispatchesEmpty() {
        when(event.getCommand()).thenReturn("orzdebug   ");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        listener.cmdDebugHandler(event);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskAsynchronously(eq(plugin), captor.capture());
        captor.getValue().run();
        verify(inboundHandler).handleMessage(eq(""), eq(true), any());
    }
}
