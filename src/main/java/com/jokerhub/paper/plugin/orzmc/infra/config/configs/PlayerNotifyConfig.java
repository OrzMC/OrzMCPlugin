package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 玩家上下线通知配置。
 *
 * <p>上下线/被踢消息的窗口聚合限流参数：窗口内多条事件合并为一条聚合摘要，
 * 单条事件仍按原模板单发（延迟一个窗口，保证最多 1 条/窗口）。
 * 各类型开关为显式配置项：关闭后该类型事件不再产生通知（配置性忽略，非运行时丢消息）。</p>
 */
public record PlayerNotifyConfig(
        boolean enabledJoin, boolean enabledQuit, boolean enabledKick, long windowMs, int maxListItems) {

    public static PlayerNotifyConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new PlayerNotifyConfig(true, true, true, 1000L, 6);
        }
        long windowMs = cfg.getLong("window_ms", 1000L);
        if (windowMs <= 0) {
            // 非正窗口会让聚合退化为 1 tick，等于关闭防刷屏 → 回退默认
            windowMs = 1000L;
        }
        int maxListItems = cfg.getInt("max_list_items", 6);
        if (maxListItems < 1) {
            maxListItems = 1;
        }
        return new PlayerNotifyConfig(
                cfg.getBoolean("enabled_join", true),
                cfg.getBoolean("enabled_quit", true),
                cfg.getBoolean("enabled_kick", true),
                windowMs,
                maxListItems);
    }
}
