package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

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
            ConfigurationSection tntSection = configService.getConfig("config").getConfigurationSection("tnt");
            if (tntSection != null) {
                long v = tntSection.getLong("notify_throttle_ms");
                return v <= 0 ? 1000L : v;
            }
            FileConfiguration legacy = configService.loadFile("tnt.yml");
            if (legacy != null) {
                long v = legacy.getLong("notify_throttle_ms");
                return v <= 0 ? 1000L : v;
            }
            return 1000L;
        } catch (Exception ignored) {
            // 配置段未就绪时使用默认值 —— 安全兜底，无需日志
            return 1000L;
        }
    }

    /** 按固定周期限频（不读取 tnt 配置）：key 在 periodMs 内最多放行一次，用于与 TNT 无关的告警限频。 */
    public boolean shouldRun(String key, long periodMs) {
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

    private void maybeCleanup(long now, long ttlMs) {
        long lc = lastCleanup;
        if (now - lc >= ttlMs) {
            lastCleanup = now;
            last.entrySet().removeIf(e -> now - e.getValue() >= ttlMs);
        }
    }
}
