package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record TntConfig(
        boolean enable,
        boolean enableRespawnAnchor,
        int placeCooldownSeconds,
        long notifyAggregateMs,
        List<Map<String, Object>> whitelistRegions,
        List<String> exemptEntities) {

    @SuppressWarnings("unchecked")
    public static TntConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new TntConfig(false, false, 5, 3000L, List.of(), List.of());
        }
        boolean enable = cfg.getBoolean("enable", false);
        boolean enableRespawnAnchor = cfg.getBoolean("enable_respawn_anchor", false);
        int placeCooldownSeconds = cfg.getInt("place_cooldown", 5);
        long notifyAggregateMs = cfg.getLong("notify_aggregate_ms", 3000L);
        if (notifyAggregateMs <= 0) {
            // 非正窗口会让聚合退化为 1 tick，等于关闭防刷屏 → 回退默认
            notifyAggregateMs = 3000L;
        }
        List<Map<String, Object>> whitelistRegions = new ArrayList<>();
        Object rawRegions = cfg.get("whitelist");
        if (rawRegions instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    whitelistRegions.add((Map<String, Object>) m);
                }
            }
        }
        List<String> exemptEntities = new ArrayList<>();
        Object rawExempt = cfg.get("exempt_entities");
        if (rawExempt instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) exemptEntities.add(String.valueOf(o));
            }
        }
        return new TntConfig(
                enable, enableRespawnAnchor, placeCooldownSeconds, notifyAggregateMs, whitelistRegions, exemptEntities);
    }

    /** 启动健康校验：段缺失为硬缺失；字段类型/数值范围与原 {@code ConfigHealthCheck} 一致。 */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 tnt 配置段");
            return;
        }
        Object enable = section.get("enable");
        if (enable != null && !(enable instanceof Boolean)) issues.add("类型错误: tnt.enable 需为布尔值");
        Object enableAnchor = section.get("enable_respawn_anchor");
        if (enableAnchor != null && !(enableAnchor instanceof Boolean))
            issues.add("类型错误: tnt.enable_respawn_anchor 需为布尔值");
        int cd = section.getInt("place_cooldown", 0);
        if (cd < 0) issues.add("非法: tnt.place_cooldown 不得为负数");
        long agg = section.getLong("notify_aggregate_ms", 3000L);
        if (agg <= 0) issues.add("非法: tnt.notify_aggregate_ms 必须为正数（≤0 会回退默认 3000ms，静默关闭防刷屏）");
        Object wl = section.get("whitelist");
        if (wl != null && !(wl instanceof List<?>)) issues.add("类型错误: tnt.whitelist 需为列表");
        Object exempt = section.get("exempt_entities");
        if (exempt != null && !(exempt instanceof List<?>)) issues.add("类型错误: tnt.exempt_entities 需为列表");
    }
}
