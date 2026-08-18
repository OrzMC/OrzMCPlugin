package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

public record MaintenanceConfig(
        boolean optimizeEnabled,
        long optimizeTickTimeThreshold,
        int backupRetentionCount,
        String backupMaintenanceMotd,
        long backupIntervalHours) {

    public static MaintenanceConfig from(ConfigurationSection cfg) {
        if (cfg == null) return new MaintenanceConfig(false, 300L, 5, "服务器维护中，稍后再试", 0L);
        boolean optimizeEnabled = cfg.getBoolean("optimize_enabled", false);
        long optimizeTickTimeThreshold = cfg.getLong("optimize_tick_time_threshold", 300L);
        int backupRetentionCount = cfg.getInt("backup_retention_count", 5);
        String backupMaintenanceMotd = cfg.getString("backup_maintenance_motd", "服务器维护中，稍后再试");
        long backupIntervalHours = cfg.getLong("backup_interval_hours", 0L);
        return new MaintenanceConfig(
                optimizeEnabled,
                optimizeTickTimeThreshold,
                backupRetentionCount,
                backupMaintenanceMotd,
                backupIntervalHours);
    }
}
