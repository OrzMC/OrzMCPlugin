package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigHealthCheck {
    private ConfigHealthCheck() {}

    public static List<String> validateAll(com.jokerhub.paper.plugin.orzmc.infra.config.AdvancedConfigManager mgr) {
        List<String> issues = new ArrayList<>();
        validateStyles(mgr.getConfig("styles"), issues);
        validateIpWhitelist(mgr.getConfig("ip_whitelist"), issues);
        validatePortals(mgr.getConfig("portals"), issues);
        validateTemplates(mgr.getConfig("templates"), issues);
        validateNotifications(mgr.getConfig("notifications"), issues);
        validateCommands(mgr.getConfig("commands"), issues);
        validateWhitelist(mgr.getConfig("whitelist"), issues);
        validateMaintenance(mgr.getConfig("maintenance"), issues);
        return issues;
    }

    private static void validateStyles(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("styles.yml 未加载");
            return;
        }
        String base = "styles.colors.";
        String[] keys = {
            "success", "info", "warn", "error", "coord", "player", "unknown", "tnt_alert", "explosion_alert"
        };
        for (String k : keys) {
            Object v = cfg.get(base + k);
            if (v == null) {
                issues.add("缺失: styles.colors." + k);
            } else {
                String s = String.valueOf(v);
                if (!s.matches("^#[0-9A-Fa-f]{6}$")) {
                    issues.add("非法: styles.colors." + k + " 必须为 #RRGGBB");
                }
            }
        }
    }

    private static void validatePortals(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("portals.yml 未加载");
            return;
        }
        Object raw = cfg.get("portals");
        if (raw instanceof org.bukkit.configuration.ConfigurationSection sec) {
            for (String k : sec.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) {
                    issues.add("非法: portals." + k + " 节点为空");
                    continue;
                }
                String target = com.jokerhub.paper.plugin.orzmc.infra.config.SafeKeys.decodeTargetKey(k);
                for (String center : s.getKeys(false)) {
                    String[] parts = center.split(":");
                    if (parts.length != 4) {
                        issues.add("非法: portals." + target + " 下键需为 world:cx:cy:cz");
                        continue;
                    }
                    try {
                        Integer.parseInt(parts[1]);
                        Integer.parseInt(parts[2]);
                        Integer.parseInt(parts[3]);
                    } catch (Exception e) {
                        issues.add("非法: portals 坐标需为整数");
                    }
                    String axis = s.getString(center, "X");
                    if (!(axis.equalsIgnoreCase("X") || axis.equalsIgnoreCase("Z"))) {
                        issues.add("非法: portals 轴向取值 X/Z");
                    }
                }
                if (target.contains(":")) {
                    String[] hp = target.split(":");
                    if (hp.length == 2) {
                        try {
                            int port = Integer.parseInt(hp[1]);
                            if (port <= 0 || port > 65535) {
                                issues.add("非法: portals 端口范围 1-65535");
                            }
                        } catch (Exception e) {
                            issues.add("类型错误: portals 端口需为数字");
                        }
                    }
                }
            }
        }
    }

    private static void validateIpWhitelist(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("ip_whitelist.yml 未加载");
            return;
        }
        Object raw = cfg.get("allow_country_code");
        if (raw == null) {
            issues.add("建议: ip_whitelist.allow_country_code 未配置，默认允许所有地区");
        } else if (raw instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    issues.add("非法: ip_whitelist.allow_country_code 不允许空项");
                } else {
                    String code = String.valueOf(o);
                    if (!code.matches("^[A-Z]{2}$")) {
                        issues.add("非法: ip_whitelist.allow_country_code '" + code + "' 必须为大写两位国家码");
                    }
                }
            }
        } else {
            issues.add("类型错误: ip_whitelist.allow_country_code 需为列表");
        }
    }

    private static void validateTemplates(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("templates.yml 未加载");
            return;
        }
        String base = "templates";
        if (!cfg.contains(base + ".player_join")) issues.add("缺失: templates.player_join");
        double scale = cfg.getDouble(base + ".coord.scale", 1.0);
        if (scale <= 0) issues.add("非法: templates.coord.scale 必须为正数");
        int precision = cfg.getInt(base + ".coord.precision", 2);
        if (precision < 0) issues.add("非法: templates.coord.precision 不得为负数");
        String unit = cfg.getString(base + ".coord.unit_label", "block");
        if (unit.isEmpty()) issues.add("缺失: templates.coord.unit_label");
        String rate = cfg.getString(base + ".progress_units.rate", "per_sec");
        if (!(rate.equalsIgnoreCase("per_sec") || rate.equalsIgnoreCase("per_min"))) {
            issues.add("非法: templates.progress_units.rate 取值 per_sec/per_min");
        }
        String eta = cfg.getString(base + ".progress_units.eta", "ms");
        if (!(eta.equalsIgnoreCase("ms") || eta.equalsIgnoreCase("sec") || eta.equalsIgnoreCase("min"))) {
            issues.add("非法: templates.progress_units.eta 取值 ms/sec/min");
        }
        String locale = cfg.getString(base + ".locale", "zh-CN");
        if (locale.isEmpty()) issues.add("缺失: templates.locale");
        Object wal = cfg.get("templates.i18n.world_alias");
        if (wal != null && !(wal instanceof org.bukkit.configuration.ConfigurationSection)) {
            issues.add("类型错误: templates.i18n.world_alias 需为对象映射");
        }
        Object ral = cfg.get("templates.i18n.role_alias");
        if (ral != null && !(ral instanceof org.bukkit.configuration.ConfigurationSection)) {
            issues.add("类型错误: templates.i18n.role_alias 需为对象映射");
        }
        Object sal = cfg.get("templates.i18n.stage_alias");
        if (sal != null && !(sal instanceof org.bukkit.configuration.ConfigurationSection)) {
            issues.add("类型错误: templates.i18n.stage_alias 需为对象映射");
        }
        // 基础别名存在性
        if (!cfg.contains("templates.world_alias.world")) issues.add("建议: templates.world_alias.world 缺失");
        if (!cfg.contains("templates.world_alias.world_nether"))
            issues.add("建议: templates.world_alias.world_nether 缺失");
        if (!cfg.contains("templates.world_alias.world_the_end"))
            issues.add("建议: templates.world_alias.world_the_end 缺失");
        if (!cfg.contains("templates.role_alias.admin")) issues.add("建议: templates.role_alias.admin 缺失");
        if (!cfg.contains("templates.role_alias.member")) issues.add("建议: templates.role_alias.member 缺失");
    }

    private static void validateNotifications(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("notifications.yml 未加载");
            return;
        }
        String key = "notifications.tnt_alert.public.enabled";
        Object v = cfg.get(key);
        if (!(v instanceof Boolean)) issues.add("类型错误: " + key + " 需为布尔值");
        String ck = "notifications.tnt_alert.channel_key";
        Object ch = cfg.get(ck);
        if (ch != null && String.valueOf(ch).isEmpty()) issues.add("非法: " + ck + " 不可为空字符串");
    }

    private static void validateCommands(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("commands.yml 未加载");
            return;
        }
        Object r1 = cfg.get("commands.tpbow.cooldown_secs");
        if (r1 != null) {
            try {
                int val = Integer.parseInt(String.valueOf(r1));
                if (val < 0) issues.add("非法: commands.tpbow.cooldown_secs 不得为负数");
            } catch (Exception e) {
                issues.add("类型错误: commands.tpbow.cooldown_secs 需为数字");
            }
        }
        Object r2 = cfg.get("commands.tpbow.admin_only");
        if (r2 != null && !(r2 instanceof Boolean)) issues.add("类型错误: commands.tpbow.admin_only 需为布尔值");
    }

    private static void validateWhitelist(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("whitelist.yml 未加载");
            return;
        }
        Object fw = cfg.get("force_whitelist");
        if (!(fw instanceof Boolean)) issues.add("类型错误: whitelist.force_whitelist 需为布尔值");
        int days = cfg.getInt("cleanup_inactive_days", 90);
        if (days <= 0) issues.add("非法: whitelist.cleanup_inactive_days 必须为正数");
        int ticks = cfg.getInt("pagination_delay_ticks", 5);
        if (ticks < 0) issues.add("非法: whitelist.pagination_delay_ticks 不得为负数");
    }

    private static void validateMaintenance(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("maintenance.yml 未加载");
            return;
        }
        Object en = cfg.get("optimize_enabled");
        if (!(en instanceof Boolean)) issues.add("类型错误: maintenance.optimize_enabled 需为布尔值");
        Object os = cfg.get("optimize_on_shutdown");
        if (!(os instanceof Boolean)) issues.add("类型错误: maintenance.optimize_on_shutdown 需为布尔值");
        int thr = cfg.getInt("optimize_tick_time_threshold", 300);
        if (thr <= 0) issues.add("非法: maintenance.optimize_tick_time_threshold 必须为正数");
        int retain = cfg.getInt("backup_retention_count", 5);
        if (retain < 0) issues.add("非法: maintenance.backup_retention_count 不得为负数");
        String motd = cfg.getString("backup_maintenance_motd", "");
        if (motd.isEmpty()) issues.add("缺失: maintenance.backup_maintenance_motd");
    }
}
