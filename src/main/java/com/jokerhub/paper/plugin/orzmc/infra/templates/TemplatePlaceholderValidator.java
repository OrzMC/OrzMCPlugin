package com.jokerhub.paper.plugin.orzmc.infra.templates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class TemplatePlaceholderValidator {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    private TemplatePlaceholderValidator() {}

    public static List<String> validate(FileConfiguration templatesCfg) {
        List<String> issues = new ArrayList<>();
        if (templatesCfg == null) {
            issues.add("templates.yml 未加载");
            return issues;
        }

        Map<String, Set<String>> allowedByKey = allowedVarsByTemplateKey();

        for (String key : allowedByKey.keySet()) {
            String tpl = templatesCfg.getString("templates." + key, "");
            if (tpl.isEmpty()) {
                continue;
            }
            validateTemplate(key, tpl, allowedByKey.getOrDefault(key, Set.of()), issues);
        }

        Object rawCmd = templatesCfg.get("templates.i18n.command");
        if (rawCmd instanceof ConfigurationSection cmdSec) {
            for (String locale : cmdSec.getKeys(false)) {
                ConfigurationSection lang = cmdSec.getConfigurationSection(locale);
                if (lang == null) continue;
                for (String key : commandTemplateKeys()) {
                    String tpl = lang.getString(key, "");
                    if (tpl.isEmpty()) {
                        continue;
                    }
                    validateTemplate(
                            "i18n.command." + locale + "." + key,
                            tpl,
                            allowedByKey.getOrDefault(key, Set.of()),
                            issues);
                }
            }
        }

        return issues;
    }

    private static void validateTemplate(String key, String template, Set<String> allowed, List<String> issues) {
        Set<String> used = extractPlaceholders(template);
        for (String var : used) {
            if (!allowed.contains(var)) {
                issues.add("模板变量未知: templates." + key + " {" + var + "}");
            }
        }
    }

    private static Set<String> extractPlaceholders(String template) {
        Set<String> vars = new HashSet<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            String v = m.group(1);
            if (v != null && !v.isEmpty()) vars.add(v);
        }
        return vars;
    }

    private static List<String> commandTemplateKeys() {
        return List.of(
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
                "command_optimize_disabled");
    }

    private static Map<String, Set<String>> allowedVarsByTemplateKey() {
        Map<String, Set<String>> m = new HashMap<>();
        Set<String> playerVars = Set.of(
                "name",
                "world",
                "world_alias",
                "x",
                "y",
                "z",
                "x_unit",
                "y_unit",
                "z_unit",
                "coord_unit",
                "role",
                "role_alias",
                "online_count",
                "max_count",
                "online_list");
        m.put("player_join", playerVars);
        m.put("player_quit", playerVars);
        m.put("player_kick", playerVars);
        m.put("exception_alert", Set.of("message", "stack_summary"));
        m.put("geoip_block", Set.of("name", "ip", "country_code", "allow_list", "address_info"));
        m.put(
                "tnt_alert",
                Set.of("msg", "world_alias", "x_unit", "y_unit", "z_unit", "coord_unit", "actor", "block_type"));
        Set<String> progressVars = Set.of(
                "label",
                "stage",
                "stage_name",
                "stage_i18n",
                "percent",
                "current",
                "total",
                "rate_per",
                "rate_unit",
                "eta_value",
                "eta_unit");
        m.put("maintenance_backup_stage", progressVars);
        m.put("maintenance_optimize_stage", progressVars);
        m.put("maintenance_backup_done", Set.of("label", "duration_ms"));
        m.put("maintenance_backup_error", Set.of("label", "duration_ms"));
        m.put("maintenance_optimize_done", Set.of("label", "duration_ms"));
        m.put("maintenance_optimize_error", Set.of("label", "duration_ms"));
        m.put("server_maintenance_hint", Set.of("motd"));
        m.put("server_load", Set.of("message"));
        m.put("server_stop", Set.of("message"));
        m.put("whitelist_block", Set.of("message"));
        m.put("whitelist_toggle_alert", Set.of("message"));
        m.put("command_output", Set.of("message"));
        m.put("command_help", Set.of("help"));
        m.put("command_players", Set.of("header", "online_count", "max_count", "online_list"));
        m.put("command_whitelist_header", Set.of("count"));
        m.put("command_whitelist_page", Set.of("header", "page", "total", "body"));
        m.put("command_whitelist_cleanup", Set.of("removed_list"));
        m.put("command_whitelist_add_result", Set.of("message"));
        m.put("command_whitelist_remove_result", Set.of("message"));
        m.put("command_admin_required", Set.of("message"));
        m.put("command_usage", Set.of("message"));
        m.put("command_backup", Set.of("message"));
        m.put("command_optimize", Set.of("message"));
        m.put("command_optimize_disabled", Set.of("message"));
        return m;
    }
}
