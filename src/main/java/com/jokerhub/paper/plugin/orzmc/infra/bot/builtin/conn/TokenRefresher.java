package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

/**
 * 鉴权失败时的令牌刷新策略（builtin IM 骨架，方案 §4.2）。
 *
 * <p>由各平台 adapter 注入：QQ/飞书等短期令牌绑定 {@code () -> tokenProvider.onAuthFailure() != null}，
 * Discord/Telegram 等长期令牌直接返回 {@link RefreshOutcome#DEAD}（401 = 配置错误不可自愈，立即告警停用）。</p>
 */
@FunctionalInterface
public interface TokenRefresher {

    /**
     * 尝试刷新令牌。
     *
     * @return 刷新结果：{@link RefreshOutcome#REFRESHED}（已拿到新令牌，立即重连）/
     *     {@link RefreshOutcome#RETRY_LATER}（暂不可用，按退避稍后重试）/
     *     {@link RefreshOutcome#DEAD}（不可恢复，视为 fatal）
     */
    RefreshOutcome refresh();

    enum RefreshOutcome {
        REFRESHED,
        RETRY_LATER,
        DEAD
    }
}
