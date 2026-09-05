package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 进服限流/反 bot（安全加固 P2-2）配置。
 *
 * <p>对应 config.yml 的 {@code login_rate_limit:} 段，供 {@code LoginRateLimitService} 使用：
 * 配置是否启用、每分钟登录尝试上限（频率）、同 IP 并发在线上限（并发），以及命中时
 * 是否 PRIVATE 私信管理员与玩家提示文案。</p>
 */
public record LoginRateLimitConfig(
        boolean enabled, int maxLoginAttemptsPerMinute, int maxConcurrentPerIp, boolean notifyAdmins, String message) {

    /** 默认命中提示文案。 */
    public static final String DEFAULT_MESSAGE = "登录过于频繁，请稍后再试";

    public static LoginRateLimitConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new LoginRateLimitConfig(true, 20, 5, true, DEFAULT_MESSAGE);
        }
        int freq = cfg.getInt("max_login_attempts_per_minute", 20);
        if (freq < 1) {
            // 非正上限会让限流失效，回退最小 1
            freq = 1;
        }
        int concurrent = cfg.getInt("max_concurrent_per_ip", 5);
        if (concurrent < 1) {
            concurrent = 1;
        }
        String msg = cfg.getString("message", DEFAULT_MESSAGE);
        if (msg == null || msg.isBlank()) {
            msg = DEFAULT_MESSAGE;
        }
        return new LoginRateLimitConfig(
                cfg.getBoolean("enabled", true), freq, concurrent, cfg.getBoolean("notify_admins", true), msg);
    }

    /**
     * 启动健康校验：段缺失为建议（默认配置完整可用，升级安装才会缺此段）；
     * 限流/文案约束与 {@code from} 内 clamp 口径一致。
     */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            // 降级为建议：默认配置完整可用，升级安装（config.yml 存在故未复制新默认值）会缺此段
            issues.add("建议: config.yml 缺失 login_rate_limit 配置段，将使用默认配置（进服限流开启）");
            return;
        }
        Object en = section.get("enabled");
        if (en != null && !(en instanceof Boolean)) issues.add("类型错误: login_rate_limit.enabled 需为布尔值");
        int freq = section.getInt("max_login_attempts_per_minute", 20);
        if (freq < 1) issues.add("非法: login_rate_limit.max_login_attempts_per_minute 不得小于 1");
        int conc = section.getInt("max_concurrent_per_ip", 5);
        if (conc < 1) issues.add("非法: login_rate_limit.max_concurrent_per_ip 不得小于 1");
        Object na = section.get("notify_admins");
        if (na != null && !(na instanceof Boolean)) issues.add("类型错误: login_rate_limit.notify_admins 需为布尔值");
        String msg = section.getString("message", "");
        if (msg.isBlank()) issues.add("缺失: login_rate_limit.message 不可为空");
    }
}
