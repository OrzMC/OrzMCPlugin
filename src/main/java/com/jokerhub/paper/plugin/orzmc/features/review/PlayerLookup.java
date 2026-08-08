package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.Optional;
import java.util.UUID;

/**
 * 玩家名 ↔ UUID 解析端口（离线服需查缓存，审核时申请者可能已下线）。
 *
 * <p>宿主侧用 {@code Bukkit.getOfflinePlayer()} 实现；测试中可注入假实现。</p>
 */
public interface PlayerLookup {

    /** 玩家名 → UUID（查不到返回 empty）。 */
    Optional<UUID> resolve(String playerName);

    /** UUID → 玩家名（查不到返回 empty）。 */
    Optional<String> name(UUID playerId);
}
