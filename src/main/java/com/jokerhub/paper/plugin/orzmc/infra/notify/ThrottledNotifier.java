package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.concurrent.ConcurrentHashMap;

public final class ThrottledNotifier {
    private final ConfigService configService;
    private final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();
    private volatile long lastCleanup = 0L;

    public ThrottledNotifier(ConfigService configService) {
        this.configService = configService;
    }

    public boolean shouldRunDefault(String key) {
        long p = defaultPeriodMs();
        return shouldRunDefault(key, p);
    }

    public boolean shouldRunDefault(String key, long ttlMs) {
        long p = defaultPeriodMs();
        return shouldRun(key, p, ttlMs);
    }

    public void runDefault(String key, Runnable action) {
        long p = defaultPeriodMs();
        if (shouldRun(key, p)) {
            action.run();
        }
    }

    public void runDefault(String key, long ttlMs, Runnable action) {
        long p = defaultPeriodMs();
        if (shouldRun(key, p, ttlMs)) {
            action.run();
        }
    }

    private long defaultPeriodMs() {
        try {
            long v = configService.getConfig("tnt").getLong("notify_throttle_ms");
            return v <= 0 ? 1000L : v;
        } catch (Exception ignored) {
            return 1000L;
        }
    }

    private boolean shouldRun(String key, long periodMs) {
        return shouldRun(key, periodMs, periodMs);
    }

    private boolean shouldRun(String key, long periodMs, long ttlMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            maybeCleanup(now, ttlMs);
            return true;
        }
        maybeCleanup(now, ttlMs);
        return false;
    }

    private void run(String key, long periodMs, Runnable action) {
        if (shouldRun(key, periodMs)) {
            action.run();
        }
    }

    private void run(String key, long periodMs, long ttlMs, Runnable action) {
        if (shouldRun(key, periodMs, ttlMs)) {
            action.run();
        }
    }

    private void maybeCleanup(long now, long ttlMs) {
        long lc = lastCleanup;
        if (now - lc >= ttlMs) {
            lastCleanup = now;
            last.entrySet().removeIf(e -> now - e.getValue() >= ttlMs);
        }
    }
}
