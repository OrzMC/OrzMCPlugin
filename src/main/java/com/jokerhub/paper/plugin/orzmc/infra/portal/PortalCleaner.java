package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import java.util.Collection;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

/**
 * 传送门方块和标签清理器。
 *
 * <p>负责在传送门移除时清理黑曜石框架、下界传送门方块和附近的装甲架标签。</p>
 */
public final class PortalCleaner {

    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_HEIGHT = 5;
    private static final double LABEL_SEARCH_RANGE = 2.5;

    private final WorldProvider worldProvider;
    private final Logger logger;

    public PortalCleaner(WorldProvider worldProvider, Logger logger) {
        this.worldProvider = worldProvider;
        this.logger = logger;
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
        preloadChunks(w, def);
        clearBlocks(w, def);
        clearNearbyLabels(w, def);
    }

    private void preloadChunks(World w, PortalService.PortalDef def) {
        if (def.axis() == Axis.X) {
            w.getChunkAt(def.cx() >> 4, (def.cz() - 1) >> 4);
            w.getChunkAt(def.cx() >> 4, def.cz() >> 4);
            w.getChunkAt(def.cx() >> 4, (def.cz() + 1) >> 4);
        } else {
            w.getChunkAt((def.cx() - 1) >> 4, def.cz() >> 4);
            w.getChunkAt(def.cx() >> 4, def.cz() >> 4);
            w.getChunkAt((def.cx() + 1) >> 4, def.cz() >> 4);
        }
    }

    private void clearBlocks(World w, PortalService.PortalDef def) {
        int baseY = def.cy() - 2;
        if (def.axis() == Axis.X) {
            int z = def.cz();
            int xBase = def.cx() - 1;
            for (int i = -1; i <= FRAME_WIDTH; i++) {
                for (int j = -2; j <= FRAME_HEIGHT; j++) {
                    removeIfPortalBlock(w.getBlockAt(xBase + i, baseY + j, z));
                }
            }
            for (int i = -1; i <= FRAME_WIDTH; i++) {
                for (int j = -2; j <= FRAME_HEIGHT; j++) {
                    removeIfPortalBlock(w.getBlockAt(xBase + i, baseY + j, z + 1));
                    removeIfPortalBlock(w.getBlockAt(xBase + i, baseY + j, z - 1));
                }
            }
        } else {
            int x = def.cx();
            int zBase = def.cz() - 1;
            for (int i = -1; i <= FRAME_WIDTH; i++) {
                for (int j = -2; j <= FRAME_HEIGHT; j++) {
                    removeIfPortalBlock(w.getBlockAt(x, baseY + j, zBase + i));
                }
            }
            for (int i = -1; i <= FRAME_WIDTH; i++) {
                for (int j = -2; j <= FRAME_HEIGHT; j++) {
                    removeIfPortalBlock(w.getBlockAt(x + 1, baseY + j, zBase + i));
                    removeIfPortalBlock(w.getBlockAt(x - 1, baseY + j, zBase + i));
                }
            }
        }
    }

    private void clearNearbyLabels(World w, PortalService.PortalDef def) {
        Location c = new Location(w, def.cx() + 0.5, def.cy() + 2.0, def.cz() + 0.5);
        Collection<Entity> nearby = w.getNearbyEntities(c, 3.0, 3.0, 3.0);
        for (Entity e : nearby) {
            if (e instanceof ArmorStand as) {
                String plain = as.customName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(as.customName());
                if (!plain.isEmpty() && (plain.contains(def.target()) || plain.contains("跨服传送"))) {
                    e.remove();
                }
            }
        }
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
