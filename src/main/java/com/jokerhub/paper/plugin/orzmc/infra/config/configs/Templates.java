package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

public record Templates(
        String playerJoin,
        String playerQuit,
        String playerKick,
        String playerDigest,
        String exceptionAlert,
        String geoipBlock,
        String geoipUnverifiable,
        String tntAlert,
        String maintenanceBackupStage,
        String maintenanceBackupDone,
        String maintenanceBackupError,
        String maintenanceOptimizeStage,
        String maintenanceOptimizeDone,
        String maintenanceOptimizeError,
        String serverLoad,
        String serverStop,
        String whitelistBlock,
        String whitelistToggleAlert,
        String maintenanceMotdBackup,
        String maintenanceMotdOptimize,
        String maintenanceMotdManual,
        String maintenanceMotdProgressLine) {

    /** 维护场景文案默认值（templates.yml {@code maintenance_motd_*}）：统一渲染入口后 MOTD/登录拦截/踢人共文案，
     *  默认带场景词，保证备份/优化/手动仍可区分（2026-09-02 review）。 */
    public static final String DEFAULT_MAINTENANCE_MOTD_BACKUP = "服务器地图备份中，请稍后再试";

    public static final String DEFAULT_MAINTENANCE_MOTD_OPTIMIZE = "服务器地图优化中，请稍后再试";
    public static final String DEFAULT_MAINTENANCE_MOTD_MANUAL = "服务器维护中，请稍后再试";

    public static Templates from(ConfigurationSection cfg) {
        String base = "templates";
        String join = cfg.getString(
                base + ".player_join",
                "🎮 当前玩家({online_count}/{max_count})\n---------------------------------\n🥰 上线：\n{name}");
        String quit = cfg.getString(
                base + ".player_quit",
                "🎮 当前玩家({online_count}/{max_count})\n---------------------------------\n😋 下线：\n{name}");
        String kick = cfg.getString(
                base + ".player_kick",
                "🎮 当前玩家({online_count}/{max_count})\n---------------------------------\n😂 被踢：\n{name}");
        String digest = cfg.getString(
                base + ".player_digest",
                "🎮 当前玩家({online_count}/{max_count})\n{join_summary}{quit_summary}{kick_summary}");
        String exceptionAlert =
                cfg.getString(base + ".exception_alert", "⚠️ 服务器异常\n---------------------------------\n{message}");
        String geoipBlock = cfg.getString(
                base + ".geoip_block",
                "{name}({ip}) 地区:{country_code} 不在允许列表({allow_list})\n{address_info}\n你的地区({country_code})不在允许列表，如有疑问请联系管理员");
        String geoipUnverifiable = cfg.getString(
                base + ".geoip_unverifiable", "{name} 地区解析服务暂时不可用，无法验证你的地区（IP:{ip}）。请稍后重新尝试登录，若持续出现请联系管理员");
        String tntAlert = cfg.getString(
                base + ".tnt_alert",
                "{msg}\n世界:{world_alias} 坐标:{x_unit},{y_unit},{z_unit}({coord_unit})\n触发:{actor} 方块:{block_type}");
        String mbStage = cfg.getString(
                base + ".maintenance_backup_stage",
                "地图{label} 阶段:{stage}({stage_name}/{stage_i18n}) 进度:{percent}% {current}/{total} 速率:{rate_per}{rate_unit} 预计剩余:{eta_value}{eta_unit}");
        String mbDone = cfg.getString(base + ".maintenance_backup_done", "地图{label} 完成 用时:{duration_human}");
        String mbErr = cfg.getString(base + ".maintenance_backup_error", "地图{label} 失败 用时:{duration_human}");
        String moStage = cfg.getString(
                base + ".maintenance_optimize_stage",
                "地图{label} 阶段:{stage}({stage_name}/{stage_i18n}) 进度:{percent}% {current}/{total} 速率:{rate_per}{rate_unit} 预计剩余:{eta_value}{eta_unit}");
        String moDone = cfg.getString(base + ".maintenance_optimize_done", "地图{label} 完成 用时:{duration_human}");
        String moErr = cfg.getString(base + ".maintenance_optimize_error", "地图{label} 失败 用时:{duration_human}");
        String serverLoad = cfg.getString(base + ".server_load", "{message}");
        String serverStop = cfg.getString(base + ".server_stop", "{message}");
        String whitelistBlock = cfg.getString(base + ".whitelist_block", "🙅🏻‍♂️ {message}");
        String whitelistToggleAlert = cfg.getString(
                base + ".whitelist_toggle_alert", "⚠️ 服务器异常\n---------------------------------\n{message}");
        // 维护场景文案 + 进度行（MOTD / 登录拦截 / 踢人统一渲染入口读取，2026-09-02 迁移自 config.yml maintenance 段）
        String maintenanceMotdBackup =
                cfg.getString(base + ".maintenance_motd_backup", DEFAULT_MAINTENANCE_MOTD_BACKUP);
        String maintenanceMotdOptimize =
                cfg.getString(base + ".maintenance_motd_optimize", DEFAULT_MAINTENANCE_MOTD_OPTIMIZE);
        String maintenanceMotdManual =
                cfg.getString(base + ".maintenance_motd_manual", DEFAULT_MAINTENANCE_MOTD_MANUAL);
        String maintenanceMotdProgressLine =
                cfg.getString(base + ".maintenance_motd_progress_line", "进度：{stage} {percent}% 预计剩余 {eta}秒");
        return new Templates(
                join,
                quit,
                kick,
                digest,
                exceptionAlert,
                geoipBlock,
                geoipUnverifiable,
                tntAlert,
                mbStage,
                mbDone,
                mbErr,
                moStage,
                moDone,
                moErr,
                serverLoad,
                serverStop,
                whitelistBlock,
                whitelistToggleAlert,
                maintenanceMotdBackup,
                maintenanceMotdOptimize,
                maintenanceMotdManual,
                maintenanceMotdProgressLine);
    }
}
