package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.paper.plugin.orzmc.OrzMC;

import java.util.concurrent.ConcurrentHashMap;

public final class ThrottledLogger {
    private static final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();

    public static void error(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            OrzMC.logger().severe(message);
        }
    }

    public static void info(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            OrzMC.logger().info(message);
        }
    }

    public static void warning(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            OrzMC.logger().warning(message);
        }
    }
}
