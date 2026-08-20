package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.LoginRateLimitConfig;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class LoginRateLimitServiceTest {

    private static final String IP = "1.2.3.4";

    private static LoginRateLimitService service(LoginRateLimitConfig config) {
        return new LoginRateLimitService(() -> config);
    }

    private static LoginRateLimitService service(LoginRateLimitConfig config, LongSupplier clock) {
        return new LoginRateLimitService(() -> config, clock);
    }

    private static LoginRateLimitConfig defaultConfig() {
        return new LoginRateLimitConfig(true, 5, 3, true, "登录过于频繁，请稍后再试");
    }

    // ---- 总开关 ----

    @Test
    void disabledConfig_allowsFloodAndConcurrency() {
        LoginRateLimitService svc = service(new LoginRateLimitConfig(false, 1, 1, true, "登录过于频繁，请稍后再试"));
        for (int i = 0; i < 10; i++) {
            assertFalse(svc.isRateLimited(IP), "关闭时应放行所有登录尝试");
        }
        svc.onPlayerJoin(IP, "alice");
        svc.onPlayerJoin(IP, "bob");
        assertFalse(svc.isConcurrencyReached(IP), "关闭时不应判定并发超限");
    }

    // ---- 频率限流 ----

    @Test
    void rateLimited_afterMaxAttemptsPerMinute() {
        LoginRateLimitService svc = service(defaultConfig());
        // 上限 5：前 5 次放行并记录，第 6 次被限流
        for (int i = 0; i < 5; i++) {
            assertFalse(svc.isRateLimited(IP), "第 " + (i + 1) + " 次尝试应放行");
        }
        assertTrue(svc.isRateLimited(IP));
    }

    @Test
    void windowSlides_afterOneMinute() {
        AtomicLong now = new AtomicLong(0L);
        LoginRateLimitService svc = service(defaultConfig(), now::get);
        for (int i = 0; i < 5; i++) {
            assertFalse(svc.isRateLimited(IP));
        }
        assertTrue(svc.isRateLimited(IP));
        // 60s 后窗口滑动，恢复可用额度
        now.set(60_000L);
        assertFalse(svc.isRateLimited(IP));
    }

    @Test
    void rateLimit_isPerIp() {
        LoginRateLimitService svc = service(defaultConfig());
        for (int i = 0; i < 5; i++) {
            assertFalse(svc.isRateLimited(IP));
        }
        assertTrue(svc.isRateLimited(IP));
        // 其他 IP 不受影响
        assertFalse(svc.isRateLimited("5.6.7.8"));
    }

    // ---- 并发限制 ----

    @Test
    void concurrencyReached_afterMaxOnline() {
        LoginRateLimitService svc = service(defaultConfig());
        svc.onPlayerJoin(IP, "alice");
        svc.onPlayerJoin(IP, "bob");
        svc.onPlayerJoin(IP, "carol");
        assertTrue(svc.isConcurrencyReached(IP));
    }

    @Test
    void concurrency_allowsWithinLimit() {
        LoginRateLimitService svc = service(defaultConfig());
        svc.onPlayerJoin(IP, "alice");
        svc.onPlayerJoin(IP, "bob");
        assertFalse(svc.isConcurrencyReached(IP));
    }

    @Test
    void onPlayerQuit_freesConcurrencySlot() {
        LoginRateLimitService svc = service(defaultConfig());
        svc.onPlayerJoin(IP, "alice");
        svc.onPlayerJoin(IP, "bob");
        svc.onPlayerJoin(IP, "carol");
        assertTrue(svc.isConcurrencyReached(IP));
        svc.onPlayerQuit("bob");
        assertFalse(svc.isConcurrencyReached(IP));
    }

    @Test
    void onPlayerQuit_unknownPlayer_noop() {
        LoginRateLimitService svc = service(defaultConfig());
        svc.onPlayerJoin(IP, "alice");
        svc.onPlayerQuit("nobody");
        assertFalse(svc.isConcurrencyReached(IP));
    }

    // ---- 状态清理 ----

    @Test
    void clear_resetsBothState() {
        LoginRateLimitService svc = service(defaultConfig());
        for (int i = 0; i < 5; i++) {
            assertFalse(svc.isRateLimited(IP));
        }
        assertTrue(svc.isRateLimited(IP));
        svc.onPlayerJoin(IP, "alice");
        svc.onPlayerJoin(IP, "bob");
        svc.onPlayerJoin(IP, "carol");
        assertTrue(svc.isConcurrencyReached(IP));
        svc.clear(IP);
        assertFalse(svc.isRateLimited(IP));
        assertFalse(svc.isConcurrencyReached(IP));
    }

    @Test
    void sweepExpired_removesStaleIpBuckets() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        LoginRateLimitService svc = service(defaultConfig(), now::get);
        // IP 在 t=0 尝试一次，留下一个过期后无用的桶
        assertFalse(svc.isRateLimited(IP));
        // 推进 120s：IP 的窗口已过期，且超过清扫间隔
        now.set(120_000L);
        // 另一个 IP 触发惰性全表清扫
        assertFalse(svc.isRateLimited("9.9.9.9"));

        java.lang.reflect.Field f = LoginRateLimitService.class.getDeclaredField("attemptTimes");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ?> attempts = (java.util.Map<String, ?>) f.get(svc);
        assertFalse(attempts.containsKey(IP), "过期 IP 桶应在清扫后移除，避免无界增长");
    }
}
