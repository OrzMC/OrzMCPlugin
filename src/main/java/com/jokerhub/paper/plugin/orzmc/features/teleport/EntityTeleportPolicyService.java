package com.jokerhub.paper.plugin.orzmc.features.teleport;

import java.util.List;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Tameable;

/**
 * 实体传送策略（2026-08-09 可配置化）：
 * <ul>
 *   <li>默认（enabled=false）不禁止——所有实体可正常传送（原版行为，兼容旧版村民过传送门等场景）</li>
 *   <li>配置为禁止（enabled=true）时，仅白名单内实体豁免（默认白名单兼容旧逻辑：
 *       TAMEABLE / ENDERMAN / ARMOR_STAND / SHULKER）</li>
 * </ul>
 * 白名单项：大写 EntityType 名（如 VILLAGER）或特殊接口键（TAMEABLE）。
 */
public final class EntityTeleportPolicyService {

    private final boolean cancelEnabled;
    private final List<String> whitelist;

    /** 兼容旧行为：禁止实体传送 + 旧白名单（历史默认）。 */
    public EntityTeleportPolicyService() {
        this(true, List.of("TAMEABLE", "ENDERMAN", "ARMOR_STAND", "SHULKER"));
    }

    public EntityTeleportPolicyService(boolean cancelEnabled, List<String> whitelist) {
        this.cancelEnabled = cancelEnabled;
        this.whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
    }

    public boolean shouldCancel(Entity entity) {
        if (!cancelEnabled) {
            return false;
        }
        for (String w : whitelist) {
            if (matches(entity, w)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(Entity entity, String whitelistEntry) {
        String key = whitelistEntry == null ? "" : whitelistEntry.trim().toUpperCase();
        return switch (key) {
            case "TAMEABLE" -> entity instanceof Tameable;
            case "ENDERMAN" -> entity instanceof Enderman;
            case "ARMOR_STAND" -> entity instanceof ArmorStand;
            case "SHULKER" -> entity instanceof Shulker;
            default -> entity.getType().name().equals(key);
        };
    }
}
