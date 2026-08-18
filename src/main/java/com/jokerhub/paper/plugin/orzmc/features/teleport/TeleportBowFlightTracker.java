package com.jokerhub.paper.plugin.orzmc.features.teleport;

import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 传送弓箭飞行跟踪器：沿箭的飞行路径 force-load 区块，保证箭进入未加载区块也不会冻结，
 * 从而让 {@code ProjectileHitEvent} 正常触发、传送到真实落点。
 *
 * <p>每支传送弓箭一个实例（在 {@link TeleportBowService#startFlightTracking} 中创建）。
 * 用 {@code EntityScheduler.runAtFixedRate}（实体所属 region 线程）每 tick 检查停止条件，
 * 并把当前区块到按速度预测前方之间
 * 整段路径的连续区块全部 force-load（提前 24 格，异步加载/生成有足够余量，路径中间不留缺口）；
 * 已加载区块直接 force-load（纯内存），未加载区块走 {@code getChunkAtAsync} 异步加载，
 * 加载/生成在异步线程完成，避免主线程同步生成区块造成卡顿。
 * 停时通过 {@link ForceLoadedChunkLease} 释放全部引用（共享注册表，多箭并发安全）。</p>
 */
final class TeleportBowFlightTracker {

    /**
     * 预测前方区块的提前量（格数 = 速度分量 × 该值）。
     * 满弦箭约 3 格/tick：原值 2 仅领先约 2 tick，异步加载常赶不上、箭在区块边界停顿；
     * 8 ≈ 24 格（约 8 tick），给异步加载/生成留足提前量，且配合整段路径加载无中间缺口。
     */
    private static final int PREDICTION_BLOCKS = 8;
    /** 最大跟踪 tick（20s），远超满弦最大飞行时间（~5s）。 */
    private static final int MAX_FLIGHT_TICKS = 400;

    private final ServerFacade server;
    private final ForceLoadedChunkLease lease;

    /**
     * Folia 线程模型：{@code acquired}/{@code pending} 由 tick（global region 线程）与
     * {@code getChunkAtAsync} 回调（目标 chunk 的 region 线程）并发读写，故用并发集合。
     * 它们是租约注册表的乐观本地镜像（非唯一状态来源），停时据此释放已 acquire 的引用。
     */
    private final Set<Long> acquired = ConcurrentHashMap.newKeySet();

    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    private Arrow arrow;
    private Player player;
    private World world;
    private ScheduledTask task;
    private int ticks;
    private volatile boolean active;

    TeleportBowFlightTracker(ServerFacade server, ForceLoadedChunkLease lease) {
        this.server = server;
        this.lease = lease;
    }

    void start(Arrow arrow, Player player) {
        this.arrow = arrow;
        this.player = player;
        this.world = arrow.getWorld();
        this.ticks = 0;
        this.active = true;
        this.task = arrow.getScheduler().runAtFixedRate(server.plugin(), unused -> tick(), this::stop, 1L, 1L);
    }

    /** 每 tick 主线程回调：停止条件满足则收尾释放，否则继续加载前方区块。包私有便于测试直调。 */
    void tick() {
        if (!active) {
            return;
        }
        try {
            if (shouldStop()) {
                stop();
                return;
            }
            forceLoadAround();
        } catch (Throwable t) {
            stop();
        }
    }

    private boolean shouldStop() {
        if (arrow == null || !active) {
            return true;
        }
        if (arrow.isDead() || !arrow.isValid()) {
            return true;
        }
        if (player != null && !player.isOnline()) {
            return true;
        }
        if (arrow.isInBlock()) {
            // 箭已扎入方块：ProjectileHitEvent 已由现有 handler 处理传送，无需再加载。
            return true;
        }
        if (++ticks > MAX_FLIGHT_TICKS) {
            return true;
        }
        Location loc = arrow.getLocation();
        // 坠入虚空：不再为虚空区块持续 force-load。
        return loc != null && world != null && loc.getY() < world.getMinHeight();
    }

    private void forceLoadAround() {
        if (world == null || arrow == null) {
            return;
        }
        Location loc = arrow.getLocation();
        if (loc == null) {
            return;
        }
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        Vector vel = arrow.getVelocity();
        int px = bx + (int) Math.round(vel.getX() * PREDICTION_BLOCKS);
        int pz = bz + (int) Math.round(vel.getZ() * PREDICTION_BLOCKS);
        int cx0 = bx >> 4;
        int cz0 = bz >> 4;
        int cx1 = px >> 4;
        int cz1 = pz >> 4;
        // 当前区块到预测落点之间整条路径的连续区块全部加载，避免跳过中间区块导致边界停顿。
        for (int x = Math.min(cx0, cx1); x <= Math.max(cx0, cx1); x++) {
            for (int z = Math.min(cz0, cz1); z <= Math.max(cz0, cz1); z++) {
                requestChunk(x, z);
            }
        }
    }

    private void requestChunk(int cx, int cz) {
        long key = key(cx, cz);
        if (acquired.contains(key) || pending.contains(key)) {
            return;
        }
        // Folia：force-load 投递由 lease 内部经 region scheduler 转到目标 chunk 的 region 线程；
        // 本线程（tracker tick 所在 region）只维护乐观的 acquired 集合，不触碰区块。
        if (world.isChunkLoaded(cx, cz)) {
            lease.acquire(world, cx, cz);
            acquired.add(key);
            return;
        }
        pending.add(key);
        world.getChunkAtAsync(cx, cz, true, true, chunk -> {
            // 先 acquire + acquired.add 再 pending.remove：任何瞬间 tick 线程都能命中
            // acquired 或 pending 任一集合去重，避免窗口期二次 acquire 泄漏。
            // 迟到的回调（tracker 已 stop）不再 acquire，避免泄漏。
            if (active) {
                lease.acquire(world, cx, cz);
                acquired.add(key);
            }
            pending.remove(key);
        });
    }

    void stop() {
        if (!active && task == null) {
            return;
        }
        active = false;
        cancelSafely();
        if (world != null) {
            for (long key : acquired) {
                lease.release(world, (int) (key >> 32), (int) (key & 0xffffffffL));
            }
        }
        acquired.clear();
        pending.clear();
        arrow = null;
        player = null;
        world = null;
    }

    /**
     * 尽力取消跟踪任务：取消失败不阻塞收尾。真实 Paper/Folia 在插件禁用时会自动回收
     * 本插件全部任务；MockBukkit 的 {@code PaperScheduledTask.cancel()} 尚未实现（4.115.0），
     * 测试环境也靠此防御避免异常中断 stop()。
     */
    private void cancelSafely() {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException e) {
            server.logger().warning("取消传送箭跟踪任务失败: " + e.getMessage());
        } finally {
            task = null;
        }
    }

    private static long key(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }
}
