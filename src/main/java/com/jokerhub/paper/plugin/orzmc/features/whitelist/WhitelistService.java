package com.jokerhub.paper.plugin.orzmc.features.whitelist;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public interface WhitelistService {
    List<String> buildWhitelistLines(Server server);

    Set<String> cleanupInactivePlayers(Server server, int inactiveDays);

    String addPlayers(Server server, Set<String> userNames);

    String removePlayers(Server server, Set<String> userNames);

    static WhitelistService defaultImpl(JavaPlugin plugin) {
        return new DefaultWhitelistService(plugin);
    }

    final class DefaultWhitelistService implements WhitelistService {
        /** 用于把踢人投递到玩家所属 region 线程（Folia），Paper 上无副作用。 */
        private final JavaPlugin plugin;

        DefaultWhitelistService(JavaPlugin plugin) {
            this.plugin = plugin;
        }

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
                        kickInPlayerRegion(onlinePlayer);
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
            Set<String> added = new HashSet<>(userNames);
            added.retainAll(allWhiteListName);
            Set<String> failed = new HashSet<>(userNames);
            failed.removeAll(allWhiteListName);
            StringBuilder message = new StringBuilder("------白名单添加------\n");
            if (!added.isEmpty()) {
                message.append(added.stream().sorted().map(name -> "✔︎ " + name).collect(Collectors.joining("\n")));
            }
            if (!failed.isEmpty()) {
                if (!added.isEmpty()) message.append("\n");
                message.append(failed.stream().sorted().map(name -> "✘ " + name).collect(Collectors.joining("\n")));
            }
            return message.toString();
        }

        @Override
        public String removePlayers(Server server, Set<String> userNames) {
            for (String userName : userNames) {
                OfflinePlayer player = server.getOfflinePlayer(userName);
                if (player.isWhitelisted()) {
                    player.setWhitelisted(false);
                    Player onlinePlayer = server.getPlayer(player.getUniqueId());
                    if (onlinePlayer != null) {
                        kickInPlayerRegion(onlinePlayer);
                    }
                }
            }
            server.reloadWhitelist();
            Set<String> allWhiteListName = server.getWhitelistedPlayers().stream()
                    .map(OfflinePlayer::getName)
                    .collect(Collectors.toSet());
            Set<String> removed = new HashSet<>(userNames);
            removed.removeAll(allWhiteListName);
            Set<String> notRemoved = new HashSet<>(userNames);
            notRemoved.retainAll(allWhiteListName);
            StringBuilder message = new StringBuilder("------白名单移除------\n");
            if (!removed.isEmpty()) {
                message.append(
                        removed.stream().sorted().map(name -> "✔︎ " + name).collect(Collectors.joining("\n")));
            }
            if (!notRemoved.isEmpty()) {
                if (!removed.isEmpty()) message.append("\n");
                message.append(
                        notRemoved.stream().sorted().map(name -> "✘ " + name).collect(Collectors.joining("\n")));
            }
            return message.toString();
        }

        /**
         * Folia：踢人必须投递到玩家所在 region 线程，直接调用会抛「not the correct region」异常。
         * 经 {@link Player#getScheduler()} 把 {@code kick()} 放到玩家自己的 region 执行；
         * Paper 上等价于主线程下 tick 执行，语义不变。
         */
        private void kickInPlayerRegion(Player onlinePlayer) {
            onlinePlayer.getScheduler().run(plugin, t -> onlinePlayer.kick(), () -> {});
        }
    }
}
