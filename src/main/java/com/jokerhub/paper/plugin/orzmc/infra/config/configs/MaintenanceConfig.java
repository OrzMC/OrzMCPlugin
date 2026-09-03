package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

public record MaintenanceConfig(
        boolean optimizeEnabled, long optimizeTickTimeThreshold, int backupRetentionCount, long backupIntervalHours) {

    /**
     * 维护场景文案/进度行已迁移到 templates.yml（{@code maintenance_motd_*} 4 键），
     * 不再从此处读取 motd（2026-09-02 PR4）。本配置仅保留维护开关/阈值/保留数/自动备份间隔。
     */
    public static MaintenanceConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new MaintenanceConfig(false, 300L, 5, 0L);
        }
        boolean optimizeEnabled = cfg.getBoolean("optimize_enabled", false);
        long optimizeTickTimeThreshold = cfg.getLong("optimize_tick_time_threshold", 300L);
        int backupRetentionCount = cfg.getInt("backup_retention_count", 5);
        long backupIntervalHours = cfg.getLong("backup_interval_hours", 0L);
        return new MaintenanceConfig(
                optimizeEnabled, optimizeTickTimeThreshold, backupRetentionCount, backupIntervalHours);
    }
}
