package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistService;
import com.jokerhub.paper.plugin.orzmc.infra.paging.Paginator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class OrzMessageParser {

    public static void parse(String message, Boolean isAdmin, Consumer<String> callback) {

        if (!OrzUserCmd.isValidCmd(message)) return;

        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(message.split("[, ]+")));
        String cmdString = cmd.remove(0);
        Set<String> userNameSet = new HashSet<>(cmd);

        // 普通命令
        if (cmdString.equals(OrzUserCmd.SHOW_PLAYERS.getCmdString())) {
            onlinePlayersInfo(callback);
        } else if (cmdString.equals(OrzUserCmd.SHOW_WHITELIST.getCmdString())) {
            Integer page = null;
            if (!cmd.isEmpty()) {
                String token = cmd.get(0);
                try {
                    page = Integer.parseInt(token);
                } catch (Exception ignored) {
                }
            }
            whiteListInfo(callback, page, isAdmin);
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
            backupWorld(isAdmin, callback);
        }
        // 管理员命令
        else if (cmdString.equals(OrzUserCmd.OPTIMIZE_WORLD.getCmdString())) {
            optimizeWorld(isAdmin, callback);
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
            default -> {}
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
            String tip = String.format(
                    "------当前在线(%d/%d)------",
                    onlinePlayers.size(), OrzMC.server().getMaxPlayers());
            StringBuilder msgBuilder = new StringBuilder(tip);

            for (Player p : onlinePlayers) {
                String name = OrzMessageParser.playerDisplayName(p);
                msgBuilder.append("\n").append(name);
            }
            String ret = msgBuilder.toString();
            callback.accept(ret);
        });
    }

    private static void whiteListInfo(Consumer<String> callback, Integer page, boolean isAdmin) {
        OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
            FileConfiguration wlCfg = OrzMC.plugin().configManager.getConfig("whitelist");
            WhitelistService svc = WhitelistService.defaultImpl();
            ArrayList<String> lines = new ArrayList<>(svc.buildWhitelistLines(OrzMC.server()));
            String header = String.format("------当前白名单玩家(%d)------", lines.size());
            int delayTicks = Math.max(0, wlCfg.getInt("pagination_delay_ticks", 5));
            if (isAdmin) {
                OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
                    java.util.Set<String> removed = svc.cleanupInactivePlayers(
                            OrzMC.server(), Math.max(1, wlCfg.getInt("cleanup_inactive_days", 90)));
                    OrzMC.server().getScheduler().runTaskAsynchronously(OrzMC.plugin(), () -> {
                        ArrayList<String> updatedLines = new ArrayList<>(svc.buildWhitelistLines(OrzMC.server()));
                        String updatedHeader = String.format("------当前白名单玩家(%d)------", updatedLines.size());
                        if (!removed.isEmpty()) {
                            String removedMsg = "------白名单清理------\n"
                                    + String.join(
                                            "\n",
                                            removed.stream()
                                                    .map(name -> "✔︎ " + name)
                                                    .collect(java.util.stream.Collectors.toSet()));
                            callback.accept(removedMsg);
                        }
                        Paginator.paginate(callback, updatedHeader, updatedLines, delayTicks, page);
                    });
                });
            } else {
                Paginator.paginate(callback, header, lines, delayTicks, page);
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
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.addPlayers(OrzMC.server(), userNames);
            callback.accept(message);
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
        OrzMC.server().getScheduler().runTask(OrzMC.plugin(), () -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.removePlayers(OrzMC.server(), userNames);
            callback.accept(message);
        });
    }

    private static void backupWorld(boolean isAdmin, Consumer<String> callback) {
        if (!isAdmin) {
            callback.accept(OrzUserCmd.BACKUP.adminPermissionRequiredTip());
            return;
        }
        long tickTimeThreshold =
                OrzMC.plugin().configManager.getConfig("maintenance").getLong("optimize_tick_time_threshold", 300L);
        int retain = OrzMC.plugin().configManager.getConfig("maintenance").getInt("backup_retention_count", 10);
        WorldMaintenanceService svc = new WorldMaintenanceService();
        svc.backup(tickTimeThreshold, retain, callback);
    }

    public static void optimizeWorld(boolean isAdmin, Consumer<String> callback) {
        if (!isAdmin) {
            callback.accept(OrzUserCmd.OPTIMIZE_WORLD.adminPermissionRequiredTip());
            return;
        }
        boolean enabled = false;
        try {
            enabled = OrzMC.plugin().configManager.getConfig("maintenance").getBoolean("optimize_enabled");
        } catch (Exception ignored) {
        }
        if (!enabled) {
            callback.accept("地图优化功能已禁用");
            return;
        }
        long tickTimeThreshold =
                OrzMC.plugin().configManager.getConfig("maintenance").getLong("optimize_tick_time_threshold", 300L);
        WorldMaintenanceService svc = new WorldMaintenanceService();
        svc.optimize(tickTimeThreshold, callback);
    }

    public static void optimizeWorldOnShutdown(Consumer<String> callback) {
        boolean enabled = false;
        try {
            enabled = OrzMC.plugin().configManager.getConfig("maintenance").getBoolean("optimize_enabled");
        } catch (Exception ignored) {
        }
        if (!enabled) {
            return;
        }
        long tickTimeThreshold =
                OrzMC.plugin().configManager.getConfig("maintenance").getLong("optimize_tick_time_threshold", 300L);
        new WorldMaintenanceService().optimizeOnShutdown(tickTimeThreshold);
    }
}
