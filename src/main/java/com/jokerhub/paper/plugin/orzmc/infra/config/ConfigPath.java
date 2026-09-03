package com.jokerhub.paper.plugin.orzmc.infra.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata registry for runtime-configurable config paths.
 * Each entry links a YAML path to its type, default value, description, and source config file.
 *
 * <p>Registered paths are writable at runtime via {@code /orzmc config set <path> <value>}.
 * Complex types (List, Map, nested Section) are excluded — they require manual YAML editing.
 */
public final class ConfigPath {
    private final String configName;
    private final String path;
    private final Class<?> type;
    private final Object defaultValue;
    private final String description;

    private ConfigPath(String configName, String path, Class<?> type, Object defaultValue, String description) {
        this.configName = Objects.requireNonNull(configName);
        this.path = Objects.requireNonNull(path);
        this.type = Objects.requireNonNull(type);
        this.defaultValue = defaultValue;
        this.description = Objects.requireNonNull(description);
    }

    public String configName() {
        return configName;
    }

    public String path() {
        return path;
    }

    public Class<?> type() {
        return type;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }

    /** All registered config paths, ordered by config file group. */
    public static Map<String, ConfigPath> all() {
        Map<String, ConfigPath> map = new LinkedHashMap<>();
        // whitelist (config.yml)
        reg(map, "config", "whitelist.force_whitelist", Boolean.class, true, "启用强制白名单");
        reg(map, "config", "whitelist.cleanup_inactive_days", Integer.class, 90, "白名单不活跃清理天数");
        reg(map, "config", "whitelist.pagination_delay_ticks", Integer.class, 5, "白名单翻页延迟(tick)");
        // maintenance (config.yml)
        reg(map, "config", "maintenance.optimize_enabled", Boolean.class, false, "启用地图自动优化");
        reg(map, "config", "maintenance.optimize_tick_time_threshold", Long.class, 300L, "优化触发tick阈值(ms)");
        reg(map, "config", "maintenance.backup_retention_count", Integer.class, 5, "地图备份保留数量");
        reg(map, "config", "maintenance.backup_interval_hours", Long.class, 0L, "定时自动备份间隔(小时)，0=关闭");
        // 维护场景文案/进度行已迁 templates.yml（maintenance_motd_*），不再注册 config 键（2026-09-02 PR4）
        // tnt (config.yml)
        reg(map, "config", "tnt.enable", Boolean.class, false, "启用TNT放置检测");
        reg(map, "config", "tnt.enable_respawn_anchor", Boolean.class, false, "启用重生锚检测");
        reg(map, "config", "tnt.place_cooldown", Integer.class, 5, "TNT放置冷却(秒)");
        reg(map, "config", "tnt.notify_aggregate_ms", Long.class, 3000L, "TNT/爆炸告警聚合窗口(毫秒)");
        // player_notify (config.yml)
        reg(map, "config", "player_notify.enabled_join", Boolean.class, true, "上线消息通知开关");
        reg(map, "config", "player_notify.enabled_quit", Boolean.class, true, "下线消息通知开关");
        reg(map, "config", "player_notify.enabled_kick", Boolean.class, true, "被踢消息通知开关");
        reg(map, "config", "player_notify.window_ms", Long.class, 1000L, "上下线通知聚合窗口(毫秒)");
        reg(map, "config", "player_notify.max_list_items", Integer.class, 6, "聚合摘要最多列出的玩家数");

        // command policies (config.yml)
        reg(map, "config", "command_policies.tpbow.cooldown_secs", Integer.class, 3, "传送弓冷却(秒)");
        reg(map, "config", "command_policies.tpbow.admin_only", Boolean.class, false, "传送弓仅管理员可用");
        reg(map, "config", "command_policies.menu.cooldown_secs", Integer.class, 0, "菜单冷却(秒)");
        reg(map, "config", "command_policies.menu.admin_only", Boolean.class, false, "菜单仅管理员可用");
        reg(map, "config", "command_policies.portal.cooldown_secs", Integer.class, 5, "传送门冷却(秒)");
        reg(map, "config", "command_policies.portal.admin_only", Boolean.class, true, "传送门仅管理员可用");
        // bot settings (easybot.yml)
        reg(map, "easybot", "cmd_prompt_char", String.class, "$", "Bot命令前缀符");
        reg(map, "easybot", "discord_server_link", String.class, null, "Discord邀请链接");
        reg(map, "easybot", "qq_group_id", String.class, null, "QQ群号");
        // templates (templates.yml)
        reg(map, "templates", "templates.coord.scale", Double.class, 1.0, "坐标缩放比例");
        reg(map, "templates", "templates.coord.precision", Integer.class, 2, "坐标小数位数");
        reg(map, "templates", "templates.coord.unit_label", String.class, "block", "坐标单位标签");
        // rank_colors (config.yml)
        reg(map, "config", "rank_colors.enabled", Boolean.class, true, "玩家名颜色总开关");
        reg(map, "config", "rank_colors.nametag_enabled", Boolean.class, true, "头顶名牌着色开关");
        reg(map, "config", "rank_colors.tab_enabled", Boolean.class, false, "Tab列表着色开关");
        reg(map, "config", "rank_colors.op_color", String.class, "gold", "OP 玩家名颜色(命名色)");
        reg(map, "config", "rank_colors.colors.admin", String.class, "red", "管理员名颜色(命名色)");
        reg(map, "config", "rank_colors.colors.builder", String.class, "green", "建造者名颜色(命名色)");
        reg(map, "config", "rank_colors.colors.member", String.class, "aqua", "成员名颜色(命名色)");
        reg(map, "config", "rank_colors.colors.default", String.class, "gray", "访客名颜色(命名色)");
        // gamemode correction (config.yml)
        reg(map, "config", "gamemode-correction.enabled", Boolean.class, true, "游戏模式矫正开关");
        reg(map, "config", "gamemode-correction.debounce-ms", Long.class, 2000L, "矫正防抖窗口(毫秒)");
        reg(
                map,
                "config",
                "gamemode-correction.teleport-to-spawn-on-spectator-fix",
                Boolean.class,
                true,
                "观察模式矫正前回出生点");
        // prison (config.yml)
        reg(map, "config", "prison.cell_location", String.class, "world,0,100,0,0,0", "牢房坐标(world,x,y,z,yaw,pitch)");
        // chat (config.yml) —— 服务以 Supplier 实时读配置，注册安全（2026-09-02 补齐）
        reg(map, "config", "chat.enabled", Boolean.class, true, "聊天反垃圾/反广告总开关");
        reg(map, "config", "chat.max_messages_per_minute", Integer.class, 20, "每分钟最多发言条数");
        reg(map, "config", "chat.detect_links", Boolean.class, true, "丢弃含链接消息(反广告)");
        reg(map, "config", "chat.detect_repeat", Boolean.class, true, "丢弃重复消息(反刷屏)");
        reg(map, "config", "chat.message", String.class, "请勿刷屏或发送广告", "命中时提示文案");
        // guard (config.yml)
        reg(map, "config", "guard.enabled", Boolean.class, true, "危险命令拦截/审计总开关");
        reg(map, "config", "guard.notify_admins", Boolean.class, true, "拦截时是否私信管理员");
        reg(map, "config", "guard.audit_enabled", Boolean.class, true, "命令审计日志开关");
        // login_rate_limit (config.yml)
        reg(map, "config", "login_rate_limit.enabled", Boolean.class, true, "进服限流/反bot总开关");
        reg(map, "config", "login_rate_limit.max_login_attempts_per_minute", Integer.class, 20, "每分钟最大登录尝试次数");
        reg(map, "config", "login_rate_limit.max_concurrent_per_ip", Integer.class, 5, "同IP最大在线上限");
        reg(map, "config", "login_rate_limit.notify_admins", Boolean.class, true, "命中时是否私信管理员");
        reg(map, "config", "login_rate_limit.message", String.class, "登录过于频繁，请稍后再试", "命中时提示文案");
        // exploit_hardening (config.yml)
        reg(map, "config", "exploit_hardening.enabled", Boolean.class, true, "漏洞加固总开关");
        reg(map, "config", "exploit_hardening.book_enabled", Boolean.class, true, "书与笔页数上限开关");
        reg(map, "config", "exploit_hardening.book_max_pages", Integer.class, 100, "每本书最多页数");
        reg(map, "config", "exploit_hardening.item_enabled", Boolean.class, true, "物品属性修饰符数量上限开关");
        reg(map, "config", "exploit_hardening.item_max_attribute_modifiers", Integer.class, 6, "物品最大属性修饰符数量");
        reg(map, "config", "exploit_hardening.entity_enabled", Boolean.class, true, "单区块实体数上限开关");
        reg(map, "config", "exploit_hardening.entity_max_per_chunk", Integer.class, 128, "单区块最大实体数");
        reg(map, "config", "exploit_hardening.notify_admins", Boolean.class, true, "命中时是否私信管理员");
        reg(map, "config", "exploit_hardening.message", String.class, "检测到异常内容，已自动处理", "命中时提示文案");
        // geoip (config.yml)
        reg(map, "config", "geoip.fail_open", Boolean.class, false, "GeoIP查询失败放行(fail-open)");
        // 根级键（config.yml）
        reg(map, "config", "entity_teleport_enabled", Boolean.class, false, "实体传送总开关");
        // update 自更新 (config.yml)
        reg(map, "config", "update.enabled", Boolean.class, true, "自更新总开关");
        reg(map, "config", "update.channel", String.class, "release", "更新通道(release/beta)");
        reg(map, "config", "update.check_interval_hours", Long.class, 12L, "自动检查间隔(小时)，0=仅启动检查一次");
        reg(map, "config", "update.auto_download", Boolean.class, false, "发现新版自动下载到plugins/update");
        return Collections.unmodifiableMap(map);
    }

    private static void reg(
            Map<String, ConfigPath> map,
            String configName,
            String path,
            Class<?> type,
            Object defaultValue,
            String description) {
        map.put(path, new ConfigPath(configName, path, type, defaultValue, description));
    }
}
