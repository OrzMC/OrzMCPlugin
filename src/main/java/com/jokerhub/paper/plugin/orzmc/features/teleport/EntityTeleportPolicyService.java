package com.jokerhub.paper.plugin.orzmc.features.teleport;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MainConfig;
import java.util.List;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityPortalEvent;

/**
 * 实体传送策略（2026-08-09 可配置化）：
 * <ul>
 *   <li>{@code cancelEnabled=false} 不禁止——所有实体可正常传送（原版行为）</li>
 *   <li>{@code cancelEnabled=true} 时，仅白名单内实体豁免（默认白名单见
 *       {@link MainConfig#DEFAULT_ENTITY_TELEPORT_WHITELIST}，覆盖常见被动/友好实体，
 *       敌对生物不在内——防止 @e 选择器误用造成地图灾难）</li>
 * </ul>
 * 白名单项：大写 EntityType 名（如 VILLAGER）或特殊接口键（TAMEABLE）。
 * 注意：config.yml 的 {@code entity_teleport_enabled} 语义与此相反（true = 允许所有实体传送），
 * 装配层（FeatureModule）传入前取反。
 * 另注意：下界传送门穿越（{@link EntityPortalEvent} 及其子类）由 {@code OrzTPEvent} 放行，
 * 不经过本策略——本策略只作用于命令/插件触发的传送。
 */
public final class EntityTeleportPolicyService {

    private final boolean cancelEnabled;
    private final List<String> whitelist;

    /** 受限模式（仅白名单可传送）+ 默认白名单（2026-08-21 起默认值）。 */
    public EntityTeleportPolicyService() {
        this(true, MainConfig.DEFAULT_ENTITY_TELEPORT_WHITELIST);
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
            default -> {
                EntityType type = entity.getType();
                yield type != null && type.name().equals(key);
            }
        };
    }
}
