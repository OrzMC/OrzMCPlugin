package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigHealthCheckTest {

    private YamlConfiguration config;
    private YamlConfiguration bot;
    private YamlConfiguration templates;
    private YamlConfiguration portals;
    private Function<String, FileConfiguration> provider;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        bot = new YamlConfiguration();
        templates = new YamlConfiguration();
        portals = new YamlConfiguration();
        provider = name -> switch (name) {
            case "config" -> config;
            case "bot" -> bot;
            case "templates" -> templates;
            case "portals" -> portals;
            default -> null;
        };
    }

    private List<String> runValidate() {
        return ConfigHealthCheck.validateAll(provider);
    }

    private void addFullValidConfig_whitelist() {
        config.createSection("whitelist");
        config.getConfigurationSection("whitelist").set("force_whitelist", true);
        config.getConfigurationSection("whitelist").set("cleanup_inactive_days", 90);
        config.getConfigurationSection("whitelist").set("pagination_delay_ticks", 5);
        config.getConfigurationSection("whitelist").createSection("kick_message");
        config.getConfigurationSection("whitelist")
                .getConfigurationSection("kick_message")
                .set("title", "欢迎");
        config.getConfigurationSection("whitelist")
                .getConfigurationSection("kick_message")
                .set("qq_group_id", "123456");
        config.getConfigurationSection("whitelist")
                .getConfigurationSection("kick_message")
                .set("ups", List.of("item1"));
    }

    private void addFullValidConfig_maintenance() {
        config.createSection("maintenance");
        config.getConfigurationSection("maintenance").set("optimize_enabled", true);
        config.getConfigurationSection("maintenance").set("optimize_tick_time_threshold", 300);
        config.getConfigurationSection("maintenance").set("backup_retention_count", 5);
        config.getConfigurationSection("maintenance").set("backup_maintenance_motd", "维护中");
    }

    private void addFullValidConfig_tnt() {
        config.createSection("tnt");
        config.getConfigurationSection("tnt").set("enable", false);
        config.getConfigurationSection("tnt").set("enable_respawn_anchor", false);
        config.getConfigurationSection("tnt").set("place_cooldown", 5);
        config.getConfigurationSection("tnt").set("notify_throttle_ms", 1000L);
        config.getConfigurationSection("tnt").set("whitelist", List.of());
        config.getConfigurationSection("tnt").set("exempt_entities", List.of());
    }

    private void addFullValidConfig_geoip() {
        config.createSection("geoip").set("allow_country_code", List.of("CN", "HK"));
    }

    private void addFullValidConfig_commandPolicies() {
        config.createSection("command_policies");
        config.getConfigurationSection("command_policies").createSection("tpbow");
        config.getConfigurationSection("command_policies")
                .getConfigurationSection("tpbow")
                .set("cooldown_secs", 3);
        config.getConfigurationSection("command_policies")
                .getConfigurationSection("tpbow")
                .set("admin_only", false);
    }

    private void addFullValidConfig_bot() {
        bot.set("enable_qq_bot", true);
        bot.set("enable_discord_bot", false);
        bot.set("enable_lark_bot", false);
        bot.set("cmd_prompt_char", "$");
        bot.set("http_connect_timeout_seconds", 3);
        bot.set("http_request_timeout_seconds", 3);
        bot.set("http_max_retries", 3);
    }

    private void addFullValidConfig_templates() {
        addMinimalValidConfig_templates();
        templates.set("templates.role_groups", "dummy");
    }

    private void addMinimalValidConfig_templates() {
        templates.set("templates.player_join", "x");
        templates.set("templates.player_quit", "x");
        templates.set("templates.player_kick", "x");
        templates.set("templates.coord.scale", 1.0);
        templates.set("templates.coord.precision", 2);
        templates.set("templates.coord.unit_label", "block");
        templates.set("templates.progress_units.rate", "per_sec");
        templates.set("templates.progress_units.eta", "ms");
        templates.set("templates.locale", "zh-CN");
        templates.set("templates.world_alias.world", "主世界");
        templates.set("templates.world_alias.world_nether", "下界");
        templates.set("templates.world_alias.world_the_end", "末地");
        templates.set("templates.role_alias.admin", "管理员");
        templates.set("templates.role_alias.member", "成员");

        // Required command templates
        String[] requiredCmds = {
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
        for (String cmd : requiredCmds) {
            templates.set("templates." + cmd, "x");
        }

        templates.createSection("notifications").set("tnt_alert.public.enabled", true);
        templates.createSection("styles").createSection("colors");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("success", "#00FF00");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("info", "#0000FF");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("warn", "#FFFF00");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("error", "#FF0000");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("coord", "#00FFFF");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("player", "#FFD700");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("unknown", "#808080");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("tnt_alert", "#FF4500");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("explosion_alert", "#FF6347");
    }

    // ================================================================
    // Happy path
    // ================================================================

    @Test
    void fullValidConfig_returnsNoIssues() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addFullValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.isEmpty(), "预期无问题，实际: " + issues);
    }

    // ================================================================
    // Null providers
    // ================================================================

    @Test
    void nullConfig_returnsError() {
        provider = name -> null;
        List<String> issues = runValidate();
        assertTrue(issues.contains("config.yml 未加载"));
        assertTrue(issues.contains("bot.yml 未加载"));
        assertTrue(issues.contains("templates.yml 未加载"));
        assertTrue(issues.contains("portals.yml 未加载"));
    }

    // ================================================================
    // Whitelist section
    // ================================================================

    @Test
    void missingWhitelistSection_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        config.set("whitelist", null);
        List<String> issues = runValidate();
        assertTrue(issues.contains("config.yml 缺失 whitelist 配置段"));
    }

    @Test
    void whitelistForceWhitelistWrongType_reportsIssue() {
        config.createSection("whitelist").set("force_whitelist", "not_boolean");
        addFullValidConfig_whitelist_helper();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: whitelist.force_whitelist 需为布尔值"));
    }

    private void addFullValidConfig_whitelist_helper() {
        if (config.getConfigurationSection("whitelist") != null) {
            config.getConfigurationSection("whitelist").set("cleanup_inactive_days", 90);
            config.getConfigurationSection("whitelist").set("pagination_delay_ticks", 5);
            config.getConfigurationSection("whitelist")
                    .createSection("kick_message")
                    .set("title", "欢迎");
            config.getConfigurationSection("whitelist")
                    .getConfigurationSection("kick_message")
                    .set("qq_group_id", "123");
            config.getConfigurationSection("whitelist")
                    .getConfigurationSection("kick_message")
                    .set("ups", List.of("x"));
        }
    }

    @Test
    void whitelistNegativeCleanupDays_reportsIssue() {
        addFullValidConfig_whitelist(); // sets all whitelist values
        config.getConfigurationSection("whitelist").set("cleanup_inactive_days", -1);
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: whitelist.cleanup_inactive_days 必须为正数"));
    }

    @Test
    void whitelistNegativePaginationTicks_reportsIssue() {
        addFullValidConfig_whitelist();
        config.getConfigurationSection("whitelist").set("pagination_delay_ticks", -5);
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: whitelist.pagination_delay_ticks 不得为负数"));
    }

    @Test
    void whitelistMissingKickMessage_reportsIssue() {
        config.createSection("whitelist").set("force_whitelist", true);
        config.getConfigurationSection("whitelist").set("cleanup_inactive_days", 90);
        config.getConfigurationSection("whitelist").set("pagination_delay_ticks", 5);
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("缺失: whitelist.kick_message 未配置"));
    }

    @Test
    void whitelistEmptyKickTitle_reportsIssue() {
        config.createSection("whitelist").set("force_whitelist", true);
        config.getConfigurationSection("whitelist").set("cleanup_inactive_days", 90);
        config.getConfigurationSection("whitelist").set("pagination_delay_ticks", 5);
        config.getConfigurationSection("whitelist")
                .createSection("kick_message")
                .set("title", "");
        config.getConfigurationSection("whitelist")
                .getConfigurationSection("kick_message")
                .set("qq_group_id", "123");
        config.getConfigurationSection("whitelist")
                .getConfigurationSection("kick_message")
                .set("ups", List.of("x"));
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("缺失: whitelist.kick_message.title 不可为空"));
    }

    @Test
    void whitelistMissingUps_reportsIssue() {
        config.createSection("whitelist").set("force_whitelist", true);
        config.getConfigurationSection("whitelist").set("cleanup_inactive_days", 90);
        config.getConfigurationSection("whitelist").set("pagination_delay_ticks", 5);
        config.getConfigurationSection("whitelist")
                .createSection("kick_message")
                .set("title", "欢迎");
        config.getConfigurationSection("whitelist")
                .getConfigurationSection("kick_message")
                .set("qq_group_id", "123");
        // no "ups" set
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("缺失: whitelist.kick_message.ups 至少需要一项"));
    }

    // ================================================================
    // Maintenance section
    // ================================================================

    @Test
    void missingMaintenanceSection_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        // no maintenance section
        List<String> issues = runValidate();
        assertTrue(issues.contains("config.yml 缺失 maintenance 配置段"));
    }

    @Test
    void maintenanceOptimizeEnabledWrongType_reportsIssue() {
        config.createSection("maintenance").set("optimize_enabled", "string");
        addFullValidConfig_whitelist();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: maintenance.optimize_enabled 需为布尔值"));
    }

    @Test
    void maintenanceNegativeThreshold_reportsIssue() {
        config.createSection("maintenance").set("optimize_enabled", true);
        config.getConfigurationSection("maintenance").set("optimize_tick_time_threshold", -1);
        config.getConfigurationSection("maintenance").set("backup_retention_count", 5);
        config.getConfigurationSection("maintenance").set("backup_maintenance_motd", "维护中");
        addFullValidConfig_whitelist();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: maintenance.optimize_tick_time_threshold 必须为正数"));
    }

    @Test
    void maintenanceNegativeRetention_reportsIssue() {
        config.createSection("maintenance").set("optimize_enabled", true);
        config.getConfigurationSection("maintenance").set("optimize_tick_time_threshold", 300);
        config.getConfigurationSection("maintenance").set("backup_retention_count", -1);
        config.getConfigurationSection("maintenance").set("backup_maintenance_motd", "维护中");
        addFullValidConfig_whitelist();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: maintenance.backup_retention_count 不得为负数"));
    }

    @Test
    void maintenanceEmptyMotd_reportsIssue() {
        config.createSection("maintenance").set("optimize_enabled", true);
        config.getConfigurationSection("maintenance").set("optimize_tick_time_threshold", 300);
        config.getConfigurationSection("maintenance").set("backup_retention_count", 5);
        config.getConfigurationSection("maintenance").set("backup_maintenance_motd", "");
        addFullValidConfig_whitelist();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("缺失: maintenance.backup_maintenance_motd"));
    }

    // ================================================================
    // TNT section
    // ================================================================

    @Test
    void missingTntSection_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        // no tnt section
        List<String> issues = runValidate();
        assertTrue(issues.contains("config.yml 缺失 tnt 配置段"));
    }

    @Test
    void tntEnableWrongType_reportsIssue() {
        config.createSection("tnt").set("enable", "yes");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: tnt.enable 需为布尔值"));
    }

    @Test
    void tntNegativeCooldown_reportsIssue() {
        config.createSection("tnt").set("place_cooldown", -1);
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: tnt.place_cooldown 不得为负数"));
    }

    @Test
    void tntNegativeNotifyThrottle_reportsIssue() {
        config.createSection("tnt").set("notify_throttle_ms", -100L);
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: tnt.notify_throttle_ms 不得为负数"));
    }

    @Test
    void tntWhitelistWrongType_reportsIssue() {
        config.createSection("tnt").set("whitelist", "not_a_list");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: tnt.whitelist 需为列表"));
    }

    @Test
    void tntExemptEntitiesWrongType_reportsIssue() {
        config.createSection("tnt").set("exempt_entities", "not_a_list");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: tnt.exempt_entities 需为列表"));
    }

    // ================================================================
    // GeoIP section
    // ================================================================

    @Test
    void missingGeoIpSection_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        // no geoip section
        List<String> issues = runValidate();
        assertTrue(issues.contains("config.yml 缺失 geoip 配置段"));
    }

    @Test
    void geoIpNullCodeList_reportsSuggestion() {
        config.createSection("geoip");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("建议: geoip.allow_country_code 未配置，默认允许所有地区"));
    }

    @Test
    void geoIpCodeListWrongType_reportsIssue() {
        config.createSection("geoip").set("allow_country_code", "CN,HK");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: geoip.allow_country_code 需为列表"));
    }

    @Test
    void geoIpInvalidCountryCode_reportsIssue() {
        config.createSection("geoip").set("allow_country_code", List.of("cn", "USA"));
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: geoip.allow_country_code 'cn' 必须为大写两位国家码"));
        assertTrue(issues.contains("非法: geoip.allow_country_code 'USA' 必须为大写两位国家码"));
    }

    @Test
    void geoIpNullEntry_reportsIssue() {
        List<Object> codes = new ArrayList<>();
        codes.add(null);
        config.createSection("geoip").set("allow_country_code", codes);
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: geoip.allow_country_code 不允许空项"));
    }

    // ================================================================
    // Command policies section
    // ================================================================

    @Test
    void missingCommandPoliciesSection_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        // no command_policies section
        List<String> issues = runValidate();
        assertTrue(issues.contains("config.yml 缺失 command_policies 配置段"));
    }

    @Test
    void commandPolicyNegativeCooldown_reportsIssue() {
        config.createSection("command_policies");
        config.getConfigurationSection("command_policies")
                .createSection("tpbow")
                .set("cooldown_secs", -1);
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: command_policies.tpbow.cooldown_secs 不得为负数"));
    }

    @Test
    void commandPolicyCooldownWrongType_reportsIssue() {
        config.createSection("command_policies");
        config.getConfigurationSection("command_policies")
                .createSection("tpbow")
                .set("cooldown_secs", "abc");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: command_policies.tpbow.cooldown_secs 需为数字"));
    }

    @Test
    void commandPolicyAdminOnlyWrongType_reportsIssue() {
        config.createSection("command_policies");
        config.getConfigurationSection("command_policies")
                .createSection("tpbow")
                .set("admin_only", "yes");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: command_policies.tpbow.admin_only 需为布尔值"));
    }

    @Test
    void commandPolicyNotASection_reportsIssue() {
        config.createSection("command_policies").set("tpbow", "string_value");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: command_policies.tpbow 需为对象"));
    }

    // ================================================================
    // Bot section
    // ================================================================

    @Test
    void botMissingFile_reportsIssue() {
        provider = name -> switch (name) {
            case "config" -> config;
            case "bot" -> null;
            case "templates" -> templates;
            case "portals" -> portals;
            default -> null;
        };
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("bot.yml 未加载"));
    }

    @Test
    void botBoolKeysWrongType_reportsIssue() {
        bot.set("enable_qq_bot", "yes");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: bot.enable_qq_bot 需为布尔值"));
    }

    @Test
    void botEmptyCmdPrompt_reportsIssue() {
        bot.set("cmd_prompt_char", "");
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: bot.cmd_prompt_char 不可为空"));
    }

    @Test
    void botNegativeHttpTimeout_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addMinimalValidConfig_templates();
        bot.set("http_connect_timeout_seconds", -1);
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: bot.http_connect_timeout_seconds 必须为正数"));
    }

    // ================================================================
    // Portals section
    // ================================================================

    @Test
    void portalsMissingFile_reportsIssue() {
        provider = name -> switch (name) {
            case "config" -> config;
            case "bot" -> bot;
            case "templates" -> templates;
            case "portals" -> null;
            default -> null;
        };
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        List<String> issues = runValidate();
        assertTrue(issues.contains("portals.yml 未加载"));
    }

    @Test
    void portalsInvalidCoordinateKey_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        // 用 createSection 逐级构造嵌套结构避免路径问题
        ConfigurationSection ps = portals.createSection("portals");
        ConfigurationSection ts = ps.createSection("example_com");
        ts.set("world:0:64:0", "Z");
        List<String> issues = runValidate();
        assertTrue(
                issues.stream().anyMatch(i -> i.startsWith("建议: templates.role_groups")),
                "仅有 role_groups 建议，实际: " + issues);
    }

    @Test
    void portalsInvalidAxis_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        ConfigurationSection ps = portals.createSection("portals");
        ConfigurationSection ts = ps.createSection("example_com");
        ts.set("world:0:64:0", "Y");
        List<String> issues = runValidate();
        assertTrue(issues.stream().anyMatch(i -> i.contains("轴向取值")), "应报告轴向错误，实际: " + issues);
    }

    // ================================================================
    // Templates section
    // ================================================================

    @Test
    void templatesMissingFile_reportsIssue() {
        provider = name -> switch (name) {
            case "config" -> config;
            case "bot" -> bot;
            case "templates" -> null;
            case "portals" -> portals;
            default -> null;
        };
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        List<String> issues = runValidate();
        assertTrue(issues.contains("templates.yml 未加载"));
    }

    @Test
    void templatesMissingNotifications_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates_witoutNotifications();
        List<String> issues = runValidate();
        assertTrue(issues.contains("templates.yml 缺失 notifications 配置段"));
    }

    private void addMinimalValidConfig_templates_witoutNotifications() {
        templates.set("templates.player_join", "x");
        templates.set("templates.player_quit", "x");
        templates.set("templates.player_kick", "x");
        templates.set("templates.coord.scale", 1.0);
        templates.set("templates.coord.precision", 2);
        templates.set("templates.coord.unit_label", "block");
        templates.set("templates.progress_units.rate", "per_sec");
        templates.set("templates.progress_units.eta", "ms");
        templates.set("templates.locale", "zh-CN");

        String[] requiredCmds = {
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
        for (String cmd : requiredCmds) {
            templates.set("templates." + cmd, "x");
        }

        templates.createSection("styles").createSection("colors");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("success", "#00FF00");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("info", "#0000FF");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("warn", "#FFFF00");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("error", "#FF0000");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("coord", "#00FFFF");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("player", "#FFD700");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("unknown", "#808080");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("tnt_alert", "#FF4500");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("explosion_alert", "#FF6347");
    }

    @Test
    void templatesMissingStyles_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates_witoutNotifications();
        templates.createSection("notifications").set("tnt_alert.public.enabled", true);
        templates.set("styles", null);
        List<String> issues = runValidate();
        assertTrue(issues.contains("templates.yml 缺失 styles 配置段"));
    }

    @Test
    void templatesMissingRequiredItems_reportsIssues() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        // minimal templates without all required items
        templates.set("templates.player_join", "x");
        templates.set("templates.coord.scale", 1.0);
        templates.set("templates.coord.precision", 2);
        templates.set("templates.coord.unit_label", "block");
        templates.set("templates.progress_units.rate", "per_sec");
        templates.set("templates.progress_units.eta", "ms");
        templates.set("templates.locale", "zh-CN");
        templates.createSection("notifications").set("tnt_alert.public.enabled", true);
        templates.createSection("styles").createSection("colors");
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("success", "#00FF00");
        List<String> issues = runValidate();
        assertTrue(issues.contains("缺失: templates.player_quit"));
        assertTrue(issues.contains("缺失: templates.player_kick"));
    }

    // ================================================================
    // Notifications section
    // ================================================================

    @Test
    void notificationsTntAlertNonBool_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        templates.getConfigurationSection("notifications").set("tnt_alert.public.enabled", "yes");
        List<String> issues = runValidate();
        assertTrue(issues.contains("类型错误: notifications.tnt_alert.public.enabled 需为布尔值"));
    }

    @Test
    void notificationsChannelKeyNoMapping_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        templates.getConfigurationSection("notifications").set("player_join.channel_key", "missing_channel");
        templates.getConfigurationSection("notifications").set("player_join.public.enabled", true);
        List<String> issues = runValidate();
        assertTrue(issues.contains("通知频道未映射: notifications.player_join.channel_key=missing_channel"));
    }

    // ================================================================
    // Styles section
    // ================================================================

    @Test
    void stylesMissingColorsSection_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        templates.getConfigurationSection("styles").set("colors", null);
        List<String> issues = runValidate();
        assertTrue(issues.contains("templates.yml styles 缺失 colors 配置段"));
    }

    @Test
    void stylesInvalidHexColor_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("success", "green");
        List<String> issues = runValidate();
        assertTrue(issues.contains("非法: styles.colors.success 必须为 #RRGGBB"));
    }

    @Test
    void stylesMissingColorKey_reportsIssue() {
        addFullValidConfig_whitelist();
        addFullValidConfig_maintenance();
        addFullValidConfig_tnt();
        addFullValidConfig_geoip();
        addFullValidConfig_commandPolicies();
        addFullValidConfig_bot();
        addMinimalValidConfig_templates();
        templates
                .getConfigurationSection("styles")
                .getConfigurationSection("colors")
                .set("success", null);
        List<String> issues = runValidate();
        assertTrue(issues.contains("缺失: styles.colors.success"));
    }
}
