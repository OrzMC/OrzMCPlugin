package com.jokerhub.paper.plugin.orzmc.infra.logging;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ThrottledLogger {
    private final ConfigService configService;
    private final Logger logger;
    private final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();

    public ThrottledLogger(ConfigService configService, Logger logger) {
        this.configService = configService;
        this.logger = logger;
    }

    public void error(String key, String message) {
        error(key, message, defaultPeriodMs());
    }

    public void info(String key, String message) {
        infoInternal(key, message, defaultPeriodMs());
    }

    public void info(String key, String message, long periodMs) {
        infoInternal(key, message, periodMs <= 0 ? defaultPeriodMs() : periodMs);
    }

    public void warning(String key, String message) {
        warning(key, message, defaultPeriodMs());
    }

    private long defaultPeriodMs() {
        try {
            long v = configService.getConfig("bot").getLong("log_throttle_ms");
            return v <= 0 ? 5000L : v;
        } catch (Exception ignored) {
            return 5000L;
        }
    }

    private void error(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            logger.severe(message);
        }
    }

    private void infoInternal(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            logger.info(message);
        }
    }

    private void warning(String key, String message, long periodMs) {
        long now = System.currentTimeMillis();
        Long prev = last.get(key);
        if (prev == null || now - prev >= periodMs) {
            last.put(key, now);
            logger.warning(message);
        }
    }
}
