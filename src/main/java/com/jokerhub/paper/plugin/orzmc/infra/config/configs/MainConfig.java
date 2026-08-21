package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record MainConfig(
        boolean forceWhitelist,
        int whitelistCleanupInactiveDays,
        int whitelistPaginationDelayTicks,
        String cmdPromptChar,
        boolean optimizeEnabled,
        long optimizeTickTimeThreshold,
        int backupRetentionCount,
        String backupMaintenanceMotd,
        List<String> allowCountryCode,
        boolean entityTeleportEnabled,
        List<String> entityTeleportWhitelist,
        Map<String, CommandPolicy> commandPolicies) {

    /**
     * 实体传送白名单兜底：与 config.yml 保持一致。只含常见被动/友好实体
     * （村民/牲畜/友好水生/傀儡等），敌对生物不在内——防止 @e 选择器误用。
     * TAMEABLE 按接口判定，已覆盖猫/狗/鹦鹉 + 全部马科，无需重复列出。
     */
    public static final List<String> DEFAULT_ENTITY_TELEPORT_WHITELIST = List.of(
            "TAMEABLE",
            "ENDERMAN",
            "ARMOR_STAND",
            "SHULKER",
            "VILLAGER",
            "WANDERING_TRADER",
            "COW",
            "PIG",
            "SHEEP",
            "CHICKEN",
            "RABBIT",
            "GOAT",
            "MOOSHROOM",
            "AXOLOTL",
            "BEE",
            "IRON_GOLEM");

    public static MainConfig from(ConfigurationSection cfg) {
        boolean forceWhitelist = cfg.getBoolean("force_whitelist", true);
        int whitelistCleanupInactiveDays = cfg.getInt("whitelist_cleanup_inactive_days", 90);
        int whitelistPaginationDelayTicks = cfg.getInt("whitelist_pagination_delay_ticks", 5);
        String cmdPromptChar = cfg.getString("cmd_prompt_char", "$");
        boolean optimizeEnabled = cfg.getBoolean("optimize_enabled", false);
        long optimizeTickTimeThreshold = cfg.getLong("optimize_tick_time_threshold", 300L);
        int backupRetentionCount = cfg.getInt("backup_retention_count", 5);
        String backupMaintenanceMotd = cfg.getString("backup_maintenance_motd", "服务器维护中，稍后再试");
        List<String> allowCodes = new ArrayList<>();
        Object raw = cfg.get("allow_country_code");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) allowCodes.add(String.valueOf(o));
            }
        }
        boolean entityTeleportEnabled = cfg.getBoolean("entity_teleport_enabled", false);
        List<String> entityTeleportWhitelist = new ArrayList<>(cfg.getStringList("entity_teleport_whitelist"));
        if (entityTeleportWhitelist.isEmpty()) {
            entityTeleportWhitelist.addAll(DEFAULT_ENTITY_TELEPORT_WHITELIST);
        }
        Map<String, CommandPolicy> policies = new HashMap<>();
        Object rawCmds = cfg.get("commands");
        if (rawCmds instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s != null) {
                    int cooldown = s.getInt("cooldown_secs", 0);
                    boolean adminOnly = s.getBoolean("admin_only", false);
                    policies.put(key, new CommandPolicy(cooldown, adminOnly));
                }
            }
        }
        return new MainConfig(
                forceWhitelist,
                whitelistCleanupInactiveDays,
                whitelistPaginationDelayTicks,
                cmdPromptChar,
                optimizeEnabled,
                optimizeTickTimeThreshold,
                backupRetentionCount,
                backupMaintenanceMotd,
                allowCodes,
                entityTeleportEnabled,
                entityTeleportWhitelist,
                policies);
    }
}
