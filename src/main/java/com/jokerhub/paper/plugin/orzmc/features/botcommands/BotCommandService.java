package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.paging.Paginator;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.entity.Player;

public final class BotCommandService implements BotInboundHandler {
    private final BotCommandFeedbackService feedbackService = new BotCommandFeedbackService();
    private final BotCommandListFeedbackService listFeedbackService;
    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private WorldMaintenanceService maintenanceService;

    public BotCommandService(ServerFacade server, TypedConfigProvider configs) {
        this.server = server;
        this.configs = configs;
        this.listFeedbackService = new BotCommandListFeedbackService(server, configs);
    }

    public void setMaintenanceService(WorldMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    public void handleMessage(String message, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        parse(message, isAdmin, callback);
    }

    public void parse(String message, Boolean isAdmin, Consumer<MessageEnvelope> callback) {
        TypedConfigs.BotConfig botConfig = botConfig();
        String promptChar = botConfig.cmdPromptChar();
        if (!message.startsWith(promptChar)) return;

        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(message.split("[, ]+")));
        String cmdString = cmd.remove(0);
        Set<String> userNameSet = new HashSet<>(cmd);
        String showPlayersCmd = cmdString(OrzUserCmd.SHOW_PLAYERS, promptChar);
        String showWhitelistCmd = cmdString(OrzUserCmd.SHOW_WHITELIST, promptChar);
        String showHelpCmd = cmdString(OrzUserCmd.SHOW_HELP, promptChar);
        String addWhitelistCmd = cmdString(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, promptChar);
        String removeWhitelistCmd = cmdString(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST, promptChar);
        String backupCmd = cmdString(OrzUserCmd.BACKUP, promptChar);
        String optimizeCmd = cmdString(OrzUserCmd.OPTIMIZE_WORLD, promptChar);
        String executeConsoleCmd = cmdString(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, promptChar);

        if (matchesCommandPrefix(message, executeConsoleCmd)) {
            executeConsoleCommand(message, executeConsoleCmd, isAdmin, callback);
            return;
        }

        if (cmdString.equals(showPlayersCmd)) {
            onlinePlayersInfo(callback);
        } else if (cmdString.equals(showWhitelistCmd)) {
            Integer page = null;
            if (!cmd.isEmpty()) {
                String token = cmd.get(0);
                try {
                    page = Integer.parseInt(token);
                } catch (Exception e) {
                    server.logger().warning("白名单页码解析失败: " + token + " - " + e.getMessage());
                }
            }
            whiteListInfo(callback, page, isAdmin);
        } else if (cmdString.equals(showHelpCmd)) {
            String help = feedbackService.helpInfo(promptChar);
            emit(callback, "command_help", Map.of("help", help), help);
        } else if (cmdString.equals(addWhitelistCmd)) {
            addWhiteListInfo(isAdmin, userNameSet, callback);
        } else if (cmdString.equals(removeWhitelistCmd)) {
            removeWhiteListInfo(isAdmin, userNameSet, callback);
        } else if (cmdString.equals(backupCmd)) {
            backupWorld(isAdmin, callback);
        } else if (cmdString.equals(optimizeCmd)) {
            optimizeWorld(isAdmin, callback);
        } else {
            String help = feedbackService.helpInfo(promptChar);
            emit(callback, "command_help", Map.of("help", help), help);
        }
    }

    private TypedConfigs.BotConfig botConfig() {
        try {
            return configs.bot();
        } catch (Exception e) {
            server.logger().warning("读取 botConfig 失败，使用默认值: " + e.getMessage());
            return new TypedConfigs.BotConfig("$", null, null, null);
        }
    }

    private String cmdString(OrzUserCmd cmd, String promptChar) {
        return promptChar + cmd.cmdName();
    }

    private boolean matchesCommandPrefix(String message, String fullCmd) {
        return message.equals(fullCmd)
                || (message.startsWith(fullCmd)
                        && message.length() > fullCmd.length()
                        && Character.isWhitespace(message.charAt(fullCmd.length())));
    }

    private void onlinePlayersInfo(Consumer<MessageEnvelope> callback) {
        server.runAsync(() -> {
            try {
                ArrayList<Player> onlinePlayers = listFeedbackService.currentOnlinePlayers();
                BotCommandListFeedbackService.OnlineList online = listFeedbackService.buildOnlineList(
                        onlinePlayers, server.server().getMaxPlayers());
                emit(callback, "command_players", listFeedbackService.onlineVars(online), online.fallback());
            } catch (Exception e) {
                server.logger().log(Level.SEVERE, "onlinePlayersInfo 异步任务异常", e);
            }
        });
    }

    private void whiteListInfo(Consumer<MessageEnvelope> callback, Integer page, boolean isAdmin) {
        server.runAsync(() -> {
            try {
                TypedConfigs.WhitelistConfig whitelistConfig = configs.whitelist();
                WhitelistService svc = WhitelistService.defaultImpl();
                int delayTicks = Math.max(0, whitelistConfig.paginationDelayTicks());
                if (isAdmin) {
                    renderWhitelistWithCleanup(callback, page, delayTicks, svc, whitelistConfig);
                } else {
                    renderWhitelistPages(callback, page, delayTicks, svc);
                }
            } catch (Exception e) {
                server.logger().log(Level.SEVERE, "whiteListInfo 异步任务异常", e);
            }
        });
    }

    private void renderWhitelistWithCleanup(
            Consumer<MessageEnvelope> callback,
            Integer page,
            int delayTicks,
            WhitelistService svc,
            TypedConfigs.WhitelistConfig whitelistConfig) {
        server.runSync(() -> {
            java.util.Set<String> removed =
                    svc.cleanupInactivePlayers(server.server(), Math.max(1, whitelistConfig.cleanupInactiveDays()));
            server.runAsync(() -> {
                try {
                    ArrayList<String> updatedLines = new ArrayList<>(svc.buildWhitelistLines(server.server()));
                    BotCommandListFeedbackService.WhitelistHeader updatedHeaderInfo =
                            listFeedbackService.buildWhitelistHeader(updatedLines.size());
                    if (!removed.isEmpty()) {
                        BotCommandListFeedbackService.CleanupNotice cleanupNotice =
                                listFeedbackService.buildCleanupNotice(removed);
                        emitWhitelistCleanup(callback, cleanupNotice);
                    }
                    emitWhitelistPages(callback, updatedHeaderInfo.header(), updatedLines, delayTicks, page);
                } catch (Exception e) {
                    server.logger().log(Level.SEVERE, "renderWhitelistWithCleanup 异步任务异常", e);
                }
            });
        });
    }

    private void renderWhitelistPages(
            Consumer<MessageEnvelope> callback, Integer page, int delayTicks, WhitelistService svc) {
        ArrayList<String> lines = new ArrayList<>(svc.buildWhitelistLines(server.server()));
        BotCommandListFeedbackService.WhitelistHeader headerInfo =
                listFeedbackService.buildWhitelistHeader(lines.size());
        emitWhitelistPages(callback, headerInfo.header(), lines, delayTicks, page);
    }

    private void addWhiteListInfo(boolean isAdmin, Set<String> userNames, Consumer<MessageEnvelope> callback) {
        if (!guardWhitelistCommand(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, isAdmin, userNames, callback)) {
            return;
        }
        server.runSync(() -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.addPlayers(server.server(), userNames);
            emitWhitelistAddResult(callback, message);
        });
    }

    private void removeWhiteListInfo(boolean isAdmin, Set<String> userNames, Consumer<MessageEnvelope> callback) {
        if (!guardWhitelistCommand(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST, isAdmin, userNames, callback)) {
            return;
        }
        server.runSync(() -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.removePlayers(server.server(), userNames);
            emitWhitelistRemoveResult(callback, message);
        });
    }

    private void backupWorld(boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (!guardAdminCommand(OrzUserCmd.BACKUP, isAdmin, callback)) {
            return;
        }
        TypedConfigs.MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        int retain = maintenance.backupRetentionCount();
        if (maintenanceService != null) {
            maintenanceService.backup(tickTimeThreshold, retain, msg -> emitBackup(callback, msg));
        }
    }

    private void optimizeWorld(boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (!guardAdminCommand(OrzUserCmd.OPTIMIZE_WORLD, isAdmin, callback)) {
            return;
        }
        if (!guardOptimizeEnabled(callback)) {
            return;
        }
        TypedConfigs.MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        if (maintenanceService != null) {
            maintenanceService.optimize(tickTimeThreshold, msg -> emitOptimize(callback, msg));
        }
    }

    private void executeConsoleCommand(
            String rawMessage, String executeConsoleCmd, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (!guardAdminCommand(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, isAdmin, callback)) {
            return;
        }
        String consoleCmd = extractCommandArgs(rawMessage, executeConsoleCmd);
        if (consoleCmd.isBlank()) {
            emitUsage(
                    callback,
                    feedbackService.usageTip(
                            OrzUserCmd.EXECUTE_CONSOLE_COMMAND, botConfig().cmdPromptChar()));
            return;
        }
        server.runSync(() -> {
            ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(consoleCmd);
            emit(callback, "command_output", Map.of("message", result.message()), result.message());
        });
    }

    private String extractCommandArgs(String rawMessage, String fullCmd) {
        if (rawMessage.length() <= fullCmd.length()) {
            return "";
        }
        return rawMessage.substring(fullCmd.length()).trim();
    }

    private void emitWhitelistCleanup(
            Consumer<MessageEnvelope> callback, BotCommandListFeedbackService.CleanupNotice notice) {
        emit(callback, "command_whitelist_cleanup", listFeedbackService.cleanupVars(notice), notice.fallback());
    }

    private void emitWhitelistPage(
            Consumer<MessageEnvelope> callback, BotCommandListFeedbackService.WhitelistPage pageInfo) {
        emit(callback, "command_whitelist_page", pageInfo.vars(), pageInfo.fallback());
    }

    private void emitWhitelistAddResult(Consumer<MessageEnvelope> callback, String message) {
        emit(callback, "command_whitelist_add_result", Map.of("message", message), message);
    }

    private void emitWhitelistRemoveResult(Consumer<MessageEnvelope> callback, String message) {
        emit(callback, "command_whitelist_remove_result", Map.of("message", message), message);
    }

    private void emitBackup(Consumer<MessageEnvelope> callback, String message) {
        emit(callback, "command_backup", Map.of("message", message), message);
    }

    private void emitOptimize(Consumer<MessageEnvelope> callback, String message) {
        emit(callback, "command_optimize", Map.of("message", message), message);
    }

    private void emitOptimizeDisabled(Consumer<MessageEnvelope> callback, String message) {
        emit(callback, "command_optimize_disabled", Map.of("message", message), message);
    }

    private boolean guardOptimizeEnabled(Consumer<MessageEnvelope> callback) {
        boolean enabled = false;
        try {
            enabled = configs.maintenance().optimizeEnabled();
        } catch (Exception e) {
            server.logger().warning("读取 optimizeEnabled 配置失败: " + e.getMessage());
        }
        if (!enabled) {
            emitOptimizeDisabled(callback, "地图优化功能已禁用");
            return false;
        }
        return true;
    }

    private boolean guardAdminCommand(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (isAdmin) {
            return true;
        }
        emitAdminRequired(
                callback, feedbackService.adminRequiredTip(cmd, botConfig().cmdPromptChar()));
        return false;
    }

    private boolean guardWhitelistCommand(
            OrzUserCmd cmd, boolean isAdmin, Set<String> userNames, Consumer<MessageEnvelope> callback) {
        if (!isAdmin) {
            emitAdminRequired(
                    callback, feedbackService.adminRequiredTip(cmd, botConfig().cmdPromptChar()));
            return false;
        }
        if (userNames.isEmpty()) {
            emitUsage(callback, feedbackService.usageTip(cmd, botConfig().cmdPromptChar()));
            return false;
        }
        return true;
    }

    private void emitWhitelistPages(
            Consumer<MessageEnvelope> callback, String header, ArrayList<String> lines, int delayTicks, Integer page) {
        Paginator.paginatePages(
                server,
                (pageIndex, total, headerText, body) -> {
                    BotCommandListFeedbackService.WhitelistPage pageInfo =
                            listFeedbackService.buildWhitelistPage(headerText, pageIndex, total, body);
                    emitWhitelistPage(callback, pageInfo);
                },
                header,
                lines,
                delayTicks,
                page);
    }

    private void emitAdminRequired(Consumer<MessageEnvelope> callback, String tip) {
        emit(callback, "command_admin_required", Map.of("message", tip), tip);
    }

    private void emitUsage(Consumer<MessageEnvelope> callback, String tip) {
        emit(callback, "command_usage", Map.of("message", tip), tip);
    }

    private void emit(
            Consumer<MessageEnvelope> callback, String templateKey, Map<String, String> vars, String fallback) {
        MessageEnvelope env = configs.renderTemplate(templateKey, vars, fallback);
        callback.accept(env);
    }
}
