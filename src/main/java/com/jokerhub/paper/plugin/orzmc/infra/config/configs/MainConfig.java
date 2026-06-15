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
        Map<String, CommandPolicy> commandPolicies) {

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
                policies);
    }
}
