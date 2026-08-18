package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TeleportBowFlightTrackerTest extends ServiceTestBase {

    private static final int MAX_FLIGHT_TICKS = 400;

    private ServerFacade server;
    private ForceLoadedChunkLease lease;
    private EntityScheduler entityScheduler;
    private ScheduledTask task;
    private Arrow arrow;
    private Player player;
    private World world;
    private Location location;

    private TeleportBowFlightTracker tracker;

    @BeforeEach
    void setUp() {
        server = mock(ServerFacade.class);
        lease = mock(ForceLoadedChunkLease.class);
        entityScheduler = mock(EntityScheduler.class);
        task = mock(ScheduledTask.class);
        when(entityScheduler.runAtFixedRate(any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(task);

        arrow = mock(Arrow.class);
        player = mock(Player.class);
        world = mock(World.class);
        location = mock(Location.class);

        when(arrow.getWorld()).thenReturn(world);
        when(arrow.getScheduler()).thenReturn(entityScheduler);
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

        verify(entityScheduler).runAtFixedRate(any(), any(), notNull(), eq(1L), eq(1L));
    }

    @Test
    void start_retiredCallback_stops() {
        tracker.start(arrow, player);

        // retired 回调 = stop()：实体被移除/任务被系统取消时自动释放 force-load 区块，避免泄漏。
        ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
        verify(entityScheduler).runAtFixedRate(any(), any(), retired.capture(), eq(1L), eq(1L));
        retired.getValue().run();

        verify(task).cancel();
        verifyNoMoreInteractions(lease);
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

    // ---- Folia 并发：tick（global region）与 getChunkAtAsync 回调（目标 chunk region）并发读写集合 ----

    @Test
    void concurrentTickAndAsyncCallback_noCorruptionOrLeak() throws Exception {
        when(location.getBlockX()).thenReturn(16);
        when(location.getBlockZ()).thenReturn(16);
        when(world.isChunkLoaded(1, 1)).thenReturn(false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer> callback = ArgumentCaptor.forClass(Consumer.class);
        tracker.start(arrow, player);
        tracker.tick();
        verify(world).getChunkAtAsync(eq(1), eq(1), eq(true), eq(true), callback.capture());

        // Folia：tick 跑在 global region 线程，异步加载回调跑在目标 chunk 的 region 线程，
        // acquired/pending 被并发读写。3 × 100 tick < MAX_FLIGHT_TICKS，shouldStop 保持 false。
        int tickThreads = 3;
        CyclicBarrier barrier = new CyclicBarrier(tickThreads + 1);
        ExecutorService pool = Executors.newFixedThreadPool(tickThreads + 1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < tickThreads; i++) {
            futures.add(pool.submit(() -> {
                awaitBarrier(barrier);
                for (int j = 0; j < 100; j++) {
                    tracker.tick();
                }
            }));
        }
        futures.add(pool.submit(() -> {
            awaitBarrier(barrier);
            callback.getValue().accept(mock(Chunk.class));
        }));
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "并发 tick/回调应在超时内完成");
        for (Future<?> f : futures) {
            f.get(); // 任一线程抛异常（如 ConcurrentModificationException）即测试失败
        }

        tracker.stop();
        // 回调 acquire 的引用被 stop 完整释放，不泄漏；tick 因 pending 去重不重复 acquire
        verify(lease, times(1)).acquire(world, 1, 1);
        verify(lease).release(world, 1, 1);
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        }
    }
}
