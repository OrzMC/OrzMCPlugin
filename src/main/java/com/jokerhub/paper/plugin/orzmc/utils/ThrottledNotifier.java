package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.util.concurrent.ConcurrentHashMap;

public final class ThrottledNotifier {

    public static boolean shouldRunDefault(String key) {
        long p = defaultPeriodMs();
        return shouldRunDefault(key, p);
    }

    public static boolean shouldRunDefault(String key, long ttlMs) {
        long p = defaultPeriodMs();
        return shouldRun(key, p, ttlMs);
    }

    public static void runDefault(String key, Runnable action) {
        long p = defaultPeriodMs();
        if (shouldRun(key, p)) {
            action.run();
        }
    }

    public static void runDefault(String key, long ttlMs, Runnable action) {
        long p = defaultPeriodMs();
        if (shouldRun(key, p, ttlMs)) {
            action.run();
        }
    }

    private static long defaultPeriodMs() {
        try {
            long v = OrzMC.plugin().configManager.getConfig("tnt").getLong("notify_throttle_ms");
            return v <= 0 ? 1000L : v;
        } catch (Exception ignored) {
            return 1000L;
        }
    }

    private static final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();
    private static volatile long lastCleanup = 0L;

    private static boolean shouldRun(String key, long periodMs) {
        return shouldRun(key, periodMs, periodMs);
    }

    private static boolean shouldRun(String key, long periodMs, long ttlMs) {
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

    private static void run(String key, long periodMs, Runnable action) {
        if (shouldRun(key, periodMs)) {
            action.run();
        }
    }

    private static void run(String key, long periodMs, long ttlMs, Runnable action) {
        if (shouldRun(key, periodMs, ttlMs)) {
            action.run();
        }
    }

    private static void maybeCleanup(long now, long ttlMs) {
        long lc = lastCleanup;
        if (now - lc >= ttlMs) {
            lastCleanup = now;
            last.entrySet().removeIf(e -> now - e.getValue() >= ttlMs);
        }
    }
}
