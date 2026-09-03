package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 坐牢（prison）配置。
 *
 * <p>对应 config.yml 的 {@code prison:} 段。{@code cell_location} 为牢房传送坐标，
 * 格式 {@code world,x,y,z} 或 {@code world,x,y,z,yaw,pitch}；未配置/世界未加载时
 * 由 {@code PrisonService} 回退玩家当前世界出生点。</p>
 */
public record PrisonConfig(String cellLocation) {

    /** 默认牢房坐标（世界 world 高空出生点，防止玩家出生即窒息/掉落）。 */
    public static final String DEFAULT_CELL_LOCATION = "world,0,100,0,0,0";

    public static PrisonConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new PrisonConfig(DEFAULT_CELL_LOCATION);
        }
        return new PrisonConfig(cfg.getString("cell_location", DEFAULT_CELL_LOCATION));
    }
}
