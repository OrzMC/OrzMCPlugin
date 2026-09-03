package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 插件自更新配置（config.yml {@code update} 段）。
 *
 * @param enabled 自更新总开关（false 时不再自动检查；{@code /update check|now} 仍可手动使用）
 * @param channel 更新通道：{@code release}（正式版）或 {@code beta}（开发版 -dev 构建）
 * @param checkIntervalHours 自动检查间隔（小时）；0 = 仅启用时检查一次
 * @param autoDownload 发现新版本后自动下载到 {@code plugins/update/}（下次重启生效）
 */
public record UpdateConfig(boolean enabled, String channel, long checkIntervalHours, boolean autoDownload) {

    public static final String CHANNEL_RELEASE = "release";
    public static final String CHANNEL_BETA = "beta";

    public static UpdateConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new UpdateConfig(true, CHANNEL_RELEASE, 12L, false);
        }
        boolean enabled = cfg.getBoolean("enabled", true);
        String channel = normalizeChannel(cfg.getString("channel", CHANNEL_RELEASE));
        long interval = cfg.getLong("check_interval_hours", 12L);
        boolean autoDownload = cfg.getBoolean("auto_download", false);
        return new UpdateConfig(enabled, channel, Math.max(0L, interval), autoDownload);
    }

    /** 未知通道回退正式版（大小写不敏感）。 */
    private static String normalizeChannel(String raw) {
        if (raw == null) {
            return CHANNEL_RELEASE;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return CHANNEL_BETA.equals(lower) ? CHANNEL_BETA : CHANNEL_RELEASE;
    }
}
