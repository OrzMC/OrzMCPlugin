package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

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
            return new LoginRateLimitConfig(true, 5, 3, true, DEFAULT_MESSAGE);
        }
        int freq = cfg.getInt("max_login_attempts_per_minute", 5);
        if (freq < 1) {
            // 非正上限会让限流失效，回退最小 1
            freq = 1;
        }
        int concurrent = cfg.getInt("max_concurrent_per_ip", 3);
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
}
