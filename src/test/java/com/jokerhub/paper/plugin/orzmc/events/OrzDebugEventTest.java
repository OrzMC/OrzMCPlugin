package com.jokerhub.paper.plugin.orzmc.events;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * orzdebug RCON 通道测试：游戏内/控制台走 Brigadier executes 直调（FeatureModule），
 * 本类仅监听 RemoteServerCommandEvent（RCON 不走 Brigadier）。
 *
 * <p>异步派发经 {@link OrzBaseListener#serverFacade()} 门面（Paper/Folia 兼容），
 * 因此断言目标是 {@link AsyncScheduler#runNow} 而非 BukkitScheduler。
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
    private AsyncScheduler asyncScheduler;

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
        when(server.getAsyncScheduler()).thenReturn(asyncScheduler);

        listener.rconDebugHandler(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(asyncScheduler).runNow(eq(plugin), captor.capture());
        captor.getValue().accept(mock(ScheduledTask.class));
        verify(inboundHandler).handleMessage(eq("hello $a"), eq(true), eq("RCON"), any());
    }

    @Test
    void rconDebugHandler_leadingSlash_stripped() {
        when(event.getCommand()).thenReturn("/orzdebug $l");
        when(plugin.getServer()).thenReturn(server);
        when(server.getAsyncScheduler()).thenReturn(asyncScheduler);

        listener.rconDebugHandler(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(asyncScheduler).runNow(eq(plugin), captor.capture());
        captor.getValue().accept(mock(ScheduledTask.class));
        verify(inboundHandler).handleMessage(eq("$l"), eq(true), eq("RCON"), any());
    }

    @Test
    void rconDebugHandler_emptyBody_dispatchesEmpty() {
        when(event.getCommand()).thenReturn("orzdebug   ");
        when(plugin.getServer()).thenReturn(server);
        when(server.getAsyncScheduler()).thenReturn(asyncScheduler);

        listener.rconDebugHandler(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(asyncScheduler).runNow(eq(plugin), captor.capture());
        captor.getValue().accept(mock(ScheduledTask.class));
        verify(inboundHandler).handleMessage(eq(""), eq(true), eq("RCON"), any());
    }
}
