package com.jokerhub.paper.plugin.orzmc.infra.logging;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.util.concurrent.ConcurrentHashMap;

public final class ThrottledLogger {
    public static void error(String key, String message) {
        error(key, message, defaultPeriodMs());
    }

    public static void info(String key, String message) {
        info(key, message, defaultPeriodMs());
    }

    public static void warning(String key, String message) {
        warning(key, message, defaultPeriodMs());
    }

    private static long defaultPeriodMs() {
        try {
            long v = OrzMC.plugin().configManager.getConfig("bot").getLong("log_throttle_ms");
            return v <= 0 ? 5000L : v;
        } catch (Exception ignored) {
            return 5000L;
        }
    }

    private static final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();

    private static void error(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            OrzMC.logger().severe(message);
        }
    }

    private static void info(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            OrzMC.logger().info(message);
        }
    }

    private static void warning(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            OrzMC.logger().warning(message);
        }
    }
}
