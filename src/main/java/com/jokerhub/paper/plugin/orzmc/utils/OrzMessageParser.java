package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.orzmc.world.Optimizer;
import com.jokerhub.orzmc.world.ProgressMode;
import com.jokerhub.orzmc.world.ProgressStage;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.file.Files.readAttributes;

public class OrzMessageParser {
    public static volatile boolean isBackupRunning = false;

    public static void parse(String message, Boolean isAdmin, Consumer<String> callback) {

        if (!OrzUserCmd.isValidCmd(message)) return;

        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(message.split("[, ]+")));
        String cmdString = cmd.removeFirst();
        Set<String> userNameSet = new HashSet<>(cmd);

        // 普通命令
        if (cmdString.equals(OrzUserCmd.SHOW_PLAYERS.getCmdString())) {
            onlinePlayersInfo(callback);
        } else if (cmdString.equals(OrzUserCmd.SHOW_WHITELIST.getCmdString())) {
            Integer page = null;
            if (!cmd.isEmpty()) {
                String token = cmd.getFirst();
                try {
                    page = Integer.parseInt(token);
                } catch (Exception ignored) {
                }
            }
            whiteListInfo(callback, page);
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

    private static void whiteListInfo(Consumer<String> callback, Integer page) {
        OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
            ArrayList<OfflinePlayer> whiteListPlayers = allWhiteListPlayer();
            String header = String.format("------当前白名单玩家(%d)------", whiteListPlayers.size());
            ArrayList<String> lines = new ArrayList<>();
            for (OfflinePlayer player : whiteListPlayers) {
                String playerName = player.getName();
                String isOnline = player.isOnline() ? "•" : "◦";
                StringBuilder line = new StringBuilder().append(isOnline).append(" ").append(playerName);
                long lastSeenTimestamp = player.getLastSeen();
                if (lastSeenTimestamp > 0) {
                    String lastSeen = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(lastSeenTimestamp));
                    line.append(" ").append(lastSeen);
                }
                lines.add(line.toString());
            }
            ArrayList<String> chunks = buildChunks(lines);
            int total = chunks.size();
            if (total == 0) {
                callback.accept(header + "\n" + "(暂无白名单玩家)");
                return;
            }
            if (page != null) {
                int idx = Math.max(1, Math.min(page, total)) - 1;
                String pageHeader = header + "\n第" + (idx + 1) + "/" + total + "页";
                String body = chunks.get(idx);
                callback.accept(pageHeader + "\n" + body);
            } else {
                for (int i = 0; i < total; i++) {
                    String pageHeader = header + "\n第" + (i + 1) + "/" + total + "页";
                    String body = chunks.get(i);
                    callback.accept(pageHeader + "\n" + body);
                }
            }
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

    private static ArrayList<String> buildChunks(ArrayList<String> lines) {
        ArrayList<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (current.isEmpty()) {
                current.append(line);
            } else if (current.length() + 1 + line.length() <= 1800) {
                current.append("\n").append(line);
            } else {
                chunks.add(current.toString());
                current = new StringBuilder(line);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
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
        // 防止一次备份过程中，触发第二次备份执行
        if (isBackupRunning) {
            callback.accept("正在备份中，请稍候...");
            return;
        }
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            // 设置备份全局标记，防止新玩家进服
            isBackupRunning = true;
            // 踢掉当前在线玩家
            OrzMC.server().getOnlinePlayers().forEach(p -> p.kick(Component.text("服务器地图备份中，请稍后再尝试登录。")));
            // 停止服务器地图自动保存功能，防止地图文件在备份过程中改变
            OrzUtil.executeConsoleCmd(() -> callback.accept("停止服务器自动地图保存功能"), "save-off", "save-all");
            // 异步线程执行地图备份
            OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
                File worldContainerDir = OrzMC.server().getWorldContainer();
                File worldBackupDir = new File(OrzMC.plugin().getDataFolder(), "backup");
                if (!worldBackupDir.exists()) {
                    boolean created = worldBackupDir.mkdirs();
                    if (!created) {
                        OrzMC.logger().warning("创建地图备份目录失败: " + worldBackupDir.getAbsolutePath());
                        OrzUtil.executeConsoleCmd(() -> callback.accept("恢复服务器自动地图保存功能"), "save-on");
                        return;
                    }
                }
                Path input = Path.of(worldContainerDir.getAbsolutePath());
                callback.accept("地图目录：" + input);
                File worldBackupTempDir = new File(worldBackupDir, "tempDir");
                Path output = Path.of(worldBackupTempDir.getAbsolutePath());
                callback.accept("备份目录：" + worldBackupDir);
                long tickTimeThreshold = 300L; // 5分钟阈值
                callback.accept("正在备份地图，请稍等......");
                Optimizer.run(input, output, tickTimeThreshold, false, ProgressMode.Region, true, false, true, true, 100, 1000, optimizeError -> {
                    OrzMC.logger().warning(optimizeError.toString());
                    callback.accept("地图备份失败");
                    return null;
                }, progressEvent -> {
                    Long current = progressEvent.getCurrent();
                    Long total = progressEvent.getTotal();
                    if (current == null || total == null || current <= 0 || total <= 0) {
                        return null;
                    }
                    int percent = (int) Math.ceil(progressEvent.getCurrent() * 100.0 / progressEvent.getTotal());
                    OrzMC.logger().info("地图备份进度：" + percent + "%");
                    if (progressEvent.getStage() == ProgressStage.Done && Objects.equals(progressEvent.getCurrent(), progressEvent.getTotal())) {
                        callback.accept("地图备份完成");
                    }
                    return null;
                });
                // 删除过老的地图备份
                pruneOldZips(worldBackupDir);
                OrzUtil.executeConsoleCmd(() -> callback.accept("恢复服务器自动地图保存功能"), "save-on");
                // 备份完成后，恢复服务器，允许玩家进服
                isBackupRunning = false;
            });
        });
    }

    private static void pruneOldZips(File backupDir) {
        int retain = OrzMC.plugin().getConfig().getInt("backup_retention_count", 10);
        if (retain <= 0) retain = 10;
        File[] zips = backupDir.listFiles(f -> f.isFile() && f.getName().endsWith(".zip"));
        if (zips == null || zips.length <= retain) return;
        Arrays.sort(zips, (a, b) -> {
            try {
                BasicFileAttributes ab = readAttributes(a.toPath(), BasicFileAttributes.class);
                BasicFileAttributes bb = readAttributes(b.toPath(), BasicFileAttributes.class);
                return Long.compare(bb.creationTime().toMillis(), ab.creationTime().toMillis());
            } catch (Exception e) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = retain; i < zips.length; i++) {
            try {
                boolean deleted = zips[i].delete();
                if (!deleted) {
                    OrzMC.logger().warning("删除旧备份失败: " + zips[i].getName());
                }
            } catch (Exception e) {
                OrzMC.logger().severe("清理旧备份异常: " + e);
            }
        }
    }
}
