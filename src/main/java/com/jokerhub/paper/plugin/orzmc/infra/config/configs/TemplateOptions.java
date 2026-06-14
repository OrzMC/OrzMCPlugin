package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record TemplateOptions(
        Map<String, String> stageCnMap,
        String rateUnit,
        String etaUnit,
        Map<String, String> worldAlias,
        Map<String, Map<String, String>> worldAliasLocalized,
        double coordScale,
        int coordPrecision,
        String coordUnitLabel,
        Map<String, String> roleAlias,
        String locale,
        Map<String, Map<String, String>> roleAliasLocalized,
        Map<String, String> roleGroupAliases,
        Map<String, Map<String, String>> stageAliasLocalized) {

    public TemplateOptions(
            Map<String, String> stageCnMap,
            String rateUnit,
            String etaUnit,
            Map<String, String> worldAlias,
            Map<String, Map<String, String>> worldAliasLocalized,
            double coordScale,
            String coordUnitLabel,
            Map<String, String> roleAlias,
            String locale,
            Map<String, Map<String, String>> roleAliasLocalized,
            Map<String, String> roleGroupAliases,
            Map<String, Map<String, String>> stageAliasLocalized) {
        this(
                stageCnMap,
                rateUnit,
                etaUnit,
                worldAlias,
                worldAliasLocalized,
                coordScale,
                2,
                coordUnitLabel,
                roleAlias,
                locale,
                roleAliasLocalized,
                roleGroupAliases,
                stageAliasLocalized);
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
        Map<String, Map<String, String>> worldAliasLocalized = new HashMap<>();
        Object wal = cfg.get("templates.i18n.world_alias");
        if (wal instanceof ConfigurationSection secw) {
            for (String lang : secw.getKeys(false)) {
                ConfigurationSection langSec = secw.getConfigurationSection(lang);
                if (langSec != null) {
                    Map<String, String> map = new HashMap<>();
                    for (String k : langSec.getKeys(false)) {
                        String v = langSec.getString(k);
                        if (v != null) map.put(k, v);
                    }
                    worldAliasLocalized.put(lang, map);
                }
            }
        }
        worldAlias.putIfAbsent("world", "主世界");
        worldAlias.putIfAbsent("world_nether", "下界");
        worldAlias.putIfAbsent("world_the_end", "末地");
        Map<String, String> roleAlias = new HashMap<>();
        Object ra = cfg.get("templates.role_alias");
        if (ra instanceof ConfigurationSection sec3) {
            for (String k : sec3.getKeys(false)) {
                String v = sec3.getString(k);
                if (v != null) roleAlias.put(k, v);
            }
        }
        roleAlias.putIfAbsent("admin", "管理员");
        roleAlias.putIfAbsent("member", "玩家");
        String locale = cfg.getString("templates.locale", "zh-CN");
        Map<String, Map<String, String>> roleAliasLocalized = new HashMap<>();
        Object ral = cfg.get("templates.i18n.role_alias");
        if (ral instanceof ConfigurationSection sec4) {
            for (String lang : sec4.getKeys(false)) {
                ConfigurationSection langSec = sec4.getConfigurationSection(lang);
                if (langSec != null) {
                    Map<String, String> map = new HashMap<>();
                    for (String k : langSec.getKeys(false)) {
                        String v = langSec.getString(k);
                        if (v != null) map.put(k, v);
                    }
                    roleAliasLocalized.put(lang, map);
                }
            }
        }
        Map<String, Map<String, String>> stageAliasLocalized = new HashMap<>();
        Object sal = cfg.get("templates.i18n.stage_alias");
        if (sal instanceof ConfigurationSection secs) {
            for (String lang : secs.getKeys(false)) {
                ConfigurationSection langSec = secs.getConfigurationSection(lang);
                if (langSec != null) {
                    Map<String, String> map = new HashMap<>();
                    for (String k : langSec.getKeys(false)) {
                        String v = langSec.getString(k);
                        if (v != null) map.put(k, v);
                    }
                    stageAliasLocalized.put(lang, map);
                }
            }
        }
        Map<String, String> roleGroups = new HashMap<>();
        Object rg = cfg.get("templates.role_groups");
        if (rg instanceof ConfigurationSection sec5) {
            for (String k : sec5.getKeys(false)) {
                String v = sec5.getString(k);
                if (v != null) roleGroups.put(k, v);
            }
        }
        roleGroups.putIfAbsent("orzmc.admin", "管理员");
        roleGroups.putIfAbsent("default", "玩家");
        double coordScale = cfg.getDouble("templates.coord.scale", 1.0);
        int coordPrecision = cfg.getInt("templates.coord.precision", 2);
        String coordUnitLabel = cfg.getString("templates.coord.unit_label", "block");
        return new TemplateOptions(
                m, rate, eta, worldAlias, worldAliasLocalized,
                coordScale, coordPrecision, coordUnitLabel, roleAlias,
                locale, roleAliasLocalized, roleGroups, stageAliasLocalized);
    }
}
