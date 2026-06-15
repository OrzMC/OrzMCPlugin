package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record Styles(Map<String, String> colors) {

    public static Styles from(ConfigurationSection cfg) {
        Map<String, String> colors = new HashMap<>();
        if (cfg == null) return new Styles(colors);
        ConfigurationSection colorsSection = cfg.getConfigurationSection("colors");
        if (colorsSection == null) return new Styles(colors);
        Map<String, String> defaults = Map.of(
                "success", "#00FF00",
                "info", "#55AAFF",
                "warn", "#FFAA00",
                "error", "#FF5555",
                "coord", "#55FF55",
                "player", "#FF5555",
                "unknown", "#AAAAAA",
                "tnt_alert", "#FF5555",
                "explosion_alert", "#FFAA00");
        defaults.forEach((k, v) -> colors.put(k, colorsSection.getString(k, v)));
        return new Styles(colors);
    }
}
