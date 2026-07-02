package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.paging.Paginator;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
    private final Map<OrzUserCmd, CmdHandler> handlers;
    private WorldMaintenanceService maintenanceService;
    private BlacklistService blacklistService;

    @FunctionalInterface
    private interface CmdHandler {
        void handle(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs);
    }

    public BotCommandService(ServerFacade server, TypedConfigProvider configs) {
        this.server = server;
        this.configs = configs;
        this.listFeedbackService = new BotCommandListFeedbackService(server, configs);
        this.handlers = Map.of(
                OrzUserCmd.SHOW_PLAYERS, this::handleShowPlayers,
                OrzUserCmd.SHOW_WHITELIST, this::handleShowWhitelist,
                OrzUserCmd.SHOW_HELP, this::handleShowHelp,
                OrzUserCmd.ADD_PLAYER_TO_WHITELIST, this::handleAddWhitelist,
                OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST, this::handleRemoveWhitelist,
                OrzUserCmd.BACKUP, this::handleBackup,
                OrzUserCmd.OPTIMIZE_WORLD, this::handleOptimize,
                OrzUserCmd.BLACKLIST, this::handleBlacklist,
                OrzUserCmd.EXECUTE_CONSOLE_COMMAND, this::handleExecuteConsoleCommand);
    }

    public void setMaintenanceService(WorldMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    public void setBlacklistService(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @Override
    public void handleMessage(String message, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        parse(message, isAdmin, callback);
    }

    public void parse(String message, Boolean isAdmin, Consumer<MessageEnvelope> callback) {
        BotConfig botConfig = botConfig();
        String promptChar = botConfig.cmdPromptChar();
        if (!message.startsWith(promptChar)) return;

        for (OrzUserCmd userCmd : OrzUserCmd.values()) {
            String cmdPrefix = promptChar + userCmd.cmdName();
            if (matchesCommandPrefix(message, cmdPrefix)) {
                String rawArgs = extractArgs(message, cmdPrefix);

                // $cmd ?：在此指令分发前统一拦截
                if (rawArgs.equals("?") || rawArgs.equals("？")) {
                    String tip = feedbackService.usageTip(userCmd, promptChar);
                    if (!tip.isBlank()) {
                        emitUsage(callback, tip);
                        return;
                    }
                    // 无 usageTip 定义，降级为此指令的正常执行
                }

                CmdHandler handler = handlers.get(userCmd);
                if (handler != null) {
                    handler.handle(userCmd, isAdmin, callback, rawArgs);
                } else {
                    emitHelp(callback);
                }
                return;
            }
        }

        // 无匹配指令
        emitHelp(callback);
    }

    private void emitHelp(Consumer<MessageEnvelope> callback) {
        String help = feedbackService.helpInfo(botConfig().cmdPromptChar());
        emit(callback, "command_help", Map.of("help", help), help);
    }

    private BotConfig botConfig() {
        try {
            return configs.bot();
        } catch (Exception e) {
            server.logger().warning("读取 botConfig 失败，使用默认值: " + e.getMessage());
            return new BotConfig("$", null, null);
        }
    }

    private boolean matchesCommandPrefix(String message, String fullCmd) {
        return message.equals(fullCmd)
                || (message.startsWith(fullCmd)
                        && message.length() > fullCmd.length()
                        && Character.isWhitespace(message.charAt(fullCmd.length())));
    }

    private String extractArgs(String rawMessage, String prefix) {
        if (rawMessage.length() <= prefix.length()) return "";
        return rawMessage.substring(prefix.length()).trim();
    }

    // ---- Command handlers (all follow CmdHandler interface) ----

    private void handleShowPlayers(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
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

    private void handleShowWhitelist(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        server.runAsync(() -> {
            try {
                WhitelistConfig whitelistConfig = configs.whitelist();
                WhitelistService svc = WhitelistService.defaultImpl();
                int delayTicks = Math.max(0, whitelistConfig.paginationDelayTicks());
                Integer page = parsePageArg(rawArgs);
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

    private Integer parsePageArg(String rawArgs) {
        if (rawArgs.isBlank()) return null;
        String token = rawArgs.split("[, ]+")[0];
        try {
            return Integer.parseInt(token);
        } catch (Exception e) {
            server.logger().warning("白名单页码解析失败: " + token + " - " + e.getMessage());
            return null;
        }
    }

    private void handleShowHelp(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        String help = feedbackService.helpInfo(botConfig().cmdPromptChar());
        emit(callback, "command_help", Map.of("help", help), help);
    }

    private void handleAddWhitelist(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        Set<String> userNames = parseArgs(rawArgs);
        if (!guardWhitelistCommand(cmd, isAdmin, userNames, callback)) return;
        server.runSync(() -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.addPlayers(server.server(), userNames);
            emit(callback, "command_whitelist_add_result", Map.of("message", message), message);
        });
    }

    private void handleRemoveWhitelist(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        Set<String> userNames = parseArgs(rawArgs);
        if (!guardWhitelistCommand(cmd, isAdmin, userNames, callback)) return;
        server.runSync(() -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.removePlayers(server.server(), userNames);
            emit(callback, "command_whitelist_remove_result", Map.of("message", message), message);
        });
    }

    private void handleBackup(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        int retain = maintenance.backupRetentionCount();
        if (maintenanceService != null) {
            maintenanceService.backup(
                    tickTimeThreshold, retain, msg -> emit(callback, "command_backup", Map.of("message", msg), msg));
        }
    }

    private void handleOptimize(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (!guardOptimizeEnabled(callback)) return;
        MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        if (maintenanceService != null) {
            maintenanceService.optimize(
                    tickTimeThreshold, msg -> emit(callback, "command_optimize", Map.of("message", msg), msg));
        }
    }

    // ---- Console command ----

    private void handleExecuteConsoleCommand(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (rawArgs.isBlank()) {
            emitUsage(
                    callback,
                    feedbackService.usageTip(
                            OrzUserCmd.EXECUTE_CONSOLE_COMMAND, botConfig().cmdPromptChar()));
            return;
        }
        server.runSync(() -> {
            ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(rawArgs);
            emit(callback, "command_output", Map.of("message", result.message()), result.message());
        });
    }

    // ---- Blacklist command ----

    private void handleBlacklist(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (blacklistService == null) {
            emit(callback, "command_blacklist_error", Map.of("message", "黑名单服务不可用"), "黑名单服务不可用");
            return;
        }
        if (rawArgs.isEmpty()) {
            List<String> patterns = blacklistService.getPatterns();
            if (patterns.isEmpty()) {
                emit(callback, "command_blacklist_list", Map.of("patterns", "黑名单为空"), "黑名单为空");
            } else {
                emit(
                        callback,
                        "command_blacklist_list",
                        Map.of("patterns", String.join("\n", patterns)),
                        String.join("\n", patterns));
            }
            return;
        }
        if (rawArgs.startsWith("-")) {
            blacklistService.remove(rawArgs.substring(1));
            emit(
                    callback,
                    "command_blacklist_remove",
                    Map.of("message", "已移除: " + rawArgs.substring(1)),
                    "已移除: " + rawArgs.substring(1));
        } else {
            blacklistService.add(rawArgs);
            emit(callback, "command_blacklist_add", Map.of("message", "已添加: " + rawArgs), "已添加: " + rawArgs);
        }
    }

    // ---- Helper ----

    private Set<String> parseArgs(String rawArgs) {
        if (rawArgs.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(rawArgs.split("[, ]+")));
    }

    // ---- Whitelist rendering ----

    private void renderWhitelistWithCleanup(
            Consumer<MessageEnvelope> callback,
            Integer page,
            int delayTicks,
            WhitelistService svc,
            WhitelistConfig whitelistConfig) {
        server.runSync(() -> {
            Set<String> removed =
                    svc.cleanupInactivePlayers(server.server(), Math.max(1, whitelistConfig.cleanupInactiveDays()));
            server.runAsync(() -> {
                try {
                    ArrayList<String> updatedLines = new ArrayList<>(svc.buildWhitelistLines(server.server()));
                    BotCommandListFeedbackService.WhitelistHeader headerInfo =
                            listFeedbackService.buildWhitelistHeader(updatedLines.size());
                    if (!removed.isEmpty()) {
                        BotCommandListFeedbackService.CleanupNotice notice =
                                listFeedbackService.buildCleanupNotice(removed);
                        emit(
                                callback,
                                "command_whitelist_cleanup",
                                listFeedbackService.cleanupVars(notice),
                                notice.fallback());
                    }
                    emitWhitelistPages(callback, headerInfo.header(), updatedLines, delayTicks, page);
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

    private void emitWhitelistPages(
            Consumer<MessageEnvelope> callback, String header, ArrayList<String> lines, int delayTicks, Integer page) {
        Paginator.paginatePages(
                server,
                (pageIndex, total, headerText, body) -> {
                    BotCommandListFeedbackService.WhitelistPage pageInfo =
                            listFeedbackService.buildWhitelistPage(headerText, pageIndex, total, body);
                    emit(callback, "command_whitelist_page", pageInfo.vars(), pageInfo.fallback());
                },
                header,
                lines,
                delayTicks,
                page);
    }

    // ---- Guards ----

    private boolean guardAdminCommand(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (isAdmin) return true;
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

    private boolean guardOptimizeEnabled(Consumer<MessageEnvelope> callback) {
        boolean enabled = false;
        try {
            enabled = configs.maintenance().optimizeEnabled();
        } catch (Exception e) {
            server.logger().warning("读取 optimizeEnabled 配置失败: " + e.getMessage());
        }
        if (!enabled) {
            emit(callback, "command_optimize_disabled", Map.of("message", "地图优化功能已禁用"), "地图优化功能已禁用");
            return false;
        }
        return true;
    }

    // ---- Emitters ----

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
