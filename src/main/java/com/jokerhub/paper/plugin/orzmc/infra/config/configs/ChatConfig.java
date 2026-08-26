package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 聊天反垃圾/反广告（安全加固 P2-1）配置。
 *
 * <p>对应 config.yml 的 {@code chat:} 段，供 {@code ChatSpamFilterService} 使用：
 * 配置是否启用、每分钟限流条数、是否检测链接/重复文本，以及命中时的提示文案。</p>
 */
public record ChatConfig(
        boolean enabled, int maxMessagesPerMinute, boolean detectLinks, boolean detectRepeat, String message) {

    /** 默认命中提示文案。 */
    public static final String DEFAULT_MESSAGE = "请勿刷屏或发送广告";

    public static ChatConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new ChatConfig(true, 20, true, true, DEFAULT_MESSAGE);
        }
        int max = cfg.getInt("max_messages_per_minute", 20);
        if (max < 1) {
            // 非正上限会让限流失效（同 1 tick），回退最小 1
            max = 1;
        }
        String msg = cfg.getString("message", DEFAULT_MESSAGE);
        if (msg == null || msg.isBlank()) {
            msg = DEFAULT_MESSAGE;
        }
        return new ChatConfig(
                cfg.getBoolean("enabled", true),
                max,
                cfg.getBoolean("detect_links", true),
                cfg.getBoolean("detect_repeat", true),
                msg);
    }
}
