package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * EasyBot IM Gateway 类型化配置。
 *
 * <p>支持多平台消息路由：每个平台独立配置 admin_group / player_group / admin_dm。</p>
 */
public record EasyBotConfig(
        String apiServer,
        String wsServer,
        String apiKey,
        Map<String, PlatformEntry> platforms,
        int httpConnectTimeoutSec,
        int httpRequestTimeoutSec,
        int httpMaxRetries,
        int wsMaxRetries,
        long wsBaseRetryMs,
        long wsMaxDelayMs,
        int wsJitterPercent,
        long wsStableResetMs,
        boolean wsMessageLogEnabled,
        long wsMessageLogThrottleMs) {

    public EasyBotConfig {
        apiServer = normalizeBaseUrl(apiServer);
        wsServer = normalizeBaseUrl(wsServer);
        apiKey = clean(apiKey);
        platforms = immutablePlatforms(platforms);
    }

    /**
     * 是否有至少一个平台已启用。
     * 替换了旧的全局 {@code enable_ez_bot} 开关，自动检测平台配置。
     */
    public boolean enabled() {
        return platforms.values().stream().anyMatch(PlatformEntry::enabled);
    }

    /** Values that require a new WebSocket client when changed by config reload. */
    public String connectionFingerprint() {
        return String.join(
                "|",
                apiServer,
                wsServer,
                hashKey(apiKey),
                String.valueOf(httpConnectTimeoutSec),
                String.valueOf(httpRequestTimeoutSec),
                String.valueOf(httpMaxRetries),
                String.valueOf(wsMaxRetries),
                String.valueOf(wsBaseRetryMs),
                String.valueOf(wsMaxDelayMs),
                String.valueOf(wsJitterPercent),
                String.valueOf(wsStableResetMs),
                String.valueOf(wsMessageLogEnabled),
                String.valueOf(wsMessageLogThrottleMs));
    }

    /**
     * 单个平台配置项。
     *
     * @param enabled     是否启用此平台（false 时不收不发）
     * @param adminGroup  管理群 target（如 "qq:YOUR_GROUP_ID"）
     * @param playerGroup 玩家群 target（为空时 PUBLIC 降级到 adminGroup）
     * @param adminDm     管理员私聊 target（如 "qq:YOUR_USER_ID"）
     */
    public record PlatformEntry(boolean enabled, String adminGroup, String playerGroup, String adminDm) {

        public PlatformEntry {
            adminGroup = clean(adminGroup);
            playerGroup = clean(playerGroup);
            adminDm = clean(adminDm);
        }

        public static PlatformEntry from(ConfigurationSection sec) {
            if (sec == null) {
                return new PlatformEntry(false, "", "", "");
            }
            return new PlatformEntry(
                    sec.getBoolean("enabled", false),
                    sec.getString("admin_group", ""),
                    sec.getString("player_group", ""),
                    sec.getString("admin_dm", ""));
        }
    }

    /**
     * 从 {@code easybot.yml} 的根 {@link ConfigurationSection} 创建配置。
     *
     * @param cfg easybot.yml 的根配置段，null 时返回全默认值（enabled=false）
     * @return EasyBotConfig 实例
     */
    public static EasyBotConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new EasyBotConfig(
                    "", "", "", Collections.emptyMap(), 3, 3, 3, 10, 5000, 60000, 10, 20000, false, 60000);
        }

        // 解析 platforms 段
        Map<String, PlatformEntry> platforms = Collections.emptyMap();
        ConfigurationSection platformsSec = cfg.getConfigurationSection("platforms");
        if (platformsSec != null) {
            platforms = new LinkedHashMap<>();
            for (String key : platformsSec.getKeys(false)) {
                String normalizedKey = clean(key).toLowerCase(Locale.ROOT);
                if (!normalizedKey.isEmpty()) {
                    platforms.put(normalizedKey, PlatformEntry.from(platformsSec.getConfigurationSection(key)));
                }
            }
        }

        return new EasyBotConfig(
                cfg.getString("api_server", "http://127.0.0.1:8080"),
                cfg.getString("ws_server", "ws://127.0.0.1:8080"),
                cfg.getString("api_key", ""),
                platforms,
                cfg.getInt("http_connect_timeout_seconds", 3),
                cfg.getInt("http_request_timeout_seconds", 3),
                cfg.getInt("http_max_retries", 3),
                cfg.getInt("ws_max_retries", 10),
                cfg.getLong("ws_base_retry_ms", 5000),
                cfg.getLong("ws_max_delay_ms", 60000),
                cfg.getInt("ws_jitter_percent", 10),
                cfg.getLong("ws_stable_reset_ms", 20000),
                cfg.getBoolean("ws_message_log_enabled", false),
                cfg.getLong("ws_message_log_throttle_ms", 60000));
    }

    private static Map<String, PlatformEntry> immutablePlatforms(Map<String, PlatformEntry> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, PlatformEntry> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = clean(key).toLowerCase(Locale.ROOT);
            if (!normalizedKey.isEmpty()) {
                copy.put(normalizedKey, value == null ? new PlatformEntry(false, "", "", "") : value);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = clean(value);
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /** apiKey 的 SHA-256 前缀（16 字符）：指纹只用于相等比较，不存明文，防误日志/调试泄露。 */
    private static String hashKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 是 JVM 必需算法，理论不会走到；退化用哈希码（仍不泄露明文）
            return Integer.toHexString(key.hashCode());
        }
    }
}
