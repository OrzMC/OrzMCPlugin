package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import com.jokerhub.paper.plugin.orzmc.infra.config.SafeKeys;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record Portals(Map<String, PortalEntry> entries) {

    public record PortalEntry(String target, String axis) {}

    public static Portals from(ConfigurationSection cfg) {
        Map<String, PortalEntry> entries = new HashMap<>();
        if (cfg == null) return new Portals(entries);
        Object raw = cfg.get("portals");
        if (raw instanceof ConfigurationSection sec) {
            for (String targetKey : sec.getKeys(false)) {
                ConfigurationSection centers = sec.getConfigurationSection(targetKey);
                if (centers == null) continue;
                String target = SafeKeys.decodeTargetKey(targetKey);
                for (String center : centers.getKeys(false)) {
                    String axis = centers.getString(center, "X");
                    entries.put(center, new PortalEntry(target, axis));
                }
            }
        }
        return new Portals(entries);
    }
}
