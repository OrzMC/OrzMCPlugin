package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token;

/**
 * 平台访问令牌提供者（builtin IM，方案 §4.2）。
 *
 * <p>区分两类平台令牌语义：</p>
 * <ul>
 *   <li><b>短期令牌</b>（QQ access_token / 飞书 tenant_access_token，约 2h）：需到期前预刷新、
 *       鉴权错误时强制重换——{@link RefreshableTokenProvider}；</li>
 *   <li><b>长期令牌</b>（Discord / Telegram bot token）：无过期刷新概念，401 = 配置错误不可自愈——
 *       {@link StaticTokenProvider}。</li>
 * </ul>
 *
 * <p>所有实现须线程安全；刷新单飞（并发下只发一次换取请求）。</p>
 */
public interface TokenProvider {

    /** 当前缓存的令牌（可能已过期/为空），不触发任何网络操作。 */
    String current();

    /**
     * 获取一个可用于请求的令牌：缓存为空或临期/过期时触发刷新（失败保留旧令牌，旧令牌也失效时返回 null）。
     *
     * @return 可用令牌；获取失败且无可用旧令牌时返回 null（调用方告警/降级）
     */
    String fresh();

    /**
     * 鉴权失败回调（HTTP 401 / 平台令牌失效错误码）：强制放弃缓存并重换一次。
     *
     * @return 重换后的新令牌；重换失败返回 null（调用方应停止退避重试并告警，勿风暴）
     */
    String onAuthFailure();
}
