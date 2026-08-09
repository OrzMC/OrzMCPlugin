package com.jokerhub.paper.plugin.orzmc.features.teleport;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 传送弓箭飞行路径的强制加载引用计数注册表。
 *
 * <p>多支传送弓箭可能同时途经同一区块；若各 tracker 各自 {@code setForceLoaded(true/false)}，
 * 先停的箭会过早解除加载，导致后到的箭重新冻结。本类按 {@code (world, chunk)} 引用计数：
 * 计数 0→1 时才真正 force-load，1→0 时才解除，避免提前卸载竞态。</p>
 *
 * <p>最后一个引用释放后主动卸载区块（立即回收内存，不必等 unload 计时），
 * 但仅卸载「由本注册表 force-load」且「无玩家在内」的区块：
 * 既不会撤销其他插件持有的 force-load，也不会把玩家脚下区块卸掉。</p>
 */
final class ForceLoadedChunkLease {

    private final Map<World, Map<Long, LeaseRef>> counts = new HashMap<>();

    /**
     * 持有一个区块的 force-load 引用。计数 0→1 时仅在区块原本未被 force-load 时
     * 才真正 force-load（不覆盖其他插件已有的 force-load），并记录是否需要我们在释放时清除。
     *
     * <p>仅应在主线程调用（tracker tick / 异步加载回调均在主线程）。</p>
     */
    void acquire(World world, int cx, int cz) {
        Map<Long, LeaseRef> perWorld = counts.computeIfAbsent(world, w -> new HashMap<>());
        long key = key(cx, cz);
        LeaseRef ref = perWorld.get(key);
        if (ref == null) {
            Chunk chunk = world.getChunkAt(cx, cz);
            boolean alreadyForced = chunk.isForceLoaded();
            if (!alreadyForced) {
                chunk.setForceLoaded(true);
            }
            perWorld.put(key, new LeaseRef(1, !alreadyForced));
        } else {
            ref.refs++;
        }
    }

    /**
     * 释放一个 force-load 引用。计数 1→0 时：仅在区块仍加载时解除我们自己设置的 force-load，
     * 随后在无玩家在内时主动 {@code unloadChunk} 回收内存（已卸载则跳过，避免 {@code getChunkAt} 重新加载）。
     */
    void release(World world, int cx, int cz) {
        Map<Long, LeaseRef> perWorld = counts.get(world);
        if (perWorld == null) {
            return;
        }
        long key = key(cx, cz);
        LeaseRef ref = perWorld.get(key);
        if (ref == null || ref.refs <= 0) {
            return;
        }
        if (ref.refs == 1) {
            if (world.isChunkLoaded(cx, cz)) {
                if (ref.weForced) {
                    world.getChunkAt(cx, cz).setForceLoaded(false);
                    if (noPlayerInChunk(world, cx, cz)) {
                        world.unloadChunk(cx, cz, true);
                    }
                }
            }
            perWorld.remove(key);
            if (perWorld.isEmpty()) {
                counts.remove(world);
            }
        } else {
            ref.refs--;
        }
    }

    /** 区块内是否没有在线玩家：有玩家则不主动卸载，避免把玩家脚下区块卸掉造成回弹/加载屏。 */
    private static boolean noPlayerInChunk(World world, int cx, int cz) {
        int minX = cx << 4;
        int minZ = cz << 4;
        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            int bx = loc.getBlockX();
            int bz = loc.getBlockZ();
            if (bx >= minX && bx <= minX + 15 && bz >= minZ && bz <= minZ + 15) {
                return false;
            }
        }
        return true;
    }

    private static long key(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }

    private static final class LeaseRef {
        int refs;
        boolean weForced;

        LeaseRef(int refs, boolean weForced) {
            this.refs = refs;
            this.weForced = weForced;
        }
    }
}
