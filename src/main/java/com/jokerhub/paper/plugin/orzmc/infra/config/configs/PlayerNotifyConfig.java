package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.List;
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

    /**
     * 启动健康校验：段缺失为建议（默认配置完整可用，升级安装才会缺此段）；
     * 窗口/上限约束与 {@code from} 内 clamp 口径一致。
     */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            // 降级为建议：默认配置完整可用，仅升级安装（config.yml 存在故未复制新默认值）会缺此段，
            // 属提示而非缺陷，避免升级后每次启动的持久告警
            issues.add("建议: config.yml 缺失 player_notify 配置段，将使用默认配置（窗口 1000ms，三类通知启用）");
            return;
        }
        for (String key : new String[] {"enabled_join", "enabled_quit", "enabled_kick"}) {
            Object en = section.get(key);
            if (en != null && !(en instanceof Boolean)) issues.add("类型错误: player_notify." + key + " 需为布尔值");
        }
        long window = section.getLong("window_ms", 1000L);
        if (window <= 0) issues.add("非法: player_notify.window_ms 必须为正数（≤0 会回退默认 1000ms，静默关闭防刷屏）");
        int maxList = section.getInt("max_list_items", 6);
        if (maxList < 1) issues.add("非法: player_notify.max_list_items 不得小于 1");
    }
}
