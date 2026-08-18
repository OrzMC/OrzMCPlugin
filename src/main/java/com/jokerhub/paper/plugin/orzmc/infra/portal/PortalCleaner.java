package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import com.jokerhub.paper.plugin.orzmc.infra.server.RegionSchedulerProvider;
import java.util.logging.Logger;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 传送门方块和标签清理器。
 *
 * <p>负责在传送门移除时清理黑曜石框架、下界传送门方块和附近的装甲架标签。</p>
 *
 * <p>Folia：方块写入与实体遍历都是区块级操作，必须投递到所属 chunk 的 region 线程。
 * 传送门足迹跨至多 4 个 chunk，按 3×3 chunk 网格逐个投递、任务内只访问该 chunk 区域；
 * 实体清理改用 {@code chunk.getEntities()} 限定本 chunk（Folia 下 {@code getNearbyEntities}
 * 只能看到当前 region 的实体，会漏掉相邻 chunk 的标签）。Paper 上 region 调度即主线程执行。</p>
 */
public final class PortalCleaner {

    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_HEIGHT = 5;
    private static final double LABEL_SEARCH_RANGE = 3.0;

    private final WorldProvider worldProvider;
    private final Logger logger;
    private final RegionSchedulerProvider regionScheduler;

    public PortalCleaner(WorldProvider worldProvider, Logger logger, RegionSchedulerProvider regionScheduler) {
        this.worldProvider = worldProvider;
        this.logger = logger;
        this.regionScheduler = regionScheduler;
    }

    /**
     * 清除传送门的所有方块和附近标签。
     *
     * @param def 传送门定义
     */
    public void clear(PortalService.PortalDef def) {
        World w = worldProvider.getWorld(def.world());
        if (w == null) {
            logger.warning("clearPortalBlocks: 世界 " + def.world() + " 不存在，跳过");
            return;
        }
        clearBlocksPerChunk(w, def);
        clearNearbyLabelsPerChunk(w, def);
    }

    /** 只向足迹实际跨越的 chunk 逐个投递方块清理（避免生成无关区块）。 */
    private void clearBlocksPerChunk(World w, PortalService.PortalDef def) {
        int cx0, cx1, cz0, cz1;
        if (def.axis() == Axis.X) {
            cx0 = (def.cx() - 2) >> 4;
            cx1 = (def.cx() + 3) >> 4;
            cz0 = (def.cz() - 1) >> 4;
            cz1 = (def.cz() + 1) >> 4;
        } else {
            cx0 = (def.cx() - 1) >> 4;
            cx1 = (def.cx() + 1) >> 4;
            cz0 = (def.cz() - 2) >> 4;
            cz1 = (def.cz() + 3) >> 4;
        }
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                final int fx = cx;
                final int fz = cz;
                regionScheduler.run(w, cx, cz, () -> clearBlocksInChunk(w, def, fx, fz));
            }
        }
    }

    /** 只清理落在 (ccx, ccz) 区块内的传送门足迹方块；先加载区块保证 getBlockAt 生效。 */
    private void clearBlocksInChunk(World w, PortalService.PortalDef def, int ccx, int ccz) {
        w.getChunkAt(ccx, ccz);
        int minX = ccx << 4;
        int maxX = minX + 15;
        int minZ = ccz << 4;
        int maxZ = minZ + 15;
        int baseY = def.cy() - 2;
        // 足迹：轴 X → x∈[cx-2,cx+3] z∈[cz-1,cz+1]；轴 Z → z∈[cz-2,cz+3] x∈[cx-1,cx+1]；y∈[cy-4,cy+3]
        int runStart = def.axis() == Axis.X ? def.cz() - 1 : def.cx() - 1;
        int runEnd = def.axis() == Axis.X ? def.cz() + 1 : def.cx() + 1;
        for (int i = -1; i <= FRAME_WIDTH; i++) {
            for (int j = -2; j <= FRAME_HEIGHT; j++) {
                int y = baseY + j;
                int along = def.axis() == Axis.X ? def.cx() - 1 + i : def.cz() - 1 + i;
                boolean inAlongChunk;
                if (def.axis() == Axis.X) {
                    inAlongChunk = along >= minX && along <= maxX;
                } else {
                    inAlongChunk = along >= minZ && along <= maxZ;
                }
                if (!inAlongChunk) {
                    continue;
                }
                for (int r = runStart; r <= runEnd; r++) {
                    int x;
                    int z;
                    if (def.axis() == Axis.X) {
                        x = along;
                        z = r;
                    } else {
                        x = r;
                        z = along;
                    }
                    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                        removeIfPortalBlock(w.getBlockAt(x, y, z));
                    }
                }
            }
        }
    }

    /** 以中心 chunk 为心的 3×3 网格逐个投递标签清理（实体经 chunk.getEntities() 限定本 chunk）。 */
    private void clearNearbyLabelsPerChunk(World w, PortalService.PortalDef def) {
        Location c = new Location(w, def.cx() + 0.5, def.cy() + 2.0, def.cz() + 0.5);
        ArmorStandCleanup.removeMatchingInChunks(w, c, LABEL_SEARCH_RANGE, def.target(), regionScheduler);
    }

    private void removeIfPortalBlock(Block b) {
        Material t = b.getType();
        if (t == Material.OBSIDIAN
                || t == Material.NETHER_PORTAL
                || t == Material.GLOWSTONE
                || t == Material.END_ROD
                || t == Material.LIGHT_BLUE_STAINED_GLASS
                || t == Material.STONE_BRICKS) {
            b.setType(Material.AIR, false);
        }
    }
}
