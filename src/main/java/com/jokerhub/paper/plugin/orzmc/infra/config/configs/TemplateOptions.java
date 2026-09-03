package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record TemplateOptions(
        Map<String, String> stageCnMap,
        String rateUnit,
        String etaUnit,
        Map<String, String> worldAlias,
        double coordScale,
        int coordPrecision,
        String coordUnitLabel) {

    public TemplateOptions(
            Map<String, String> stageCnMap,
            String rateUnit,
            String etaUnit,
            Map<String, String> worldAlias,
            double coordScale,
            String coordUnitLabel) {
        this(stageCnMap, rateUnit, etaUnit, worldAlias, coordScale, 2, coordUnitLabel);
    }

    public static TemplateOptions from(ConfigurationSection cfg) {
        Map<String, String> m = new HashMap<>();
        Object raw = cfg.get("templates.stage_cn");
        if (raw instanceof ConfigurationSection sec) {
            for (String k : sec.getKeys(false)) {
                String v = sec.getString(k);
                if (v != null) m.put(k, v);
            }
        }
        String rate = cfg.getString("templates.progress_units.rate", "per_sec");
        String eta = cfg.getString("templates.progress_units.eta", "ms");
        Map<String, String> worldAlias = new HashMap<>();
        Object wa = cfg.get("templates.world_alias");
        if (wa instanceof ConfigurationSection sec2) {
            for (String k : sec2.getKeys(false)) {
                String v = sec2.getString(k);
                if (v != null) worldAlias.put(k, v);
            }
        }
        worldAlias.putIfAbsent("world", "主世界");
        worldAlias.putIfAbsent("world_nether", "下界");
        worldAlias.putIfAbsent("world_the_end", "末地");
        double coordScale = cfg.getDouble("templates.coord.scale", 1.0);
        int coordPrecision = cfg.getInt("templates.coord.precision", 2);
        String coordUnitLabel = cfg.getString("templates.coord.unit_label", "block");
        return new TemplateOptions(m, rate, eta, worldAlias, coordScale, coordPrecision, coordUnitLabel);
    }
}
