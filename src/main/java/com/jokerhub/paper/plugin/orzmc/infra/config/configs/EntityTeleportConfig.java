package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 实体传送策略配置：读取 config.yml 根级 {@code entity_teleport_enabled} /
 * {@code entity_teleport_whitelist} 两个键（合并重构后实体传送仍为根级扁平键，未段化）。
 *
 * <p>{@code enabled=true} 表示允许命令/插件传送所有实体（原版行为）；{@code enabled=false}
 * 时仅白名单内实体可被传送（默认，防 @e 选择器误用）。注意与
 * {@code EntityTeleportPolicyService} 的 cancelEnabled 语义相反，装配层取反后传入。</p>
 */
public record EntityTeleportConfig(boolean enabled, List<String> whitelist) {

    /**
     * 实体传送白名单兜底：与 config.yml 保持一致。只含常见被动/友好实体
     * （村民/牲畜/友好水生/傀儡等），敌对生物不在内——防止 @e 选择器误用。
     * TAMEABLE 按接口判定，已覆盖猫/狗/鹦鹉 + 全部马科，无需重复列出。
     */
    public static final List<String> DEFAULT_ENTITY_TELEPORT_WHITELIST = List.of(
            "TAMEABLE",
            "ENDERMAN",
            "ARMOR_STAND",
            "SHULKER",
            "VILLAGER",
            "WANDERING_TRADER",
            "COW",
            "PIG",
            "SHEEP",
            "CHICKEN",
            "RABBIT",
            "GOAT",
            "MOOSHROOM",
            "AXOLOTL",
            "BEE",
            "IRON_GOLEM");

    public static EntityTeleportConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new EntityTeleportConfig(false, DEFAULT_ENTITY_TELEPORT_WHITELIST);
        }
        boolean enabled = cfg.getBoolean("entity_teleport_enabled", false);
        List<String> whitelist = new ArrayList<>(cfg.getStringList("entity_teleport_whitelist"));
        if (whitelist.isEmpty()) {
            // config.yml 白名单为空/未配置 → 回退内置 16 项（保持现语义）
            whitelist.addAll(DEFAULT_ENTITY_TELEPORT_WHITELIST);
        }
        return new EntityTeleportConfig(enabled, whitelist);
    }
}
