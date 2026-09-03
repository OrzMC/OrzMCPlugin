package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 游戏模式矫正（权限组变化后）配置。
 *
 * <p>对应 config.yml 的 {@code gamemode-correction:} 段，供 {@code GamemodeCorrectionService}
 * 使用：玩家被降级后若当前游戏模式（创造/观察/冒险）已无对应权限，自动切回生存——
 * 权限组与游戏模式匹配，杜绝「普通玩家保留创造能力」的权限漏洞。</p>
 */
public record GamemodeCorrectionConfig(boolean enabled, long debounceMs, boolean teleportToSpawnOnSpectatorFix) {

    /** 默认防抖窗口（毫秒）：同一玩家在该窗口内不重复矫正。 */
    public static final long DEFAULT_DEBOUNCE_MS = 2000L;

    public static GamemodeCorrectionConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new GamemodeCorrectionConfig(true, DEFAULT_DEBOUNCE_MS, true);
        }
        long debounce = cfg.getLong("debounce-ms", DEFAULT_DEBOUNCE_MS);
        if (debounce < 0) {
            // 负窗口视为禁用防抖（恒放行）
            debounce = 0;
        }
        return new GamemodeCorrectionConfig(
                cfg.getBoolean("enabled", true), debounce, cfg.getBoolean("teleport-to-spawn-on-spectator-fix", true));
    }
}
