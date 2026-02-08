package com.jokerhub.paper.plugin.orzmc.features.teleport;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.core.OrzConstants;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class TeleportBowService {
    public static final String name = "传送弓";
    private final OrzTextStyles styles;
    private final TeleportBowTexts texts;

    public TeleportBowService(OrzTextStyles styles) {
        this.styles = styles;
        this.texts = new TeleportBowTexts(styles);
    }

    public TextComponent prefix() {
        return Component.text("传送弓");
    }

    public void giveAndEquip(Player player) {
        ItemStack teleport_bow = new ItemStack(Material.BOW);
        ItemMeta meta = teleport_bow.getItemMeta();
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
        meta.displayName(Component.text(name));
        java.util.ArrayList<Component> loreList = new java.util.ArrayList<>();
        loreList.add(Component.text("可以把你传送到箭落地的位置"));
        meta.lore(loreList);
        NamespacedKey key = new NamespacedKey(OrzMC.plugin(), OrzConstants.TPBOW_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        teleport_bow.setItemMeta(meta);
        ItemStack prev = player.getInventory().getItemInMainHand();
        if (prev.getType() != Material.AIR) {
            player.getInventory().addItem(prev);
        }
        player.getInventory().setItemInMainHand(teleport_bow);
        ItemStack arrow = new ItemStack(Material.ARROW);
        player.getInventory().addItem(arrow);
        player.sendMessage(styles.success("你获得了" + name));
    }

    private static final org.bukkit.NamespacedKey KEY_TPBOW =
            new org.bukkit.NamespacedKey(OrzMC.plugin(), OrzConstants.TPBOW_KEY);

    public boolean isTPBowArrow(org.bukkit.entity.Projectile proj) {
        if (proj instanceof org.bukkit.entity.Arrow arrow) {
            return arrow.getPersistentDataContainer().has(KEY_TPBOW, org.bukkit.persistence.PersistentDataType.BYTE);
        }
        return false;
    }

    public void markArrow(org.bukkit.event.entity.EntityShootBowEvent event) {
        org.bukkit.inventory.meta.ItemMeta meta =
                event.getBow() != null ? event.getBow().getItemMeta() : null;
        if (meta != null
                && meta.getPersistentDataContainer().has(KEY_TPBOW, org.bukkit.persistence.PersistentDataType.BYTE)) {
            if (event.getProjectile() instanceof org.bukkit.entity.Arrow arrow) {
                arrow.getPersistentDataContainer()
                        .set(KEY_TPBOW, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    private static final java.util.EnumSet<org.bukkit.Material> DANGEROUS = java.util.EnumSet.of(
            org.bukkit.Material.LAVA,
            org.bukkit.Material.WATER,
            org.bukkit.Material.MAGMA_BLOCK,
            org.bukkit.Material.CACTUS,
            org.bukkit.Material.FIRE,
            org.bukkit.Material.SOUL_FIRE,
            org.bukkit.Material.CAMPFIRE,
            org.bukkit.Material.SOUL_CAMPFIRE,
            org.bukkit.Material.POWDER_SNOW);

    private boolean withinWorldBounds(org.bukkit.Location loc) {
        if (loc == null) return false;
        org.bukkit.World w = loc.getWorld();
        if (w == null) return false;
        int y = loc.getBlockY();
        int min = w.getMinHeight();
        int max = w.getMaxHeight();
        return y >= min + 1 && y <= max - 2;
    }

    private org.bukkit.Location toBlockCenter(org.bukkit.Location loc, org.bukkit.util.Vector dir) {
        org.bukkit.World w = loc.getWorld();
        if (w == null) return null;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        float yaw = vectorYaw(dir);
        return new org.bukkit.Location(w, bx + 0.5, by, bz + 0.5, yaw, 0f);
    }

    private boolean isStandable(org.bukkit.Location loc) {
        org.bukkit.block.Block foot = loc.getBlock();
        org.bukkit.block.Block head = foot.getRelative(0, 1, 0);
        org.bukkit.block.Block ground = foot.getRelative(0, -1, 0);
        org.bukkit.Material ft = foot.getType();
        org.bukkit.Material ht = head.getType();
        org.bukkit.Material gt = ground.getType();
        if (!ft.isAir() || !ht.isAir()) return false;
        if (DANGEROUS.contains(ft) || DANGEROUS.contains(ht) || DANGEROUS.contains(gt)) return false;
        return gt.isSolid();
    }

    private org.bukkit.Location findStandableAtOrAbove(
            org.bukkit.World world, int bx, int by, int bz, org.bukkit.util.Vector facing) {
        for (int dy = 0; dy <= 1; dy++) {
            org.bukkit.Location loc =
                    new org.bukkit.Location(world, bx + 0.5, by + dy, bz + 0.5, vectorYaw(facing), 0f);
            if (withinWorldBounds(loc) && isStandable(loc)) {
                return loc;
            }
        }
        return null;
    }

    private org.bukkit.Location findNearestSafe(org.bukkit.Location center, org.bukkit.util.Vector facing) {
        org.bukkit.World w = center.getWorld();
        if (w == null) return null;
        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();
        final org.bukkit.util.Vector facingNorm = facing.clone().normalize();
        org.bukkit.Location standable = findStandableAtOrAbove(w, bx, by, bz, facingNorm);
        if (standable != null) return standable;
        java.util.List<org.bukkit.Location> candidates = new java.util.ArrayList<>();
        final int radius = 1;
        for (int r = 1; r == radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    candidates.add(
                            new org.bukkit.Location(w, bx + dx + 0.5, by, bz + dz + 0.5, vectorYaw(facingNorm), 0f));
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
        for (org.bukkit.Location cand : candidates) {
            org.bukkit.Location found =
                    findStandableAtOrAbove(w, cand.getBlockX(), cand.getBlockY(), cand.getBlockZ(), facingNorm);
            if (found != null) return found;
        }
        return null;
    }

    private float vectorYaw(org.bukkit.util.Vector v) {
        double yawRad = Math.atan2(-v.getX(), v.getZ());
        return (float) Math.toDegrees(yawRad);
    }

    public void handleArrowHit(org.bukkit.entity.Arrow arrow, org.bukkit.entity.Player player) {
        if (arrow.isInWater()) {
            player.sendMessage(texts.logText("箭射进了水里!").color(styles.colorError()));
            return;
        }
        if (arrow.isInLava()) {
            player.sendMessage(texts.logText("箭射进了岩浆里!").color(styles.colorError()));
            return;
        }
        org.bukkit.Location base = arrow.getLocation();
        org.bukkit.World pw = player.getWorld();
        org.bukkit.World tw = base.getWorld();
        if (!pw.equals(tw)) {
            player.sendMessage(texts.logText("无法跨世界传送!").color(styles.colorError()));
            return;
        }
        org.bukkit.util.Vector dir = arrow.getVelocity();
        org.bukkit.Location center = toBlockCenter(base, dir);
        if (!withinWorldBounds(center)) {
            player.sendMessage(texts.logText("目标高度不合法!").color(styles.colorError()));
            return;
        }
        org.bukkit.Location safe = findNearestSafe(center, dir);
        if (safe == null) {
            player.sendMessage(texts.logText("目标位置不可站立!").color(styles.colorError()));
            return;
        }
        player.teleport(safe);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_CAT_PURR, 1.0F, 1.0F);
        player.sendMessage(texts.logText("传送完成!").color(styles.colorSuccess()));
    }
}
