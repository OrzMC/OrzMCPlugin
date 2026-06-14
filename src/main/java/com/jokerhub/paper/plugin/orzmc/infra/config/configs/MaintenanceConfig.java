package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

public record MaintenanceConfig(
        boolean optimizeEnabled,
        long optimizeTickTimeThreshold,
        int backupRetentionCount,
        String backupMaintenanceMotd) {

    public static MaintenanceConfig from(ConfigurationSection cfg) {
        if (cfg == null) return new MaintenanceConfig(false, 300L, 5, "服务器维护中，稍后再试");
        boolean optimizeEnabled = cfg.getBoolean("optimize_enabled", false);
        long optimizeTickTimeThreshold = cfg.getLong("optimize_tick_time_threshold", 300L);
        int backupRetentionCount = cfg.getInt("backup_retention_count", 5);
        String backupMaintenanceMotd = cfg.getString("backup_maintenance_motd", "服务器维护中，稍后再试");
        return new MaintenanceConfig(
                optimizeEnabled, optimizeTickTimeThreshold, backupRetentionCount, backupMaintenanceMotd);
    }
}
