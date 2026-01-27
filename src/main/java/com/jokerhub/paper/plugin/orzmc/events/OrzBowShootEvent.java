package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzTPBow;
import com.jokerhub.paper.plugin.orzmc.utils.OrzConstants;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class OrzBowShootEvent extends OrzBaseListener {
    public OrzBowShootEvent(OrzMC plugin) {
        super(plugin);
    }

    private static final NamespacedKey KEY_TPBOW = new NamespacedKey(OrzMC.plugin(), OrzConstants.TPBOW_KEY);

    @EventHandler
    public void onBowShoot(@NotNull ProjectileHitEvent event) {

        if (event.getEntity() instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player player) {
                if (!arrow.getPersistentDataContainer().has(KEY_TPBOW, PersistentDataType.BYTE)) {
                    return;
                }
                if (arrow.isInWater()) {
                    player.sendMessage(OrzTPBow.logText("箭射进了水里!"));
                    return;
                }
                if (arrow.isInLava()) {
                    player.sendMessage(OrzTPBow.logText("箭射进了岩浆里!"));
                    return;
                }
                Location base = arrow.getLocation();
                World pw = player.getWorld();
                World tw = base.getWorld();
                if (!pw.equals(tw)) {
                    player.sendMessage(OrzTPBow.logText("无法跨世界传送!"));
                    return;
                }
                org.bukkit.util.Vector dir = arrow.getVelocity();
                Location center = toBlockCenter(base, dir);
                if (!withinWorldBounds(center)) {
                    player.sendMessage(OrzTPBow.logText("目标高度不合法!"));
                    return;
                }
                Location safe = findNearestSafe(center, dir);
                if (safe == null) {
                    player.sendMessage(OrzTPBow.logText("目标位置不可站立!"));
                    return;
                }
                player.teleport(safe);
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURR, 1.0F, 1.0F);
                player.sendMessage(OrzTPBow.logText("传送完成!"));
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(@NotNull EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemMeta meta = event.getBow() != null ? event.getBow().getItemMeta() : null;
            if (meta != null && meta.getPersistentDataContainer().has(KEY_TPBOW, PersistentDataType.BYTE)) {
                if (event.getProjectile() instanceof Arrow arrow) {
                    arrow.getPersistentDataContainer().set(KEY_TPBOW, PersistentDataType.BYTE, (byte) 1);
                }
            }
        }
    }

    private static final EnumSet<Material> DANGEROUS = EnumSet.of(
            Material.LAVA,
            Material.WATER,
            Material.MAGMA_BLOCK,
            Material.CACTUS,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW
    );

    private boolean withinWorldBounds(Location loc) {
        if (loc == null) return false;
        World w = loc.getWorld();
        if (w == null) return false;
        int y = loc.getBlockY();
        int min = w.getMinHeight();
        int max = w.getMaxHeight();
        return y >= min + 1 && y <= max - 2;
    }

    private Location toBlockCenter(@NotNull Location loc, @NotNull org.bukkit.util.Vector dir) {
        World w = loc.getWorld();
        if (w == null) return null;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        float yaw = vectorYaw(dir);
        return new Location(w, bx + 0.5, by, bz + 0.5, yaw, 0f);
    }

    private boolean isStandable(@NotNull Location loc) {
        Block foot = loc.getBlock();
        Block head = foot.getRelative(0, 1, 0);
        Block ground = foot.getRelative(0, -1, 0);
        Material ft = foot.getType();
        Material ht = head.getType();
        Material gt = ground.getType();
        if (!ft.isAir() || !ht.isAir()) return false;
        if (DANGEROUS.contains(ft) || DANGEROUS.contains(ht) || DANGEROUS.contains(gt)) return false;
        return gt.isSolid();
    }

    private Location findNearestSafe(@NotNull Location center, @NotNull Vector facing) {
        if (isStandable(center)) return center;
        World w = center.getWorld();
        if (w == null) return null;
        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();
        final org.bukkit.util.Vector facingNorm = facing.clone().normalize();
        java.util.List<Location> candidates = new java.util.ArrayList<>();
        final int radius = 1;
        for (int r = 1; r == radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    candidates.add(new Location(w, bx + dx + 0.5, by, bz + dz + 0.5, vectorYaw(facingNorm), 0f));
                }
            }
        }
        candidates.sort((a, b) -> {
            org.bukkit.util.Vector va = a.clone().subtract(center).toVector();
            org.bukkit.util.Vector vb = b.clone().subtract(center).toVector();
            double da = va.normalize().dot(facingNorm);
            double db = vb.normalize().dot(facingNorm);
            return Double.compare(db, da);
        });
        for (Location cand : candidates) {
            if (withinWorldBounds(cand) && isStandable(cand)) {
                return cand;
            }
        }
        return null;
    }

    private float vectorYaw(@NotNull org.bukkit.util.Vector v) {
        double yawRad = Math.atan2(-v.getX(), v.getZ());
        return (float) Math.toDegrees(yawRad);
    }
}
