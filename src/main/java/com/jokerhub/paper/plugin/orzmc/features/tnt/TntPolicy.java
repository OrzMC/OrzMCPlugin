package com.jokerhub.paper.plugin.orzmc.features.tnt;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;

public class TntPolicy {
    public record Region(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, String world) {
        public boolean contains(Location loc) {
            return loc.getWorld().getName().equals(world)
                    && loc.getX() >= minX
                    && loc.getX() <= maxX
                    && loc.getY() >= minY
                    && loc.getY() <= maxY
                    && loc.getZ() >= minZ
                    && loc.getZ() <= maxZ;
        }

        public boolean contains(String worldName, double x, double y, double z) {
            return world.equals(worldName)
                    && x >= minX
                    && x <= maxX
                    && y >= minY
                    && y <= maxY
                    && z >= minZ
                    && z <= maxZ;
        }
    }

    private final boolean enableTnt;
    private final boolean enableRespawnAnchor;
    private final int placeCooldownSeconds;
    private final long notifyThrottleMs;
    private final List<Region> whitelistRegions = new ArrayList<>();
    private final List<String> exemptEntities;

    public TntPolicy(TntConfig cfg) {
        this.enableTnt = cfg.enable();
        this.enableRespawnAnchor = cfg.enableRespawnAnchor();
        this.placeCooldownSeconds = cfg.placeCooldownSeconds();
        this.notifyThrottleMs = cfg.notifyThrottleMs();
        this.exemptEntities = cfg.exemptEntities();
        for (Map<String, Object> m : cfg.whitelistRegions()) {
            int minX = ((Number) m.getOrDefault("minX", 0)).intValue();
            int maxX = ((Number) m.getOrDefault("maxX", 0)).intValue();
            int minY = ((Number) m.getOrDefault("minY", 0)).intValue();
            int maxY = ((Number) m.getOrDefault("maxY", 0)).intValue();
            int minZ = ((Number) m.getOrDefault("minZ", 0)).intValue();
            int maxZ = ((Number) m.getOrDefault("maxZ", 0)).intValue();
            String world = String.valueOf(m.getOrDefault("world", "world"));
            whitelistRegions.add(new Region(
                    Math.min(minX, maxX),
                    Math.max(minX, maxX),
                    Math.min(minY, maxY),
                    Math.max(minY, maxY),
                    Math.min(minZ, maxZ),
                    Math.max(minZ, maxZ),
                    world));
        }
    }

    public boolean isEnableTnt() {
        return enableTnt;
    }

    public boolean isEnableRespawnAnchor() {
        return enableRespawnAnchor;
    }

    public int getPlaceCooldownSeconds() {
        return placeCooldownSeconds;
    }

    public long getNotifyThrottleMs() {
        return notifyThrottleMs;
    }

    public List<String> getExemptEntities() {
        return exemptEntities;
    }

    public boolean isNotInWhiteList(Location loc) {
        for (Region r : whitelistRegions) {
            if (r.contains(loc)) return false;
        }
        return true;
    }

    public boolean isNotInWhiteList(String worldName, double x, double y, double z) {
        for (Region r : whitelistRegions) {
            if (r.contains(worldName, x, y, z)) return false;
        }
        return true;
    }
}
