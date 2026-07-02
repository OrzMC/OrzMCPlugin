package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplatePlaceholderValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigHealthCheck {
    private ConfigHealthCheck() {}

    public static List<String> validateAll(AdvancedConfigManager mgr) {
        return validateAll(mgr::getConfig);
    }

    public static List<String> validateAll(Function<String, FileConfiguration> provider) {
        List<String> issues = new ArrayList<>();
        validateConfig(provider.apply("config"), provider, issues);
        validateBot(provider.apply("bot"), issues);
        validateTemplates(provider.apply("templates"), provider, issues);
        validatePortals(provider.apply("portals"), issues);
        return issues;
    }

    private static void validateConfig(
            FileConfiguration cfg, Function<String, FileConfiguration> provider, List<String> issues) {
        if (cfg == null) {
            issues.add("config.yml 未加载");
            return;
        }
        validateWhitelistSection(cfg.getConfigurationSection("whitelist"), issues);
        validateMaintenanceSection(cfg.getConfigurationSection("maintenance"), issues);
        validateTntSection(cfg.getConfigurationSection("tnt"), issues);
        validateGeoIpSection(cfg.getConfigurationSection("geoip"), issues);
        validateCommandPoliciesSection(cfg.getConfigurationSection("command_policies"), issues);
    }

    private static void validateWhitelistSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 whitelist 配置段");
            return;
        }
        Object fw = section.get("force_whitelist");
        if (!(fw instanceof Boolean)) issues.add("类型错误: whitelist.force_whitelist 需为布尔值");
        int days = section.getInt("cleanup_inactive_days", 90);
        if (days <= 0) issues.add("非法: whitelist.cleanup_inactive_days 必须为正数");
        int ticks = section.getInt("pagination_delay_ticks", 5);
        if (ticks < 0) issues.add("非法: whitelist.pagination_delay_ticks 不得为负数");
        ConfigurationSection kickSection = section.getConfigurationSection("kick_message");
        if (kickSection == null) {
            issues.add("缺失: whitelist.kick_message 未配置");
        } else {
            String title = kickSection.getString("title", "");
            if (title.isEmpty()) issues.add("缺失: whitelist.kick_message.title 不可为空");
            String qqGroupId = kickSection.getString("qq_group_id", "");
            if (qqGroupId.isEmpty()) issues.add("建议: whitelist.kick_message.qq_group_id 未配置，将使用 bot.qq_group_id 作为默认值");
            List<?> ups = kickSection.getList("ups");
            if (ups == null || ups.isEmpty()) issues.add("缺失: whitelist.kick_message.ups 至少需要一项");
        }
    }

    private static void validateMaintenanceSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 maintenance 配置段");
            return;
        }
        Object en = section.get("optimize_enabled");
        if (!(en instanceof Boolean)) issues.add("类型错误: maintenance.optimize_enabled 需为布尔值");
        int thr = section.getInt("optimize_tick_time_threshold", 300);
        if (thr <= 0) issues.add("非法: maintenance.optimize_tick_time_threshold 必须为正数");
        int retain = section.getInt("backup_retention_count", 5);
        if (retain < 0) issues.add("非法: maintenance.backup_retention_count 不得为负数");
        String motd = section.getString("backup_maintenance_motd", "");
        if (motd.isEmpty()) issues.add("缺失: maintenance.backup_maintenance_motd");
    }

    private static void validateTntSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 tnt 配置段");
            return;
        }
        Object enable = section.get("enable");
        if (enable != null && !(enable instanceof Boolean)) issues.add("类型错误: tnt.enable 需为布尔值");
        Object enableAnchor = section.get("enable_respawn_anchor");
        if (enableAnchor != null && !(enableAnchor instanceof Boolean))
            issues.add("类型错误: tnt.enable_respawn_anchor 需为布尔值");
        int cd = section.getInt("place_cooldown", 0);
        if (cd < 0) issues.add("非法: tnt.place_cooldown 不得为负数");
        long thr = section.getLong("notify_throttle_ms", 1000L);
        if (thr < 0) issues.add("非法: tnt.notify_throttle_ms 不得为负数");
        Object wl = section.get("whitelist");
        if (wl != null && !(wl instanceof List<?>)) issues.add("类型错误: tnt.whitelist 需为列表");
        Object exempt = section.get("exempt_entities");
        if (exempt != null && !(exempt instanceof List<?>)) issues.add("类型错误: tnt.exempt_entities 需为列表");
    }

    private static void validateGeoIpSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 geoip 配置段");
            return;
        }
        Object raw = section.get("allow_country_code");
        if (raw == null) {
            issues.add("建议: geoip.allow_country_code 未配置，默认允许所有地区");
        } else if (raw instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    issues.add("非法: geoip.allow_country_code 不允许空项");
                } else {
                    String code = String.valueOf(o);
                    if (!code.matches("^[A-Z]{2}$")) {
                        issues.add("非法: geoip.allow_country_code '" + code + "' 必须为大写两位国家码");
                    }
                }
            }
        } else {
            issues.add("类型错误: geoip.allow_country_code 需为列表");
        }
    }

    private static void validateCommandPoliciesSection(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 command_policies 配置段");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                issues.add("非法: command_policies." + key + " 需为对象");
                continue;
            }
            Object cd = s.get("cooldown_secs");
            if (cd != null) {
                try {
                    int val = Integer.parseInt(String.valueOf(cd));
                    if (val < 0) issues.add("非法: command_policies." + key + ".cooldown_secs 不得为负数");
                } catch (Exception e) {
                    issues.add("类型错误: command_policies." + key + ".cooldown_secs 需为数字");
                }
            }
            Object ao = s.get("admin_only");
            if (ao != null && !(ao instanceof Boolean))
                issues.add("类型错误: command_policies." + key + ".admin_only 需为布尔值");
        }
    }

    private static void validateBot(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("bot.yml 未加载");
            return;
        }
        String[] boolKeys = {"enable_qq_bot", "enable_discord_bot", "enable_lark_bot", "ws_message_log_enabled"};
        for (String k : boolKeys) {
            Object v = cfg.get(k);
            if (v != null && !(v instanceof Boolean)) issues.add("类型错误: bot." + k + " 需为布尔值");
        }
        Object prompt = cfg.get("cmd_prompt_char");
        if (prompt != null && String.valueOf(prompt).isEmpty()) issues.add("非法: bot.cmd_prompt_char 不可为空");
        int httpConn = cfg.getInt("http_connect_timeout_seconds", 3);
        int httpReq = cfg.getInt("http_request_timeout_seconds", 3);
        int httpRetries = cfg.getInt("http_max_retries", 3);
        if (httpConn <= 0) issues.add("非法: bot.http_connect_timeout_seconds 必须为正数");
        if (httpReq <= 0) issues.add("非法: bot.http_request_timeout_seconds 必须为正数");
        if (httpRetries < 0) issues.add("非法: bot.http_max_retries 不得为负数");
    }

    private static void validatePortals(FileConfiguration cfg, List<String> issues) {
        if (cfg == null) {
            issues.add("portals.yml 未加载");
            return;
        }
        Object raw = cfg.get("portals");
        if (raw instanceof ConfigurationSection sec) {
            for (String k : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) {
                    issues.add("非法: portals." + k + " 节点为空");
                    continue;
                }
                String target = SafeKeys.decodeTargetKey(k);
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

    private static void validateTemplates(
            FileConfiguration cfg, Function<String, FileConfiguration> provider, List<String> issues) {
        if (cfg == null) {
            issues.add("templates.yml 未加载");
            return;
        }
        issues.addAll(TemplatePlaceholderValidator.validate(cfg));

        // Validate notifications section
        ConfigurationSection notificationsSection = cfg.getConfigurationSection("notifications");
        if (notificationsSection == null) {
            issues.add("templates.yml 缺失 notifications 配置段");
        } else {
            validateNotificationsSection(notificationsSection, provider.apply("bot"), cfg, issues);
        }

        // Validate styles section
        ConfigurationSection stylesSection = cfg.getConfigurationSection("styles");
        if (stylesSection == null) {
            issues.add("templates.yml 缺失 styles 配置段");
        } else {
            validateStylesSection(stylesSection, issues);
        }

        // Validate templates section
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
        if (wal != null && !(wal instanceof ConfigurationSection)) {
            issues.add("类型错误: templates.i18n.world_alias 需为对象映射");
        }
        Object ral = cfg.get("templates.i18n.role_alias");
        if (ral != null && !(ral instanceof ConfigurationSection)) {
            issues.add("类型错误: templates.i18n.role_alias 需为对象映射");
        }
        Object sal = cfg.get("templates.i18n.stage_alias");
        if (sal != null && !(sal instanceof ConfigurationSection)) {
            issues.add("类型错误: templates.i18n.stage_alias 需为对象映射");
        }
        Object cal = cfg.get("templates.i18n.command");
        if (cal != null && !(cal instanceof ConfigurationSection)) {
            issues.add("类型错误: templates.i18n.command 需为对象映射");
        }
        if (!cfg.contains("templates.world_alias.world")) issues.add("建议: templates.world_alias.world 缺失");
        if (!cfg.contains("templates.world_alias.world_nether"))
            issues.add("建议: templates.world_alias.world_nether 缺失");
        if (!cfg.contains("templates.world_alias.world_the_end"))
            issues.add("建议: templates.world_alias.world_the_end 缺失");
        if (!cfg.contains("templates.role_alias.admin")) issues.add("建议: templates.role_alias.admin 缺失");
        if (!cfg.contains("templates.role_alias.member")) issues.add("建议: templates.role_alias.member 缺失");
        if (!cfg.contains("templates.role_groups")) issues.add("建议: templates.role_groups 未配置，将使用默认别名");
        String[] commandKeys = {
            "command_output",
            "command_help",
            "command_players",
            "command_whitelist_header",
            "command_whitelist_page",
            "command_whitelist_cleanup",
            "command_whitelist_add_result",
            "command_whitelist_remove_result",
            "command_admin_required",
            "command_usage",
            "command_backup",
            "command_optimize",
            "command_optimize_disabled",
            "command_blacklist_list",
            "command_blacklist_add",
            "command_blacklist_remove",
            "command_blacklist_error"
        };
        String[] requiredTemplates = {
            "command_output",
            "command_help",
            "command_players",
            "command_whitelist_header",
            "command_whitelist_page",
            "command_whitelist_cleanup",
            "command_whitelist_add_result",
            "command_whitelist_remove_result",
            "command_blacklist_list",
            "command_blacklist_add",
            "command_blacklist_remove",
            "command_blacklist_error",
            "command_admin_required",
            "command_usage",
            "command_backup",
            "command_optimize",
            "command_optimize_disabled",
            "server_load",
            "server_stop",
            "whitelist_block",
            "whitelist_toggle_alert",
            "player_join",
            "player_quit",
            "player_kick",
            "exception_alert",
            "geoip_block",
            "tnt_alert",
            "maintenance_backup_stage",
            "maintenance_backup_done",
            "maintenance_backup_error",
            "maintenance_optimize_stage",
            "maintenance_optimize_done",
            "maintenance_optimize_error",
            "server_maintenance_hint"
        };
        for (String key : requiredTemplates) {
            if (!cfg.contains("templates." + key)) {
                issues.add("缺失: templates." + key);
            }
        }
        if (cal instanceof ConfigurationSection cmdSec) {
            for (String localeKey : cmdSec.getKeys(false)) {
                ConfigurationSection langSec = cmdSec.getConfigurationSection(localeKey);
                if (langSec == null) continue;
                for (String key : commandKeys) {
                    if (!langSec.contains(key)) {
                        issues.add("建议: templates.i18n.command." + localeKey + "." + key + " 缺失");
                    }
                }
            }
        }
        Object rawFmt = cfg.get("templates.format");
        if (rawFmt instanceof ConfigurationSection sec) {
            for (String key : sec.getKeys(false)) {
                String raw = sec.getString(key, "DEFAULT");
                if (raw.isEmpty()) {
                    issues.add("非法: templates.format." + key + " 不可为空");
                    continue;
                }
                String v = raw.toUpperCase();
                if (!("DEFAULT".equals(v) || "PLAIN".equals(v) || "CODE_BLOCK".equals(v))) {
                    issues.add("非法: templates.format." + key + " 值无效: " + raw);
                }
                if (!cfg.contains("templates." + key)) {
                    issues.add("建议: templates.format." + key + " 未找到对应模板");
                }
            }
        }
    }

    private static void validateNotificationsSection(
            ConfigurationSection section,
            FileConfiguration botCfg,
            FileConfiguration templatesCfg,
            List<String> issues) {
        String key = "notifications.tnt_alert.public.enabled";
        Object v = section.get("tnt_alert.public.enabled");
        if (!(v instanceof Boolean)) issues.add("类型错误: " + key + " 需为布尔值");
        for (String eventKey : section.getKeys(false)) {
            if (templatesCfg != null && !templatesCfg.contains("templates." + eventKey)) {
                issues.add("通知事件缺少模板: notifications." + eventKey);
            }
            String ckey = section.getString(eventKey + ".channel_key", "");
            if (ckey.isEmpty()) continue;
            String qq = botCfg == null ? null : botCfg.getString("channels." + ckey + ".qq");
            String discord = botCfg == null ? null : botCfg.getString("channels." + ckey + ".discord");
            String lark = botCfg == null ? null : botCfg.getString("channels." + ckey + ".lark");
            if ((qq == null || qq.isEmpty())
                    && (discord == null || discord.isEmpty())
                    && (lark == null || lark.isEmpty())) {
                issues.add("通知频道未映射: notifications." + eventKey + ".channel_key=" + ckey);
            }
        }
    }

    private static void validateStylesSection(ConfigurationSection section, List<String> issues) {
        ConfigurationSection colorsSection = section.getConfigurationSection("colors");
        if (colorsSection == null) {
            issues.add("templates.yml styles 缺失 colors 配置段");
            return;
        }
        String[] keys = {
            "success", "info", "warn", "error", "coord", "player", "unknown", "tnt_alert", "explosion_alert"
        };
        for (String k : keys) {
            Object v = colorsSection.get(k);
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
}
