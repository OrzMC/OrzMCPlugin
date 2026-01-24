package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzTPBow;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class OrzBowShootEvent implements Listener {
    private static final NamespacedKey KEY_TPBOW = new NamespacedKey(OrzMC.plugin(), "tpbow");

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
                Location center = toBlockCenter(base);
                if (!withinWorldBounds(center)) {
                    player.sendMessage(OrzTPBow.logText("目标高度不合法!"));
                    return;
                }
                Location safe = findNearestSafe(center, 2);
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

    private boolean withinWorldBounds(@NotNull Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;
        int y = loc.getBlockY();
        int min = w.getMinHeight();
        int max = w.getMaxHeight();
        return y >= min + 1 && y <= max - 2;
    }

    private Location toBlockCenter(@NotNull Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return new Location(w, bx + 0.5, by, bz + 0.5, loc.getYaw(), loc.getPitch());
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

    private Location findNearestSafe(@NotNull Location center, int radius) {
        if (isStandable(center)) return center;
        World w = center.getWorld();
        if (w == null) return null;
        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location cand = new Location(w, bx + dx + 0.5, by, bz + dz + 0.5, center.getYaw(), center.getPitch());
                    if (withinWorldBounds(cand) && isStandable(cand)) {
                        return cand;
                    }
                }
            }
        }
        return null;
    }
}
