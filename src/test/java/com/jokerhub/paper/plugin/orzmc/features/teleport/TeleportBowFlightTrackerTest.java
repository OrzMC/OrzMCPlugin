package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TeleportBowFlightTrackerTest extends ServiceTestBase {

    private static final int MAX_FLIGHT_TICKS = 400;

    private ServerFacade server;
    private ForceLoadedChunkLease lease;
    private BukkitTask task;
    private Arrow arrow;
    private Player player;
    private World world;
    private Location location;

    private TeleportBowFlightTracker tracker;

    @BeforeEach
    void setUp() {
        server = mock(ServerFacade.class);
        lease = mock(ForceLoadedChunkLease.class);
        task = mock(BukkitTask.class);
        when(server.runTaskTimer(any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

        arrow = mock(Arrow.class);
        player = mock(Player.class);
        world = mock(World.class);
        location = mock(Location.class);

        when(arrow.getWorld()).thenReturn(world);
        when(arrow.getLocation()).thenReturn(location);
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, 0));
        when(arrow.isDead()).thenReturn(false);
        when(arrow.isValid()).thenReturn(true);
        when(arrow.isInBlock()).thenReturn(false);
        when(player.isOnline()).thenReturn(true);
        when(location.getY()).thenReturn(64.0);
        when(world.getMinHeight()).thenReturn(-64);

        tracker = new TeleportBowFlightTracker(server, lease);
    }

    @Test
    void start_schedulesPerTickTimer() {
        tracker.start(arrow, player);

        verify(server).runTaskTimer(any(Runnable.class), eq(1L), eq(1L));
    }

    @Test
    void tick_flying_loadsCurrentAndPredictedChunks() {
        // bx=175 -> chunk 10；预测 px=177 -> chunk 11（vel.x=1, PRED=2）。
        when(location.getBlockX()).thenReturn(175);
        when(location.getBlockZ()).thenReturn(16);
        when(arrow.getVelocity()).thenReturn(new Vector(1, 0, 0));
        when(world.isChunkLoaded(10, 1)).thenReturn(true);
        when(world.isChunkLoaded(11, 1)).thenReturn(true);

        tracker.start(arrow, player);
        tracker.tick();

        verify(lease).acquire(world, 10, 1);
        verify(lease).acquire(world, 11, 1);
        verify(task, never()).cancel();
    }

    @Test
    void tick_fastVelocity_loadsPathChunksWithoutGaps() {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(arrow.getVelocity()).thenReturn(new Vector(3, 0, 0));
        // 满弦 ~3 格/tick × PRED=8 = 24 格 → px=40 → chunk 2，路径覆盖 chunk 1..2（含中间区块）。
        when(world.isChunkLoaded(1, 1)).thenReturn(true);
        when(world.isChunkLoaded(2, 1)).thenReturn(true);

        tracker.start(arrow, player);
        tracker.tick();

        verify(lease).acquire(world, 1, 1);
        verify(lease).acquire(world, 2, 1);
    }

    @Test
    void tick_alreadyAcquiredChunk_notRequestedTwice() {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(world.isChunkLoaded(1, 1)).thenReturn(true);

        tracker.start(arrow, player);
        tracker.tick();
        tracker.tick();

        // 当前与预测重合为同一区块，只 acquire 一次。
        verify(lease, times(1)).acquire(world, 1, 1);
    }

    @Test
    void tick_arrowInBlock_stops() {
        when(arrow.isInBlock()).thenReturn(true);

        tracker.start(arrow, player);
        tracker.tick();

        verify(task).cancel();
        verifyNoMoreInteractions(lease);
    }

    @Test
    void tick_arrowDead_stops() {
        when(arrow.isDead()).thenReturn(true);

        tracker.start(arrow, player);
        tracker.tick();

        verify(task).cancel();
        verifyNoMoreInteractions(lease);
    }

    @Test
    void tick_arrowInvalid_stops() {
        when(arrow.isValid()).thenReturn(false);

        tracker.start(arrow, player);
        tracker.tick();

        verify(task).cancel();
    }

    @Test
    void tick_playerOffline_stops() {
        when(player.isOnline()).thenReturn(false);

        tracker.start(arrow, player);
        tracker.tick();

        verify(task).cancel();
    }

    @Test
    void tick_belowMinHeight_stops() {
        when(location.getY()).thenReturn(-100.0);

        tracker.start(arrow, player);
        tracker.tick();

        verify(task).cancel();
    }

    @Test
    void tick_exceedingMaxFlightTicks_stops() {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(world.isChunkLoaded(1, 1)).thenReturn(true);

        tracker.start(arrow, player);
        for (int i = 0; i <= MAX_FLIGHT_TICKS; i++) {
            tracker.tick();
        }

        verify(task).cancel();
    }

    @Test
    void tick_chunkUnloaded_requestsAsync_andAcquiresOnCallback() {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(world.isChunkLoaded(1, 1)).thenReturn(false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer> callback = ArgumentCaptor.forClass(Consumer.class);
        tracker.start(arrow, player);
        tracker.tick();

        verify(world).getChunkAtAsync(eq(1), eq(1), eq(true), eq(true), callback.capture());
        verify(lease, never()).acquire(any(), anyInt(), anyInt());

        callback.getValue().accept(mock(Chunk.class));

        verify(lease).acquire(world, 1, 1);
    }

    @Test
    void tick_chunkUnloaded_skipsPendingDuplicate() {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(world.isChunkLoaded(1, 1)).thenReturn(false);

        tracker.start(arrow, player);
        tracker.tick();
        tracker.tick();

        // pending 去重：只发一次异步加载请求。
        verify(world, times(1)).getChunkAtAsync(eq(1), eq(1), eq(true), eq(true), any());
    }

    @Test
    void asyncCallback_afterStop_doesNotAcquire() {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(world.isChunkLoaded(1, 1)).thenReturn(false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer> callback = ArgumentCaptor.forClass(Consumer.class);
        tracker.start(arrow, player);
        tracker.tick();
        verify(world).getChunkAtAsync(eq(1), eq(1), eq(true), eq(true), callback.capture());

        tracker.stop();
        callback.getValue().accept(mock(Chunk.class));

        // 迟到的回调（tracker 已 stop）不得 acquire，避免泄漏。
        verify(lease, never()).acquire(any(), anyInt(), anyInt());
    }

    @Test
    void tick_exception_stops() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenThrow(new RuntimeException("boom"));

        tracker.start(arrow, player);
        tracker.tick();

        verify(task).cancel();
    }

    @Test
    void stop_releasesAcquiredChunks() {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(world.isChunkLoaded(1, 1)).thenReturn(true);

        tracker.start(arrow, player);
        tracker.tick();
        tracker.stop();

        verify(lease).release(world, 1, 1);
        verify(task).cancel();
    }

    @Test
    void stop_isIdempotent() {
        tracker.start(arrow, player);

        tracker.stop();
        tracker.stop();

        verify(task, times(1)).cancel();
    }
}
