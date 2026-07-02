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
        reg(map, "config", "maintenance.backup_maintenance_motd", String.class, "服务器维护中，稍后再试", "维护MOTD提示");
        // tnt (config.yml)
        reg(map, "config", "tnt.enable", Boolean.class, false, "启用TNT放置检测");
        reg(map, "config", "tnt.enable_respawn_anchor", Boolean.class, false, "启用重生锚检测");
        reg(map, "config", "tnt.place_cooldown", Integer.class, 5, "TNT放置冷却(秒)");
        reg(map, "config", "tnt.notify_throttle_ms", Long.class, 1000L, "TNT通知限流(毫秒)");
        // command policies (config.yml)
        reg(map, "config", "command_policies.tpbow.cooldown_secs", Integer.class, 3, "传送弓冷却(秒)");
        reg(map, "config", "command_policies.tpbow.admin_only", Boolean.class, false, "传送弓仅管理员可用");
        reg(map, "config", "command_policies.menu.cooldown_secs", Integer.class, 0, "菜单冷却(秒)");
        reg(map, "config", "command_policies.menu.admin_only", Boolean.class, false, "菜单仅管理员可用");
        reg(map, "config", "command_policies.portal.cooldown_secs", Integer.class, 5, "传送门冷却(秒)");
        reg(map, "config", "command_policies.portal.admin_only", Boolean.class, true, "传送门仅管理员可用");
        // bot (bot.yml)
        reg(map, "bot", "cmd_prompt_char", String.class, "$", "Bot命令前缀符");
        reg(map, "bot", "discord_server_link", String.class, null, "Discord邀请链接");
        reg(map, "bot", "qq_group_id", String.class, null, "QQ群号");
        // templates (templates.yml)
        reg(map, "templates", "templates.locale", String.class, "zh-CN", "本地化语言");
        reg(map, "templates", "templates.coord.scale", Double.class, 1.0, "坐标缩放比例");
        reg(map, "templates", "templates.coord.precision", Integer.class, 2, "坐标小数位数");
        reg(map, "templates", "templates.coord.unit_label", String.class, "block", "坐标单位标签");
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
