package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.orzmc.world.Optimizer;
import com.jokerhub.orzmc.world.ProgressMode;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OrzMessageParser {

    public static void parse(String message, Boolean isAdmin, Consumer<String> callback) {

        if (!OrzUserCmd.isValidCmd(message)) return;

        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(message.split("[, ]+")));
        String cmdString = cmd.removeFirst();
        Set<String> userNameSet = new HashSet<>(cmd);

        // 普通命令
        if (cmdString.equals(OrzUserCmd.SHOW_PLAYERS.getCmdString())) {
            onlinePlayersInfo(callback);
        } else if (cmdString.equals(OrzUserCmd.SHOW_WHITELIST.getCmdString())) {
            whiteListInfo(callback);
        } else if (cmdString.equals(OrzUserCmd.SHOW_HELP.getCmdString())) {
            callback.accept(OrzUserCmd.helpInfo());
        }
        // 管理员命令
        else if (cmdString.equals(OrzUserCmd.ADD_PLAYER_TO_WHITELIST.getCmdString())) {
            addWhiteListInfo(isAdmin, userNameSet, callback);
        }
        // 管理员命令
        else if (cmdString.equals(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.getCmdString())) {
            removeWhiteListInfo(isAdmin, userNameSet, callback);
        }
        // 管理员命令
        else if (cmdString.equals(OrzUserCmd.BACKUP.getCmdString())) {
            backup(isAdmin, callback);
        }
        // 其它命令，展示帮助信息
        else {
            callback.accept(OrzUserCmd.helpInfo());
        }
    }

    public static String playerDisplayName(Player player) {
        String ret = player.getPlayerProfile().getName();

        if (player.isOp()) {
            ret += "(op)";
        }

        String gameMode = "";
        switch (player.getGameMode()) {
            case CREATIVE -> gameMode = "创造";
            case SURVIVAL -> gameMode = "生存";
            case ADVENTURE -> gameMode = "冒险";
            case SPECTATOR -> gameMode = "观察";
            default -> {
            }
        }
        ret += " " + gameMode + "模式";
        return ret;
    }

    private static void onlinePlayersInfo(Consumer<String> callback) {
        OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
            ArrayList<Player> onlinePlayers = new ArrayList<>();
            Object[] objects = OrzMC.server().getOnlinePlayers().toArray();
            for (Object obj : objects) {
                if (obj instanceof Player p) {
                    onlinePlayers.add(p);
                }
            }
            String tip = String.format("------当前在线(%d/%d)------", onlinePlayers.size(), OrzMC.server().getMaxPlayers());
            StringBuilder msgBuilder = new StringBuilder(tip);

            for (Player p : onlinePlayers) {
                String name = OrzMessageParser.playerDisplayName(p);
                msgBuilder.append("\n").append(name);
            }
            String ret = msgBuilder.toString();
            callback.accept(ret);
        });
    }

    private static void whiteListInfo(Consumer<String> callback) {
        OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
            ArrayList<OfflinePlayer> whiteListPlayers = allWhiteListPlayer();
            StringBuilder whiteListInfo = new StringBuilder(String.format("------当前白名单玩家(%d)------", whiteListPlayers.size()));
            for (OfflinePlayer player : whiteListPlayers) {
                String playerName = player.getName();
                String isOnline = player.isOnline() ? "•" : "◦";
                whiteListInfo.append("\n").append(isOnline).append(" ").append(playerName);
                long lastSeenTimestamp = player.getLastSeen();
                if (lastSeenTimestamp > 0) {
                    String lastSeen = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(lastSeenTimestamp));
                    whiteListInfo.append(" ").append(String.format("%s", lastSeen));
                }
            }
            String ret = whiteListInfo.toString();
            callback.accept(ret);
        });
    }

    private static void addWhiteListInfo(boolean isAdmin, Set<String> userNames, Consumer<String> callback) {
        if (!isAdmin) {
            callback.accept(OrzUserCmd.ADD_PLAYER_TO_WHITELIST.adminPermissionRequiredTip());
            return;
        }
        if (userNames.isEmpty()) {
            callback.accept(OrzUserCmd.ADD_PLAYER_TO_WHITELIST.usageTip());
            return;
        }
        // 主线程上执行白名单操作
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            for (String userName : userNames) {
                OfflinePlayer player = OrzMC.server().getOfflinePlayer(userName);
                if (!player.isWhitelisted()) {
                    player.setWhitelisted(true);
                }
            }
            // 回调异步执行
            OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
                OrzMC.server().reloadWhitelist();
                Set<String> allWhiteListName = allWhiteListPlayerName();
                String message = "------白名单添加------\n";
                if (allWhiteListName.containsAll(userNames)) {
                    message += String.join("\n", userNames.stream().map(name -> "✔︎ ︎" + name).collect(Collectors.toSet()));
                }
                userNames.removeAll(allWhiteListName);
                if (!userNames.isEmpty()) {
                    message += String.join("\n", userNames.stream().map(name -> "✘ " + name).collect(Collectors.toSet()));
                }
                callback.accept(message);
            });
        });
    }

    private static void removeWhiteListInfo(boolean isAdmin, Set<String> userNames, Consumer<String> callback) {
        if (!isAdmin) {
            callback.accept(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.adminPermissionRequiredTip());
            return;
        }
        if (userNames.isEmpty()) {
            callback.accept(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.usageTip());
            return;
        }
        // 主线程上执行白名单操作
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            for (String userName : userNames) {
                OfflinePlayer player = OrzMC.server().getOfflinePlayer(userName);
                if (player.isWhitelisted()) {
                    player.setWhitelisted(false);
                    Player onlinePlayer = OrzMC.server().getPlayer(player.getUniqueId());
                    if (onlinePlayer != null) {
                        onlinePlayer.kick();
                    }
                }
            }
            // 回调异步执行
            OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
                OrzMC.server().reloadWhitelist();
                Set<String> allWhiteListName = allWhiteListPlayerName();
                String message = "------白名单移除------\n";
                if (!allWhiteListName.containsAll(userNames)) {
                    message += String.join("\n", userNames.stream().map(name -> "✔︎ " + name).collect(Collectors.toSet()));
                }
                userNames.retainAll(allWhiteListName);
                if (!userNames.isEmpty()) {
                    message += String.join("\n", userNames.stream().map(name -> "✘ " + name).collect(Collectors.toSet()));
                }
                callback.accept(message);
            });
        });
    }

    private static ArrayList<OfflinePlayer> allWhiteListPlayer() {
        ArrayList<OfflinePlayer> whiteListPlayers = new ArrayList<>(OrzMC.server().getWhitelistedPlayers());
        whiteListPlayers.sort((o1, o2) -> Long.compare(o2.getLastSeen(), o1.getLastSeen()));
        return whiteListPlayers;
    }

    private static Set<String> allWhiteListPlayerName() {
        return allWhiteListPlayer().stream().map(OfflinePlayer::getName).collect(Collectors.toSet());
    }

    private static void backup(boolean isAdmin, Consumer<String> callback) {
        if (!isAdmin) {
            callback.accept(OrzUserCmd.BACKUP.adminPermissionRequiredTip());
            return;
        }
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            callback.accept("开始备份地图");
            OrzMC.server().getOnlinePlayers().forEach(p -> p.kick(Component.text("服务器地图备份中，请稍后再尝试登录。")));
            // TODO: 禁止玩家加入
            OrzUtil.executeConsoleCmd(() -> {
                callback.accept("停止服务器自动地图保存");
            }, "save-off", "save-all");
            // 异步线程执行地图备份
            OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
                File worldContainerDir = OrzMC.server().getWorldContainer();
                File worldBackupDir = new File(OrzMC.plugin().getDataFolder(), "backup");
                if (!worldBackupDir.exists()) {
                    boolean created = worldBackupDir.mkdirs();
                    if (!created) {
                        OrzMC.logger().warning("创建地图备份目录失败: " + worldBackupDir.getAbsolutePath());
                        OrzUtil.executeConsoleCmd(() -> {
                            callback.accept("恢复服务器自动地图保存");
                        }, "save-on");
                        return;
                    }
                }
                Path input = Path.of(worldContainerDir.getAbsolutePath());
                callback.accept("地图目录：" + input);
                Path output = Path.of(worldBackupDir.getAbsolutePath());
                callback.accept("备份目录：" + output);
                long tickTimeThreshold = 300L; // 5分钟阈值
                Optimizer.run(input, output, tickTimeThreshold, false, ProgressMode.Region);
                // TODO: 备份完成/失败回调
                // TODO: zip压缩
                OrzUtil.executeConsoleCmd(() -> {
                    callback.accept("备份地图完成");
                }, "save-on");
            });
        });
    }
}