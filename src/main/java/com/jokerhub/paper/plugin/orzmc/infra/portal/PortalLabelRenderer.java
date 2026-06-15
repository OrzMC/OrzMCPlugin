package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
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
 */
public final class PortalLabelRenderer {

    private static final TextColor TITLE_COLOR = TextColor.color(0xFFD700);
    private static final TextColor ADDR_COLOR = TextColor.color(0x00FFFF);

    private final WorldProvider worldProvider;
    private final Logger logger;

    public PortalLabelRenderer(WorldProvider worldProvider, Logger logger) {
        this.worldProvider = worldProvider;
        this.logger = logger;
    }

    /**
     * 在传送门中心位置生成装甲架标签（"跨服传送" + 目标地址）。
     * 如果标签已存在则跳过。
     */
    public void spawnLabel(String worldName, int cx, int cy, int cz, String target) {
        World w = worldProvider.getWorld(worldName);
        if (w == null) return;
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
        removeMatchingArmorStands(w, base, 2.5, target);
    }

    /** 在传送门侧面放置壁挂标牌。 */
    public void placeInfoSign(World world, Location center, Axis axis, int dx, int dz, String host, int port) {
        int sx = center.getBlockX() + dx;
        int sz = center.getBlockZ() + dz;
        int sy = center.getBlockY();
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
            front.setLine(0, "传送门");
            front.setLine(1, host + ":" + port);
            sign.update(true, false);
        }
    }

    /** 在清除传送门方块后清理附近的装甲架（含在 clearPortalBlocks 中使用）。 */
    public void clearNearbyArmorStands(World w, Location center, double range, String target) {
        removeMatchingArmorStands(w, center, range, target);
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

    private void removeMatchingArmorStands(World w, Location base, double range, String target) {
        Collection<Entity> nearby = w.getNearbyEntities(base, range, range, range);
        for (Entity e : nearby) {
            if (e instanceof ArmorStand as) {
                Component name = as.customName();
                String plain = name == null
                        ? ""
                        : PlainTextComponentSerializer.plainText().serialize(name);
                if (!plain.isEmpty() && (plain.contains(target) || plain.contains("跨服传送"))) {
                    e.remove();
                }
            }
        }
    }
}
