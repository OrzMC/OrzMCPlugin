package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.List;
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

    /**
     * 启动健康校验：段缺失为建议（默认配置完整可用，升级安装才会缺此段）；
     * 限流/文案约束与 {@code from} 内 clamp 口径一致。
     */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            // 降级为建议：默认配置完整可用，升级安装（config.yml 存在故未复制新默认值）会缺此段，
            // 属提示而非缺陷，避免升级后每次启动的持久告警
            issues.add("建议: config.yml 缺失 chat 配置段，将使用默认配置（聊天反垃圾开启）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: chat.enabled 需为布尔值");
        int max = section.getInt("max_messages_per_minute", 20);
        if (max < 1) issues.add("非法: chat.max_messages_per_minute 不得小于 1");
        Object dl = section.get("detect_links");
        if (dl != null && !(dl instanceof Boolean)) issues.add("类型错误: chat.detect_links 需为布尔值");
        Object dr = section.get("detect_repeat");
        if (dr != null && !(dr instanceof Boolean)) issues.add("类型错误: chat.detect_repeat 需为布尔值");
        String msg = section.getString("message", "");
        if (msg.isBlank()) issues.add("缺失: chat.message 不可为空");
    }
}
