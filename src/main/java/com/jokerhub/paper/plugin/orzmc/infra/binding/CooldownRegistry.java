package com.jokerhub.paper.plugin.orzmc.infra.binding;

import java.util.concurrent.ConcurrentHashMap;

public final class CooldownRegistry {
    private static final ConcurrentHashMap<String, Long> lastInvoke = new ConcurrentHashMap<>();

    private CooldownRegistry() {}

    public static boolean isCoolingDown(String key, int seconds) {
        if (seconds <= 0) return false;
        long now = System.currentTimeMillis();
        Long prev = lastInvoke.get(key);
        if (prev == null || now - prev >= seconds * 1000L) {
            lastInvoke.put(key, now);
            return false;
        }
        return true;
    }
}
