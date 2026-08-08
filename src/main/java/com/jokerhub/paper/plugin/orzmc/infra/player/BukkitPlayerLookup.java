package com.jokerhub.paper.plugin.orzmc.infra.player;

import com.jokerhub.paper.plugin.orzmc.features.review.PlayerLookup;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * 玩家名 ↔ UUID 解析端口实现（离线服查 Bukkit 最后已知缓存）。
 *
 * <p>审核时申请者可能已下线，用 {@code Bukkit.getOfflinePlayer()} 保证离线也可解析。</p>
 */
public final class BukkitPlayerLookup implements PlayerLookup {

    @Override
    public Optional<UUID> resolve(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return player.hasPlayedBefore() ? Optional.of(player.getUniqueId()) : Optional.empty();
    }

    @Override
    public Optional<String> name(UUID playerId) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(playerId).getName());
    }
}
