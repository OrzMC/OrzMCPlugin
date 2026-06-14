package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record TntConfig(
        boolean enable,
        boolean enableRespawnAnchor,
        int placeCooldownSeconds,
        long notifyThrottleMs,
        List<Map<String, Object>> whitelistRegions,
        List<String> exemptEntities) {

    @SuppressWarnings("unchecked")
    public static TntConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new TntConfig(false, false, 5, 1000L, List.of(), List.of());
        }
        boolean enable = cfg.getBoolean("enable", false);
        boolean enableRespawnAnchor = cfg.getBoolean("enable_respawn_anchor", false);
        int placeCooldownSeconds = cfg.getInt("place_cooldown", 5);
        long notifyThrottleMs = cfg.getLong("notify_throttle_ms", 1000L);
        List<Map<String, Object>> whitelistRegions = new ArrayList<>();
        Object rawRegions = cfg.get("whitelist");
        if (rawRegions instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    whitelistRegions.add((Map<String, Object>) m);
                }
            }
        }
        List<String> exemptEntities = new ArrayList<>();
        Object rawExempt = cfg.get("exempt_entities");
        if (rawExempt instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) exemptEntities.add(String.valueOf(o));
            }
        }
        return new TntConfig(
                enable,
                enableRespawnAnchor,
                placeCooldownSeconds,
                notifyThrottleMs,
                whitelistRegions,
                exemptEntities);
    }
}
