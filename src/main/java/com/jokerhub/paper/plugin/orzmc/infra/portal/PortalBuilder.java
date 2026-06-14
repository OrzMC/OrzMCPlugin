package com.jokerhub.paper.plugin.orzmc.infra.portal;

import java.util.Map;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 传送门方块构建器。
 *
 * <p>负责检测合适位置、放置黑曜石框架和下界传送门方块、计算方向。</p>
 */
public final class PortalBuilder {

    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_HEIGHT = 5;
    private static final int MAX_ATTEMPTS = 16;

    private final Map<String, String> interiorTargets;

    public PortalBuilder(Map<String, String> interiorTargets) {
        this.interiorTargets = interiorTargets;
    }

    /**
     * 在玩家前方建造传送门，返回 PortalBuildResult 包含中心位置和轴向。
     */
    public PortalBuildResult build(Player player, String target) {
        Location loc = player.getLocation();
        Vector dir = loc.getDirection().normalize();
        boolean axisX = Math.abs(dir.getX()) >= Math.abs(dir.getZ());
        int dx = axisX ? (dir.getX() >= 0 ? 1 : -1) : 0;
        int dz = axisX ? 0 : (dir.getZ() >= 0 ? 1 : -1);
        int baseX = loc.getBlockX() + dx * 2;
        int baseY = Math.max(2, loc.getBlockY());
        int baseZ = loc.getBlockZ() + dz * 2;
        World world = loc.getWorld();
        int maxY = world.getMaxHeight() - FRAME_HEIGHT;
        if (baseY > maxY) baseY = Math.max(2, maxY);

        baseY = findClearSpace(world, baseX, baseY, baseZ, axisX, maxY);
        placeFrame(world, baseX, baseY, baseZ, axisX, target);
        placePad(world, baseX, baseY, baseZ, axisX);

        int cx = baseX + (axisX ? 0 : 1);
        int cy = baseY + 2;
        int cz = baseZ + (axisX ? 1 : 0);
        Axis portalAxis = axisX ? Axis.Z : Axis.X;
        Location center = new Location(world, cx, cy, cz);
        return new PortalBuildResult(center, axisX ? Axis.X : Axis.Z, portalAxis, world.getName(), cx, cy, cz, target);
    }

    private int findClearSpace(World world, int baseX, int baseY, int baseZ, boolean axisX, int maxY) {
        int y = baseY;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            boolean clear = true;
            for (int i = 1; i < FRAME_WIDTH - 1 && clear; i++) {
                for (int j = 1; j < FRAME_HEIGHT - 1 && clear; j++) {
                    int x = baseX + (axisX ? 0 : i);
                    int z = baseZ + (axisX ? i : 0);
                    Block b = world.getBlockAt(x, y + j, z);
                    if (!b.getType().isAir()) {
                        clear = false;
                    }
                }
            }
            if (clear) break;
            if (y < maxY) {
                y++;
            } else {
                break;
            }
        }
        return y;
    }

    private void placeFrame(World world, int baseX, int baseY, int baseZ, boolean axisX, String target) {
        String keyPrefix = world.getName() + ":";
        for (int i = 0; i < FRAME_WIDTH; i++) {
            for (int j = 0; j < FRAME_HEIGHT; j++) {
                boolean frame = (i == 0 || i == FRAME_WIDTH - 1 || j == 0 || j == FRAME_HEIGHT - 1);
                int x = baseX + (axisX ? 0 : i);
                int z = baseZ + (axisX ? i : 0);
                int y = baseY + j;
                Block b = world.getBlockAt(x, y, z);
                if (frame) {
                    b.setType(Material.OBSIDIAN, false);
                } else {
                    b.setType(Material.NETHER_PORTAL, false);
                    if (b.getBlockData() instanceof Orientable o) {
                        o.setAxis(axisX ? Axis.Z : Axis.X);
                        b.setBlockData(o, false);
                    }
                    interiorTargets.put(keyPrefix + x + ":" + y + ":" + z, target);
                }
            }
        }
    }

    private void placePad(World world, int baseX, int baseY, int baseZ, boolean axisX) {
        int padY = baseY - 1;
        for (int i = -1; i <= FRAME_WIDTH; i++) {
            int x = baseX + (axisX ? 0 : i);
            int z = baseZ + (axisX ? i : 0);
            world.getBlockAt(x, padY, z).setType(Material.GOLD_BLOCK, false);
        }
    }

    /** 传送门构建结果，包含中心坐标等信息。 */
    public record PortalBuildResult(
            Location center, Axis infoAxis, Axis portalAxis, String worldName, int cx, int cy, int cz, String target) {}
}
