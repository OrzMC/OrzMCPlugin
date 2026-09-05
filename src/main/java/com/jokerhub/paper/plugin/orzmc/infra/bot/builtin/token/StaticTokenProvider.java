package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token;

/**
 * 长期令牌提供者（Discord / Telegram bot token，无过期刷新）。
 *
 * <p>{@code current()}/{@code fresh()} 恒返回配置令牌；{@code onAuthFailure()} 返回 null——
 * 表示 401 属于配置错误、插件无法自愈（骨架层据此停用该平台并告警，不无限重试）。</p>
 */
public final class StaticTokenProvider implements TokenProvider {

    private final String token;

    public StaticTokenProvider(String token) {
        this.token = token;
    }

    @Override
    public String current() {
        return token;
    }

    @Override
    public String fresh() {
        return token;
    }

    @Override
    public String onAuthFailure() {
        return null;
    }
}
