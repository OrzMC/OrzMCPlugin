package com.jokerhub.paper.plugin.orzmc.utils;

import java.util.concurrent.ConcurrentHashMap;

public final class ThrottledNotifier {
    private static final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();
    private static volatile long lastCleanup = 0L;

    public static void run(String key, long periodMs, Runnable action) {
        if (shouldRun(key, periodMs, periodMs)) {
            action.run();
        }
    }

    public static void run(String key, long periodMs, long ttlMs, Runnable action) {
        if (shouldRun(key, periodMs, ttlMs)) {
            action.run();
        }
    }

    public static boolean shouldRun(String key, long periodMs) {
        return shouldRun(key, periodMs, periodMs);
    }

    public static boolean shouldRun(String key, long periodMs, long ttlMs) {
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

    private static void maybeCleanup(long now, long ttlMs) {
        long lc = lastCleanup;
        if (now - lc >= ttlMs) {
            lastCleanup = now;
            last.entrySet().removeIf(e -> now - e.getValue() >= ttlMs);
        }
    }
}
