package com.jokerhub.paper.plugin.orzmc.features.portal;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.PortalsWriter;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.portal.IPortalService;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class PortalService implements IPortalService {
    private static final PortalService INSTANCE = new PortalService();

    public static PortalService defaultImpl() {
        return INSTANCE;
    }

    private final Map<String, String> interiorTargets = new HashMap<>();
    private final Map<String, PortalDef> portalCenters = new HashMap<>();

    private String key(org.bukkit.World w, int x, int y, int z) {
        return w.getName() + ":" + x + ":" + y + ":" + z;
    }

    public record PortalInfo(Location location, Axis axis) {}

    public record PortalDef(String world, int cx, int cy, int cz, Axis axis, String target) {

        public String centerKey() {
            return world + ":" + cx + ":" + cy + ":" + cz;
        }
    }

    public PortalInfo createPortal(Player player, String host, int port) {
        org.bukkit.Location loc = player.getLocation();
        Vector dir = loc.getDirection().normalize();
        boolean axisX = Math.abs(dir.getX()) >= Math.abs(dir.getZ());
        int dx = axisX ? (dir.getX() >= 0 ? 1 : -1) : 0;
        int dz = axisX ? 0 : (dir.getZ() >= 0 ? 1 : -1);
        int baseX = loc.getBlockX() + dx * 2;
        int baseY = Math.max(2, loc.getBlockY());
        int baseZ = loc.getBlockZ() + dz * 2;
        org.bukkit.World world = loc.getWorld();
        int fw = 4;
        int fh = 5;
        int maxY = world.getMaxHeight() - fh;
        if (baseY > maxY) baseY = Math.max(2, maxY);
        for (int attempt = 0; attempt < 16; attempt++) {
            boolean clear = true;
            for (int i = 1; i < fw - 1 && clear; i++) {
                for (int j = 1; j < fh - 1 && clear; j++) {
                    int x = baseX + (axisX ? 0 : i);
                    int z = baseZ + (axisX ? i : 0);
                    int y = baseY + j;
                    org.bukkit.block.Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        clear = false;
                    }
                }
            }
            if (clear) break;
            if (baseY < maxY) {
                baseY++;
            } else {
                break;
            }
        }
        for (int i = 0; i < fw; i++) {
            for (int j = 0; j < fh; j++) {
                boolean frame = (i == 0 || i == fw - 1 || j == 0 || j == fh - 1);
                int x = baseX + (axisX ? 0 : i);
                int z = baseZ + (axisX ? i : 0);
                int y = baseY + j;
                Block b = world.getBlockAt(x, y, z);
                if (frame) {
                    b.setType(Material.OBSIDIAN, false);
                } else {
                    b.setType(Material.NETHER_PORTAL, false);
                    org.bukkit.block.data.BlockData bd = b.getBlockData();
                    if (bd instanceof Orientable o) {
                        org.bukkit.Axis portalAxis = axisX ? org.bukkit.Axis.Z : org.bukkit.Axis.X;
                        o.setAxis(portalAxis);
                        b.setBlockData(o, false);
                    }
                    interiorTargets.put(key(world, x, y, z), host + ":" + port);
                }
            }
        }
        // 仅添加底部装饰
        int padY = baseY - 1;
        if (axisX) {
            int y = padY;
            for (int i = -1; i <= fw; i++) {
                int x = baseX;
                int z = baseZ + i;
                Block pad = world.getBlockAt(x, y, z);
                pad.setType(Material.GOLD_BLOCK, false);
            }
        } else {
            int y = padY;
            for (int i = -1; i <= fw; i++) {
                int x = baseX + i;
                int z = baseZ;
                Block pad = world.getBlockAt(x, y, z);
                pad.setType(Material.GOLD_BLOCK, false);
            }
        }
        int cx = baseX + (axisX ? 0 : 1);
        int cy = baseY + 2;
        int cz = baseZ + (axisX ? 1 : 0);
        org.bukkit.Location center = new org.bukkit.Location(world, cx, cy, cz);
        org.bukkit.Axis portalAxis = axisX ? org.bukkit.Axis.Z : org.bukkit.Axis.X;
        PortalDef def = new PortalDef(world.getName(), cx, cy, cz, portalAxis, host + ":" + port);
        portalCenters.put(def.centerKey(), def);
        rehydrateInterior(def);
        spawnLabel(def);
        saveToStorage();
        return new PortalInfo(center, axisX ? org.bukkit.Axis.X : org.bukkit.Axis.Z);
    }

    public String findTarget(org.bukkit.Location location) {
        String k = key(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String v = interiorTargets.getOrDefault(k, null);
        if (v != null) return v;
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        org.bukkit.World w = location.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String kk = key(w, bx + dx, by + dy, bz + dz);
                    String vv = interiorTargets.get(kk);
                    if (vv != null) return vv;
                }
            }
        }
        return null;
    }

    private void placeInfoSign(
            org.bukkit.World world,
            org.bukkit.Location center,
            org.bukkit.Axis axis,
            int dx,
            int dz,
            String host,
            int port) {
        int sx = center.getBlockX() + (dx);
        int sz = center.getBlockZ() + (dz);
        int sy = center.getBlockY();
        org.bukkit.block.Block signBlock = world.getBlockAt(sx, sy, sz);
        if (signBlock.getType().isAir()) {
            signBlock.setType(Material.OAK_WALL_SIGN, false);
            org.bukkit.block.data.BlockData bd = signBlock.getBlockData();
            if (bd instanceof WallSign ws) {
                BlockFace face =
                        dx > 0 ? BlockFace.EAST : dx < 0 ? BlockFace.WEST : dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
                ws.setFacing(face);
                signBlock.setBlockData(ws, false);
            }
            org.bukkit.block.BlockState st = signBlock.getState();
            if (st instanceof Sign sign) {
                SignSide front = sign.getSide(Side.FRONT);
                front.setLine(0, "传送门");
                front.setLine(1, host + ":" + port);
                sign.update(true, false);
            }
        }
    }

    public void loadFromStorage() {
        org.bukkit.configuration.file.FileConfiguration cfg =
                OrzMC.plugin().configManager.getConfig("portals");
        TypedConfigs.Portals typed = TypedConfigs.Portals.from(cfg);
        for (java.util.Map.Entry<String, TypedConfigs.Portals.PortalEntry> e :
                typed.entries().entrySet()) {
            String k = e.getKey();
            String[] parts = k.split(":");
            if (parts.length != 4) continue;
            String world = parts[0];
            int cx, cy, cz;
            try {
                cx = Integer.parseInt(parts[1]);
                cy = Integer.parseInt(parts[2]);
                cz = Integer.parseInt(parts[3]);
            } catch (Exception ignored) {
                continue;
            }
            String axisStr = e.getValue().axis();
            String target = e.getValue().target();
            if (target == null || target.isEmpty()) continue;
            org.bukkit.Axis axis = "Z".equalsIgnoreCase(axisStr) ? org.bukkit.Axis.Z : org.bukkit.Axis.X;
            PortalDef def = new PortalDef(world, cx, cy, cz, axis, target);
            portalCenters.put(def.centerKey(), def);
            rehydrateInterior(def);
            spawnLabel(def);
        }
    }

    public void saveToStorage() {
        org.bukkit.configuration.file.FileConfiguration cfg =
                OrzMC.plugin().configManager.getConfig("portals");
        if (cfg == null) return;
        java.util.Map<String, TypedConfigs.Portals.PortalEntry> entries = new java.util.HashMap<>();
        for (PortalDef def : portalCenters.values()) {
            entries.put(
                    def.centerKey(),
                    new TypedConfigs.Portals.PortalEntry(def.target, def.axis == org.bukkit.Axis.Z ? "Z" : "X"));
        }
        PortalsWriter.write(cfg, entries);
        OrzMC.plugin().configManager.saveConfig("portals");
    }

    public int removeByTarget(String target) {
        java.util.List<PortalDef> toRemove = new java.util.ArrayList<>();
        for (PortalDef def : portalCenters.values()) {
            if (target.equals(def.target())) {
                toRemove.add(def);
            }
        }
        for (PortalDef def : toRemove) {
            clearPortalBlocks(def);
            portalCenters.remove(def.centerKey());
            clearInterior(def);
            clearLabels(def);
        }
        saveToStorage();
        OrzMC.plugin().configManager.reloadConfig("portals");
        return toRemove.size();
    }

    private void clearInterior(PortalDef def) {
        int z = def.cz;
        int x1 = def.cx;
        int x2 = def.cx + 1;
        int y1 = def.cy - 1;
        int y2 = def.cy;
        int y3 = def.cy + 1;
        interiorTargets.remove(def.world + ":" + x1 + ":" + y1 + ":" + z);
        interiorTargets.remove(def.world + ":" + x1 + ":" + y2 + ":" + z);
        interiorTargets.remove(def.world + ":" + x1 + ":" + y3 + ":" + z);
        interiorTargets.remove(def.world + ":" + x2 + ":" + y1 + ":" + z);
        interiorTargets.remove(def.world + ":" + x2 + ":" + y2 + ":" + z);
        interiorTargets.remove(def.world + ":" + x2 + ":" + y3 + ":" + z);
    }

    private void clearPortalBlocks(PortalDef def) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(def.world());
        if (w == null) return;
        int baseY = def.cy - 2;
        int fw = 4;
        int fh = 5;
        if (def.axis == org.bukkit.Axis.X) {
            int z = def.cz;
            int xBase = def.cx - 1;
            for (int i = -1; i <= fw; i++) {
                for (int j = -2; j <= fh; j++) {
                    int x = xBase + i;
                    int y = baseY + j;
                    removeIfPortalBlock(w.getBlockAt(x, y, z));
                }
            }
            for (int i = -1; i <= fw; i++) {
                for (int j = -2; j <= fh; j++) {
                    int x = xBase + i;
                    int y = baseY + j;
                    removeIfPortalBlock(w.getBlockAt(x, y, z + 1));
                    removeIfPortalBlock(w.getBlockAt(x, y, z - 1));
                }
            }
        } else {
            int x = def.cx;
            int zBase = def.cz - 1;
            for (int i = -1; i <= fw; i++) {
                for (int j = -2; j <= fh; j++) {
                    int z = zBase + i;
                    int y = baseY + j;
                    removeIfPortalBlock(w.getBlockAt(x, y, z));
                }
            }
            for (int i = -1; i <= fw; i++) {
                for (int j = -2; j <= fh; j++) {
                    int z = zBase + i;
                    int y = baseY + j;
                    removeIfPortalBlock(w.getBlockAt(x + 1, y, z));
                    removeIfPortalBlock(w.getBlockAt(x - 1, y, z));
                }
            }
        }
        org.bukkit.Location c = new org.bukkit.Location(w, def.cx + 0.5, def.cy + 2.0, def.cz + 0.5);
        java.util.Collection<org.bukkit.entity.Entity> nearby = w.getNearbyEntities(c, 3.0, 3.0, 3.0);
        for (org.bukkit.entity.Entity e : nearby) {
            if (e instanceof org.bukkit.entity.ArmorStand as) {
                net.kyori.adventure.text.Component name = as.customName();
                String plain = name == null
                        ? ""
                        : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(name);
                if (!plain.isEmpty() && (plain.contains(def.target()) || plain.contains("跨服传送"))) {
                    e.remove();
                }
            }
        }
    }

    private void removeIfPortalBlock(org.bukkit.block.Block b) {
        org.bukkit.Material t = b.getType();
        if (t == org.bukkit.Material.OBSIDIAN
                || t == org.bukkit.Material.NETHER_PORTAL
                || t == org.bukkit.Material.GLOWSTONE
                || t == org.bukkit.Material.END_ROD
                || t == org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS
                || t == org.bukkit.Material.STONE_BRICKS) {
            b.setType(org.bukkit.Material.AIR, false);
        }
    }

    private void clearLabels(PortalDef def) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(def.world());
        if (w == null) return;
        org.bukkit.Location base = new org.bukkit.Location(w, def.cx + 0.5, def.cy + 1.9, def.cz + 0.5);
        java.util.Collection<org.bukkit.entity.Entity> nearby = w.getNearbyEntities(base, 2.5, 2.5, 2.5);
        for (org.bukkit.entity.Entity e : nearby) {
            if (e instanceof org.bukkit.entity.ArmorStand as) {
                net.kyori.adventure.text.Component name = as.customName();
                String plain = name == null
                        ? ""
                        : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(name);
                if (!plain.isEmpty() && (plain.contains(def.target()) || plain.contains("跨服传送"))) {
                    e.remove();
                }
            }
        }
    }

    private void rehydrateInterior(PortalDef def) {
        // 2x3 interior based on center/axis
        if (def.axis == org.bukkit.Axis.X) {
            int z = def.cz;
            int x1 = def.cx;
            int x2 = def.cx + 1;
            int y1 = def.cy - 1;
            int y2 = def.cy;
            int y3 = def.cy + 1;
            interiorTargets.put(def.world + ":" + x1 + ":" + y1 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x1 + ":" + y2 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x1 + ":" + y3 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x2 + ":" + y1 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x2 + ":" + y2 + ":" + z, def.target);
            interiorTargets.put(def.world + ":" + x2 + ":" + y3 + ":" + z, def.target);
        } else {
            int x = def.cx;
            int z1 = def.cz;
            int z2 = def.cz + 1;
            int y1 = def.cy - 1;
            int y2 = def.cy;
            int y3 = def.cy + 1;
            interiorTargets.put(def.world + ":" + x + ":" + y1 + ":" + z1, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y2 + ":" + z1, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y3 + ":" + z1, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y1 + ":" + z2, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y2 + ":" + z2, def.target);
            interiorTargets.put(def.world + ":" + x + ":" + y3 + ":" + z2, def.target);
        }
    }

    private void spawnLabel(PortalDef def) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(def.world());
        if (w == null) return;
        org.bukkit.Location base = new org.bukkit.Location(w, def.cx() + 0.5, def.cy() + 1.9, def.cz() + 0.5);
        java.util.Collection<org.bukkit.entity.Entity> nearby = w.getNearbyEntities(base, 2.0, 2.0, 2.0);
        for (org.bukkit.entity.Entity e : nearby) {
            if (e instanceof org.bukkit.entity.ArmorStand as) {
                Component name = as.customName();
                String plain = name == null
                        ? ""
                        : PlainTextComponentSerializer.plainText().serialize(name);
                if (!plain.isEmpty() && plain.contains(def.target())) {
                    return;
                }
            }
        }
        org.bukkit.entity.ArmorStand title = (org.bukkit.entity.ArmorStand)
                w.spawnEntity(base.clone().add(0, 0.3, 0), org.bukkit.entity.EntityType.ARMOR_STAND);
        title.setInvisible(true);
        title.setMarker(true);
        title.setGravity(false);
        title.setCustomNameVisible(true);
        title.customName(Component.text("跨服传送").color(TextColor.color(0xFFD700)));
        org.bukkit.entity.ArmorStand addr =
                (org.bukkit.entity.ArmorStand) w.spawnEntity(base, org.bukkit.entity.EntityType.ARMOR_STAND);
        addr.setInvisible(true);
        addr.setMarker(true);
        addr.setGravity(false);
        addr.setCustomNameVisible(true);
        addr.customName(Component.text(def.target()).color(TextColor.color(0x00FFFF)));
    }
}
