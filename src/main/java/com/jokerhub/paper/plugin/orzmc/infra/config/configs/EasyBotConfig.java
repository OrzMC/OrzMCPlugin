package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * EasyBot IM Gateway 类型化配置。
 *
 * <p>与现有 {@link BotConfig}（来源于 bot.yml）完全独立，使用专属 easybot.yml。
 * 支持多平台消息路由：每个平台独立配置 admin_group / player_group / admin_dm。</p>
 */
public record EasyBotConfig(
        String apiServer,
        String wsServer,
        String apiKey,
        String parseMode,
        Map<String, PlatformEntry> platforms,
        Map<String, Map<String, String>> channels,
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

    /**
     * 是否有至少一个平台已启用。
     * 替换了旧的全局 {@code enable_ez_bot} 开关，自动检测平台配置。
     */
    public boolean enabled() {
        return platforms.values().stream().anyMatch(PlatformEntry::enabled);
    }

    /**
     * 单个平台配置项。
     *
     * @param enabled     是否启用此平台（false 时不收不发）
     * @param adminGroup  管理群 target（如 "qq:1082305302"）
     * @param playerGroup 玩家群 target（为空时 PUBLIC 降级到 adminGroup）
     * @param adminDm     管理员私聊 target（如 "qq:1092760538"）
     */
    public record PlatformEntry(boolean enabled, String adminGroup, String playerGroup, String adminDm) {

        public static PlatformEntry from(ConfigurationSection sec) {
            if (sec == null) {
                return new PlatformEntry(false, "", "", "");
            }
            return new PlatformEntry(
                    sec.getBoolean("enabled", true),
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
                    "",
                    "",
                    "",
                    "markdown",
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    3,
                    3,
                    3,
                    10,
                    5000,
                    60000,
                    10,
                    20000,
                    false,
                    60000);
        }

        // 解析 platforms 段
        Map<String, PlatformEntry> platforms = Collections.emptyMap();
        ConfigurationSection platformsSec = cfg.getConfigurationSection("platforms");
        if (platformsSec != null) {
            platforms = new HashMap<>();
            for (String key : platformsSec.getKeys(false)) {
                platforms.put(key, PlatformEntry.from(platformsSec.getConfigurationSection(key)));
            }
        }

        // 解析 channels 段
        Map<String, Map<String, String>> channels = Collections.emptyMap();
        ConfigurationSection channelsSec = cfg.getConfigurationSection("channels");
        if (channelsSec != null) {
            channels = new HashMap<>();
            for (String channelKey : channelsSec.getKeys(false)) {
                ConfigurationSection targetSec = channelsSec.getConfigurationSection(channelKey);
                if (targetSec == null) continue;
                Map<String, String> targets = new HashMap<>();
                for (String platformKey : targetSec.getKeys(false)) {
                    String target = targetSec.getString(platformKey);
                    if (target != null && !target.isEmpty()) {
                        targets.put(platformKey, target);
                    }
                }
                if (!targets.isEmpty()) {
                    channels.put(channelKey, targets);
                }
            }
        }

        return new EasyBotConfig(
                cfg.getString("api_server", "http://127.0.0.1:8020"),
                cfg.getString("ws_server", "ws://127.0.0.1:8020"),
                cfg.getString("api_key", ""),
                cfg.getString("parse_mode", "markdown"),
                platforms,
                channels,
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
}
