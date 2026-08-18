package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.server.GlobalSchedulerProvider;
import com.jokerhub.paper.plugin.orzmc.infra.server.RegionSchedulerProvider;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForceLoadedChunkLeaseTest extends ServiceTestBase {

    private World world;

    /** 默认同步直跑（不投递），让既有断言在任务体内即时执行；投递目标用 captureProvider 单独验证。 */
    private RegionSchedulerProvider provider;

    /** 默认同步直跑；global 投递单独用 capturingGlobal 验证。 */
    private GlobalSchedulerProvider global;

    private ForceLoadedChunkLease lease;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        provider = (w, cx, cz, task) -> task.run();
        global = Runnable::run;
        lease = new ForceLoadedChunkLease(provider, global);
    }

    /** 捕获每次 region 投递的 (world, cx, cz, task) 并同步执行任务体的 provider。 */
    private RegionSchedulerProvider capturingProvider(List<Object[]> dispatches) {
        return (w, cx, cz, task) -> {
            dispatches.add(new Object[] {w, cx, cz, task});
            task.run();
        };
    }

    /** 捕获每次 global 投递的 task 并同步执行的 global scheduler。 */
    private GlobalSchedulerProvider capturingGlobal(List<Runnable> dispatches) {
        return task -> {
            dispatches.add(task);
            task.run();
        };
    }

    // ---- Folia 区域亲和：force-load 状态读写是全局状态（global region），unload 才投递 region ----

    @Test
    void acquire_runsOnGlobalRegion_andForcesViaWorldApi() {
        List<Runnable> globalDispatches = new ArrayList<>();
        lease = new ForceLoadedChunkLease(provider, capturingGlobal(globalDispatches));

        lease.acquire(world, 10, 7);

        assertEquals(1, globalDispatches.size());
        // 用 world 级 API 读/写 force-load 状态（global 线程安全），不再经 getChunkAt
        verify(world).isChunkForceLoaded(10, 7);
        verify(world).setChunkForceLoaded(10, 7, true);
    }

    @Test
    void release_dispatchesUnloadToOwningChunkRegion() {
        List<Object[]> dispatches = new ArrayList<>();
        lease = new ForceLoadedChunkLease(capturingProvider(dispatches), global);
        lease.acquire(world, 1, 2);

        clearInvocations(world);
        dispatches.clear();
        lease.release(world, 1, 2);

        assertEquals(1, dispatches.size());
        assertEquals(world, dispatches.get(0)[0]);
        assertEquals(1, dispatches.get(0)[1]);
        assertEquals(2, dispatches.get(0)[2]);
        // 解除 force-load 在 global 线程执行；unload 投递到区块 region
        verify(world).setChunkForceLoaded(1, 2, false);
        verify(world).unloadChunk(1, 2, true);
    }

    @Test
    void acquire_firstReference_loadsAndForces() {
        lease.acquire(world, 1, 2);

        verify(world).isChunkForceLoaded(1, 2);
        verify(world).setChunkForceLoaded(1, 2, true);
    }

    @Test
    void acquire_secondReference_doesNotReload() {
        lease.acquire(world, 1, 2);
        lease.acquire(world, 1, 2);

        verify(world, times(1)).setChunkForceLoaded(1, 2, true);
    }

    @Test
    void release_higherReference_doesNotUnload() {
        lease.acquire(world, 1, 2);
        lease.acquire(world, 1, 2);

        lease.release(world, 1, 2);

        verify(world, never()).setChunkForceLoaded(1, 2, false);
        verify(world, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void release_lastReference_unloadsWhenChunkLoaded() {
        lease.acquire(world, 1, 2);

        lease.release(world, 1, 2);

        verify(world).setChunkForceLoaded(1, 2, false);
        verify(world).unloadChunk(1, 2, true);
    }

    @Test
    void release_lastReference_skipsUnloadWhenPlayerInChunk() {
        lease.acquire(world, 1, 2);

        Player player = mock(Player.class);
        Location loc = mock(Location.class);
        when(world.getPlayers()).thenReturn(List.of(player));
        when(player.getLocation()).thenReturn(loc);
        when(loc.getBlockX()).thenReturn(20); // chunk(1,2)：x 16..31
        when(loc.getBlockZ()).thenReturn(40); // z 32..47

        lease.release(world, 1, 2);

        // 解除 force-load，但区块内有玩家则不主动卸载。
        verify(world).setChunkForceLoaded(1, 2, false);
        verify(world, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void release_unknownReference_isNoOp() {
        lease.release(world, 3, 4);

        verify(world, never()).setChunkForceLoaded(anyInt(), anyInt(), anyBoolean());
        verify(world, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void otherOwnersForceLoad_notClearedOrUnloaded() {
        when(world.isChunkForceLoaded(1, 2)).thenReturn(true); // 其他插件已 force-load

        lease.acquire(world, 1, 2);
        verify(world, never()).setChunkForceLoaded(1, 2, true);

        lease.release(world, 1, 2);

        // 不是我们 force-load 的：不解除、不主动卸载。
        verify(world, never()).setChunkForceLoaded(1, 2, false);
        verify(world, never()).unloadChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void negativeCoordinates_trackedDistinctly() {
        lease.acquire(world, -1, -2);
        verify(world, times(1)).setChunkForceLoaded(-1, -2, true);

        lease.acquire(world, -1, -2);
        verify(world, times(1)).setChunkForceLoaded(-1, -2, true); // 第二次 acquire 不重复加载

        clearInvocations(world);

        lease.release(world, -1, -2);
        verify(world, never()).setChunkForceLoaded(-1, -2, false);

        lease.release(world, -1, -2);
        verify(world, times(1)).setChunkForceLoaded(-1, -2, false);
    }

    @Test
    void sameChunkCoords_distinctWorlds_independent() {
        World worldB = mock(World.class);

        lease.acquire(world, 1, 2);
        lease.acquire(worldB, 1, 2);

        lease.release(world, 1, 2);
        lease.release(worldB, 1, 2);

        verify(world).setChunkForceLoaded(1, 2, false);
        verify(worldB).setChunkForceLoaded(1, 2, false);
    }
}
