package com.jokerhub.paper.plugin.orzmc.features.whitelist;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public interface WhitelistService {
    List<String> buildWhitelistLines(Server server);

    Set<String> cleanupInactivePlayers(Server server, int inactiveDays);

    String addPlayers(Server server, Set<String> userNames);

    String removePlayers(Server server, Set<String> userNames);

    static WhitelistService defaultImpl() {
        return new DefaultWhitelistService();
    }

    final class DefaultWhitelistService implements WhitelistService {
        @Override
        public List<String> buildWhitelistLines(Server server) {
            ArrayList<OfflinePlayer> whiteListPlayers = new ArrayList<>(server.getWhitelistedPlayers());
            whiteListPlayers.sort((o1, o2) -> Long.compare(o2.getLastSeen(), o1.getLastSeen()));
            ArrayList<String> lines = new ArrayList<>();
            for (OfflinePlayer player : whiteListPlayers) {
                String playerName = player.getName();
                String isOnline = player.isOnline() ? "•" : "◦";
                StringBuilder line =
                        new StringBuilder().append(isOnline).append(" ").append(playerName);
                long lastSeenTimestamp = player.getLastSeen();
                if (lastSeenTimestamp > 0) {
                    String lastSeen = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(lastSeenTimestamp));
                    line.append(" ").append(lastSeen);
                }
                lines.add(line.toString());
            }
            return lines;
        }

        @Override
        public Set<String> cleanupInactivePlayers(Server server, int inactiveDays) {
            ArrayList<OfflinePlayer> whiteListPlayers = new ArrayList<>(server.getWhitelistedPlayers());
            long now = System.currentTimeMillis();
            long threshold = now - inactiveDays * 24L * 60L * 60L * 1000L;
            Set<OfflinePlayer> toRemove = whiteListPlayers.stream()
                    .filter(p -> {
                        long lastSeen = p.getLastSeen();
                        return lastSeen <= 0 || lastSeen < threshold;
                    })
                    .collect(Collectors.toSet());
            for (OfflinePlayer p : toRemove) {
                if (p.isWhitelisted()) {
                    p.setWhitelisted(false);
                    Player onlinePlayer = server.getPlayer(p.getUniqueId());
                    if (onlinePlayer != null) {
                        onlinePlayer.kick();
                    }
                }
            }
            server.reloadWhitelist();
            return toRemove.stream()
                    .map(p -> p.getName() == null ? "(unknown)" : p.getName())
                    .collect(Collectors.toSet());
        }

        @Override
        public String addPlayers(Server server, Set<String> userNames) {
            for (String userName : userNames) {
                OfflinePlayer player = server.getOfflinePlayer(userName);
                if (!player.isWhitelisted()) {
                    player.setWhitelisted(true);
                }
            }
            server.reloadWhitelist();
            Set<String> allWhiteListName = server.getWhitelistedPlayers().stream()
                    .map(OfflinePlayer::getName)
                    .collect(Collectors.toSet());
            String message = "------白名单添加------\n";
            if (allWhiteListName.containsAll(userNames)) {
                message += String.join(
                        "\n", userNames.stream().map(name -> "✔︎ ︎" + name).collect(Collectors.toSet()));
            }
            Set<String> failed = new HashSet<>(userNames);
            failed.removeAll(allWhiteListName);
            if (!failed.isEmpty()) {
                message += String.join(
                        "\n", failed.stream().map(name -> "✘ " + name).collect(Collectors.toSet()));
            }
            return message;
        }

        @Override
        public String removePlayers(Server server, Set<String> userNames) {
            for (String userName : userNames) {
                OfflinePlayer player = server.getOfflinePlayer(userName);
                if (player.isWhitelisted()) {
                    player.setWhitelisted(false);
                    Player onlinePlayer = server.getPlayer(player.getUniqueId());
                    if (onlinePlayer != null) {
                        onlinePlayer.kick();
                    }
                }
            }
            server.reloadWhitelist();
            Set<String> allWhiteListName = server.getWhitelistedPlayers().stream()
                    .map(OfflinePlayer::getName)
                    .collect(Collectors.toSet());
            String message = "------白名单移除------\n";
            Set<String> removed = new HashSet<>(userNames);
            removed.removeAll(allWhiteListName);
            if (!removed.isEmpty()) {
                message += String.join(
                        "\n", removed.stream().map(name -> "✔︎ " + name).collect(Collectors.toSet()));
            }
            Set<String> notRemoved = new HashSet<>(userNames);
            notRemoved.retainAll(allWhiteListName);
            if (!notRemoved.isEmpty()) {
                message += String.join(
                        "\n", notRemoved.stream().map(name -> "✘ " + name).collect(Collectors.toSet()));
            }
            return message;
        }
    }
}
