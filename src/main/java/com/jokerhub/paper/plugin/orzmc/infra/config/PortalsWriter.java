package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs.Portals.PortalEntry;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

public final class PortalsWriter {
    private PortalsWriter() {}

    public static void write(FileConfiguration cfg, Map<String, PortalEntry> entries) {
        if (cfg == null || entries == null) return;
        Map<String, Map<String, String>> grouped = new java.util.HashMap<>();
        for (Map.Entry<String, PortalEntry> e : entries.entrySet()) {
            String center = e.getKey();
            PortalEntry pe = e.getValue();
            String safe = SafeKeys.encodeTargetKey(pe.target());
            grouped.computeIfAbsent(safe, k -> new java.util.HashMap<>()).put(center, pe.axis());
        }
        Object raw = cfg.get("portals");
        if (raw instanceof org.bukkit.configuration.ConfigurationSection sec) {
            for (String k : sec.getKeys(false)) {
                cfg.set("portals." + k, null);
            }
        }
        for (Map.Entry<String, Map<String, String>> ge : grouped.entrySet()) {
            String target = ge.getKey();
            for (Map.Entry<String, String> ce : ge.getValue().entrySet()) {
                cfg.set("portals." + target + "." + ce.getKey(), ce.getValue());
            }
        }
    }
}
