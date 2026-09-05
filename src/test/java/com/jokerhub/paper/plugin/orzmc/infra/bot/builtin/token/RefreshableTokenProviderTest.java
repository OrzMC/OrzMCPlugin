package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RefreshableTokenProviderTest {

    private static final Duration TTL = Duration.ofSeconds(120);
    private static final Duration AHEAD = Duration.ofSeconds(30);

    @Test
    void firstFresh_fetchesAndCaches() {
        AtomicInteger calls = new AtomicInteger();
        RefreshableTokenProvider p = new RefreshableTokenProvider(() -> "tok-" + calls.incrementAndGet(), TTL, AHEAD);

        assertEquals("tok-1", p.fresh());
        assertEquals("tok-1", p.current());
        assertEquals(1, calls.get());
    }

    @Test
    void freshWithinTtl_doesNotRefetch() {
        AtomicInteger calls = new AtomicInteger();
        RefreshableTokenProvider p = new RefreshableTokenProvider(() -> "tok-" + calls.incrementAndGet(), TTL, AHEAD);

        p.fresh();
        assertEquals("tok-1", p.fresh());
        assertEquals("tok-1", p.fresh());
        assertEquals(1, calls.get());
    }

    @Test
    void onAuthFailure_forcesRefetchEvenWithinTtl() {
        AtomicInteger calls = new AtomicInteger();
        RefreshableTokenProvider p = new RefreshableTokenProvider(() -> "tok-" + calls.incrementAndGet(), TTL, AHEAD);

        p.fresh();
        assertEquals("tok-2", p.onAuthFailure());
        assertEquals(2, calls.get());
    }

    @Test
    void onAuthFailureWhenFetchFails_returnsNullAndClearsCache() {
        RefreshableTokenProvider p = new RefreshableTokenProvider(() -> null, TTL, AHEAD);

        assertNull(p.fresh());
        assertNull(p.onAuthFailure());
        assertNull(p.current());
    }

    @Test
    void refreshFailureKeepsOldTokenWhenStillValid() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> next = new AtomicReference<>("tok-1");
        RefreshableTokenProvider p = new RefreshableTokenProvider(
                () -> {
                    calls.incrementAndGet();
                    return next.get();
                },
                TTL,
                Duration.ofSeconds(120)); // refreshAhead == TTL → 立即进入预刷新窗口

        assertEquals("tok-1", p.fresh());
        next.set(null); // 后续刷新失败
        assertEquals("tok-1", p.fresh()); // 预刷新失败仍回退旧令牌
        assertEquals(2, calls.get());
    }

    @Test
    void concurrentFresh_refetchesOnlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RefreshableTokenProvider p = new RefreshableTokenProvider(() -> "tok-" + calls.incrementAndGet(), TTL, AHEAD);

        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            p.fresh();
                        } catch (Exception e) {
                            failure.set(e);
                        }
                    })
                    .start();
        }
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        Thread.sleep(300);

        assertNull(failure.get());
        assertEquals(1, calls.get());
    }

    @Test
    void constructorRejectsNullSupplier() {
        try {
            new RefreshableTokenProvider(null, TTL, AHEAD);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    @Test
    void expiryThenFresh_refetches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RefreshableTokenProvider p = new RefreshableTokenProvider(
                () -> "tok-" + calls.incrementAndGet(), Duration.ofMillis(80), Duration.ZERO);

        assertEquals("tok-1", p.fresh());
        Thread.sleep(120);
        assertEquals("tok-2", p.fresh());
        assertEquals("tok-2", p.current());
        assertEquals(2, calls.get());
    }
}
