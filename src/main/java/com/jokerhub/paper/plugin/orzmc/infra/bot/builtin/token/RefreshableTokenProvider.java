package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 短期令牌提供者（QQ access_token / 飞书 tenant_access_token，约 2h）。
 *
 * <p>语义：缓存令牌 + 到期前 {@code refreshAhead} 预刷新 + {@link #onAuthFailure()} 强制重换一次；
 * 刷新单飞（{@code synchronized}，并发只发一次换取请求）；刷新失败保留旧令牌（旧令牌已失效时
 * {@link #fresh()} 返回 null，调用方降级）。换取请求由 {@code fetchNewToken} 提供（平台 adapter 注入），
 * 返回 null 表示换取失败。</p>
 */
public final class RefreshableTokenProvider implements TokenProvider {

    private final Supplier<String> fetchNewToken;
    private final Duration tokenTtl;
    private final Duration refreshAhead;
    private final Object lock = new Object();

    private String cached;
    private long cachedExpiryEpochMs; // 0 = 无缓存

    public RefreshableTokenProvider(Supplier<String> fetchNewToken, Duration tokenTtl, Duration refreshAhead) {
        if (fetchNewToken == null) {
            throw new IllegalArgumentException("fetchNewToken must not be null");
        }
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("tokenTtl must be positive");
        }
        this.fetchNewToken = fetchNewToken;
        this.tokenTtl = tokenTtl;
        this.refreshAhead = refreshAhead == null ? Duration.ZERO : refreshAhead;
    }

    @Override
    public String current() {
        return cached;
    }

    @Override
    public String fresh() {
        return ensureFresh(false);
    }

    @Override
    public String onAuthFailure() {
        return ensureFresh(true);
    }

    /**
     * @param force 鉴权失败触发：放弃现有缓存强换；否则仅在无缓存或临期/过期时刷新
     */
    private String ensureFresh(boolean force) {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            boolean stale = cached == null || now >= cachedExpiryEpochMs;
            boolean imminent =
                    refreshAhead.toMillis() > 0 && now >= cachedExpiryEpochMs - Math.max(1, refreshAhead.toMillis());
            if (!force && !stale && !imminent) {
                return cached;
            }
            if (force) {
                // 鉴权已失败：放弃旧缓存，强制重换（失败即 null，调用方停用告警）
                cached = null;
                cachedExpiryEpochMs = 0;
                String fetched = fetchNewToken.get();
                if (fetched == null) {
                    return null;
                }
                store(fetched);
                return cached;
            }
            // 临期/过期：尝试刷新；失败保留旧令牌（可能仍被平台接受），避免可用性闪断
            String fetched = fetchNewToken.get();
            if (fetched == null) {
                return cached;
            }
            store(fetched);
            return cached;
        }
    }

    private void store(String token) {
        this.cached = token;
        this.cachedExpiryEpochMs = System.currentTimeMillis() + tokenTtl.toMillis();
    }
}
