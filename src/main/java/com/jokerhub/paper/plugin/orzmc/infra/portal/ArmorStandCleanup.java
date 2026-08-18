package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.infra.server.RegionSchedulerProvider;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

/**
 * 按区块扫描清理传送门附近的装甲架标签。
 *
 * <p>Folia 下 {@code getNearbyEntities} 只能返回当前 region 内的实体，跨区块的标签会漏掉；
 * 因此改为以中心所在 chunk 为中心的 3×3 chunk 逐个投递到各 chunk 的 region 线程，
 * 用 {@code chunk.getEntities()} 限定本 chunk，再按与原 {@code getNearbyEntities} 一致的
 * 立方体范围过滤。Paper 上 region 调度即主线程执行，行为不变。</p>
 */
final class ArmorStandCleanup {

    private ArmorStandCleanup() {}

    /** 移除中心 {@code (cx+range)} 立方体内的、名称匹配 {@code target} 或含「跨服传送」的装甲架。 */
    static void removeMatchingInChunks(
            World world, Location center, double range, String target, RegionSchedulerProvider regionScheduler) {
        int ccx = center.getBlockX() >> 4;
        int ccz = center.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = ccx + dx;
                int cz = ccz + dz;
                regionScheduler.run(world, cx, cz, () -> {
                    if (!world.isChunkLoaded(cx, cz)) {
                        return; // 不主动加载区块仅为了找标签
                    }
                    Entity[] entities = world.getChunkAt(cx, cz).getEntities();
                    if (entities == null) {
                        return; // 防御：真实 Bukkit 恒非 null，仅 mock 环境可能为 null
                    }
                    for (Entity e : entities) {
                        if (e instanceof ArmorStand as && withinRange(e.getLocation(), center, range)) {
                            String plain = as.customName() == null
                                    ? ""
                                    : PlainTextComponentSerializer.plainText().serialize(as.customName());
                            if (!plain.isEmpty() && (plain.contains(target) || plain.contains("跨服传送"))) {
                                e.remove();
                            }
                        }
                    }
                });
            }
        }
    }

    private static boolean withinRange(Location loc, Location center, double range) {
        if (loc == null) {
            return false;
        }
        return Math.abs(loc.getX() - center.getX()) <= range
                && Math.abs(loc.getY() - center.getY()) <= range
                && Math.abs(loc.getZ() - center.getZ()) <= range;
    }
}
