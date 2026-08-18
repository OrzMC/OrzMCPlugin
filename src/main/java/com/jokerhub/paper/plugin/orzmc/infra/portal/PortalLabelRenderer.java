package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import com.jokerhub.paper.plugin.orzmc.infra.server.RegionSchedulerProvider;
import java.util.Collection;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

/**
 * 传送门标签渲染器。
 *
 * <p>负责在传送门附近生成/清理装甲架标签和壁挂标牌。</p>
 *
 * <p>Folia：生成实体/放置标牌是区块级操作，必须投递到目标 chunk 所属 region 线程
 * （以传送门中心所在 chunk 为 anchor）。跨区块的标签清理经 {@link ArmorStandCleanup}
 * 按 chunk 逐个投递。Paper 上 region 调度即主线程执行，语义不变。</p>
 */
public final class PortalLabelRenderer {

    private static final TextColor TITLE_COLOR = TextColor.color(0xFFD700);
    private static final TextColor ADDR_COLOR = TextColor.color(0x00FFFF);

    private final WorldProvider worldProvider;
    private final Logger logger;
    private final RegionSchedulerProvider regionScheduler;

    public PortalLabelRenderer(WorldProvider worldProvider, Logger logger, RegionSchedulerProvider regionScheduler) {
        this.worldProvider = worldProvider;
        this.logger = logger;
        this.regionScheduler = regionScheduler;
    }

    /**
     * 在传送门中心位置生成装甲架标签（"跨服传送" + 目标地址）。
     * 如果标签已存在则跳过。
     */
    public void spawnLabel(String worldName, int cx, int cy, int cz, String target) {
        World w = worldProvider.getWorld(worldName);
        if (w == null) return;
        regionScheduler.run(w, cx >> 4, cz >> 4, () -> doSpawnLabel(w, cx, cy, cz, target));
    }

    private void doSpawnLabel(World w, int cx, int cy, int cz, String target) {
        Location base = new Location(w, cx + 0.5, cy + 1.9, cz + 0.5);
        if (hasExistingLabel(w, base, target)) return;
        ArmorStand title = (ArmorStand) w.spawnEntity(base.clone().add(0, 0.3, 0), EntityType.ARMOR_STAND);
        title.setInvisible(true);
        title.setMarker(true);
        title.setGravity(false);
        title.setCustomNameVisible(true);
        title.customName(Component.text("跨服传送").color(TITLE_COLOR));
        ArmorStand addr = (ArmorStand) w.spawnEntity(base, EntityType.ARMOR_STAND);
        addr.setInvisible(true);
        addr.setMarker(true);
        addr.setGravity(false);
        addr.setCustomNameVisible(true);
        addr.customName(Component.text(target).color(ADDR_COLOR));
    }

    /** 清除传送门附近的装甲架标签。 */
    public void clearLabels(String worldName, int cx, int cy, int cz, String target) {
        World w = worldProvider.getWorld(worldName);
        if (w == null) {
            logger.warning("clearLabels: 世界 " + worldName + " 不存在，跳过");
            return;
        }
        Location base = new Location(w, cx + 0.5, cy + 1.9, cz + 0.5);
        ArmorStandCleanup.removeMatchingInChunks(w, base, 2.5, target, regionScheduler);
    }

    /** 在传送门侧面放置壁挂标牌。 */
    public void placeInfoSign(World world, Location center, Axis axis, int dx, int dz, String host, int port) {
        int sx = center.getBlockX() + dx;
        int sz = center.getBlockZ() + dz;
        int sy = center.getBlockY();
        regionScheduler.run(world, sx >> 4, sz >> 4, () -> doPlaceInfoSign(world, sx, sy, sz, dx, dz, host, port));
    }

    private void doPlaceInfoSign(World world, int sx, int sy, int sz, int dx, int dz, String host, int port) {
        Block signBlock = world.getBlockAt(sx, sy, sz);
        if (!signBlock.getType().isAir()) return;
        signBlock.setType(Material.OAK_WALL_SIGN, false);
        if (signBlock.getBlockData() instanceof WallSign ws) {
            BlockFace face =
                    dx > 0 ? BlockFace.EAST : dx < 0 ? BlockFace.WEST : dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            ws.setFacing(face);
            signBlock.setBlockData(ws, false);
        }
        if (signBlock.getState() instanceof Sign sign) {
            SignSide front = sign.getSide(Side.FRONT);
            front.line(0, Component.text("传送门"));
            front.line(1, Component.text(host + ":" + port));
            sign.update(true, false);
        }
    }

    /** 在清除传送门方块后清理附近的装甲架（含在 clearPortalBlocks 中使用）。 */
    public void clearNearbyArmorStands(World w, Location center, double range, String target) {
        ArmorStandCleanup.removeMatchingInChunks(w, center, range, target, regionScheduler);
    }

    private boolean hasExistingLabel(World w, Location base, String target) {
        Collection<Entity> nearby = w.getNearbyEntities(base, 2.0, 2.0, 2.0);
        for (Entity e : nearby) {
            if (e instanceof ArmorStand as) {
                Component name = as.customName();
                String plain = name == null
                        ? ""
                        : PlainTextComponentSerializer.plainText().serialize(name);
                if (!plain.isEmpty() && plain.contains(target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
