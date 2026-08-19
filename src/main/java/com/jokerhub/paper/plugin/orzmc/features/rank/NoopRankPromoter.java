package com.jokerhub.paper.plugin.orzmc.features.rank;

import java.util.Optional;
import java.util.UUID;

/**
 * LuckPerms 缺失时的降级实现（软依赖）。
 *
 * <p>LP 未安装时装配层使用本实现：所有升降级/组查询返回 null/false，
 * 调用方（RankService/命令层）据此给出"权限管理不可用"提示；
 * 当前权限组一律回退 default（访客）——无本地推断，避免虚假展示。
 * 类加载零 LP 依赖。</p>
 */
public final class NoopRankPromoter implements RankPromoter {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String currentTrackGroup(UUID playerId) {
        return null;
    }

    @Override
    public String promote(UUID playerId) {
        return null;
    }

    @Override
    public String demote(UUID playerId) {
        return null;
    }

    @Override
    public UUID resolvePlayerId(String playerName) {
        org.bukkit.OfflinePlayer p = org.bukkit.Bukkit.getOfflinePlayer(playerName);
        return p.hasPlayedBefore() ? p.getUniqueId() : null;
    }

    @Override
    public Optional<String> playerName(UUID playerId) {
        return Optional.empty();
    }
}
