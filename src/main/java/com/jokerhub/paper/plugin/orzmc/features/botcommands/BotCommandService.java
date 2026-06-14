package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
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
    private final Map<OrzUserCmd, CmdHandler> handlers;
    private WorldMaintenanceService maintenanceService;

    @FunctionalInterface
    private interface CmdHandler {
        void handle(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args);
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
                OrzUserCmd.OPTIMIZE_WORLD, this::handleOptimize);
    }

    public void setMaintenanceService(WorldMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    public void handleMessage(String message, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        parse(message, isAdmin, callback);
    }

    public void parse(String message, Boolean isAdmin, Consumer<MessageEnvelope> callback) {
        BotConfig botConfig = botConfig();
        String promptChar = botConfig.cmdPromptChar();
        if (!message.startsWith(promptChar)) return;

        // $e 使用前缀匹配，需要完整原始消息解析参数
        String execPrefix = promptChar + OrzUserCmd.EXECUTE_CONSOLE_COMMAND.cmdName();
        if (matchesCommandPrefix(message, execPrefix)) {
            executeConsoleCommand(message, execPrefix, isAdmin, callback);
            return;
        }

        // 其他命令：拆分为 tokens
        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(message.split("[, ]+")));
        String cmdString = cmd.remove(0);
        Set<String> userNameSet = new HashSet<>(cmd);

        OrzUserCmd userCmd = lookupEnum(cmdString, promptChar);
        if (userCmd == null) {
            String help = feedbackService.helpInfo(promptChar);
            emit(callback, "command_help", Map.of("help", help), help);
            return;
        }

        CmdHandler handler = handlers.get(userCmd);
        if (handler != null) {
            handler.handle(userCmd, isAdmin, callback, userNameSet);
        }
    }

    private OrzUserCmd lookupEnum(String cmdString, String promptChar) {
        for (OrzUserCmd c : OrzUserCmd.values()) {
            if (c == OrzUserCmd.EXECUTE_CONSOLE_COMMAND) continue;
            if (cmdString.equals(promptChar + c.cmdName())) {
                return c;
            }
        }
        return null;
    }

    private BotConfig botConfig() {
        try {
            return configs.bot();
        } catch (Exception e) {
            server.logger().warning("读取 botConfig 失败，使用默认值: " + e.getMessage());
            return new BotConfig("$", null, null, null);
        }
    }

    private boolean matchesCommandPrefix(String message, String fullCmd) {
        return message.equals(fullCmd)
                || (message.startsWith(fullCmd)
                        && message.length() > fullCmd.length()
                        && Character.isWhitespace(message.charAt(fullCmd.length())));
    }

    // ---- Command handlers (all follow CmdHandler interface) ----

    private void handleShowPlayers(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args) {
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
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args) {
        server.runAsync(() -> {
            try {
                WhitelistConfig whitelistConfig = configs.whitelist();
                WhitelistService svc = WhitelistService.defaultImpl();
                int delayTicks = Math.max(0, whitelistConfig.paginationDelayTicks());
                Integer page = parsePageArg(args);
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

    private Integer parsePageArg(Set<String> args) {
        if (args.isEmpty()) return null;
        String token = args.iterator().next();
        try {
            return Integer.parseInt(token);
        } catch (Exception e) {
            server.logger().warning("白名单页码解析失败: " + token + " - " + e.getMessage());
            return null;
        }
    }

    private void handleShowHelp(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args) {
        String help = feedbackService.helpInfo(botConfig().cmdPromptChar());
        emit(callback, "command_help", Map.of("help", help), help);
    }

    private void handleAddWhitelist(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args) {
        if (!guardWhitelistCommand(cmd, isAdmin, args, callback)) return;
        server.runSync(() -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.addPlayers(server.server(), args);
            emit(callback, "command_whitelist_add_result", Map.of("message", message), message);
        });
    }

    private void handleRemoveWhitelist(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args) {
        if (!guardWhitelistCommand(cmd, isAdmin, args, callback)) return;
        server.runSync(() -> {
            WhitelistService svc = WhitelistService.defaultImpl();
            String message = svc.removePlayers(server.server(), args);
            emit(callback, "command_whitelist_remove_result", Map.of("message", message), message);
        });
    }

    private void handleBackup(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        int retain = maintenance.backupRetentionCount();
        if (maintenanceService != null) {
            maintenanceService.backup(tickTimeThreshold, retain, msg -> emit(callback, "command_backup", Map.of("message", msg), msg));
        }
    }

    private void handleOptimize(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, Set<String> args) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (!guardOptimizeEnabled(callback)) return;
        MaintenanceConfig maintenance = configs.maintenance();
        long tickTimeThreshold = maintenance.optimizeTickTimeThreshold();
        if (maintenanceService != null) {
            maintenanceService.optimize(
                    tickTimeThreshold, msg -> emit(callback, "command_optimize", Map.of("message", msg), msg));
        }
    }

    // ---- Console command (special: needs raw message) ----

    private void executeConsoleCommand(
            String rawMessage, String execPrefix, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (!guardAdminCommand(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, isAdmin, callback)) return;
        String consoleCmd = extractArgs(rawMessage, execPrefix);
        if (consoleCmd.isBlank()) {
            emitUsage(
                    callback,
                    feedbackService.usageTip(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, botConfig().cmdPromptChar()));
            return;
        }
        server.runSync(() -> {
            ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(consoleCmd);
            emit(callback, "command_output", Map.of("message", result.message()), result.message());
        });
    }

    private String extractArgs(String rawMessage, String prefix) {
        if (rawMessage.length() <= prefix.length()) return "";
        return rawMessage.substring(prefix.length()).trim();
    }

    // ---- Whitelist rendering ----

    private void renderWhitelistWithCleanup(
            Consumer<MessageEnvelope> callback, Integer page, int delayTicks,
            WhitelistService svc, WhitelistConfig whitelistConfig) {
        server.runSync(() -> {
            Set<String> removed = svc.cleanupInactivePlayers(server.server(), Math.max(1, whitelistConfig.cleanupInactiveDays()));
            server.runAsync(() -> {
                try {
                    ArrayList<String> updatedLines = new ArrayList<>(svc.buildWhitelistLines(server.server()));
                    BotCommandListFeedbackService.WhitelistHeader headerInfo =
                            listFeedbackService.buildWhitelistHeader(updatedLines.size());
                    if (!removed.isEmpty()) {
                        BotCommandListFeedbackService.CleanupNotice notice = listFeedbackService.buildCleanupNotice(removed);
                        emit(callback, "command_whitelist_cleanup", listFeedbackService.cleanupVars(notice), notice.fallback());
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
        BotCommandListFeedbackService.WhitelistHeader headerInfo = listFeedbackService.buildWhitelistHeader(lines.size());
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
        emitAdminRequired(callback, feedbackService.adminRequiredTip(cmd, botConfig().cmdPromptChar()));
        return false;
    }

    private boolean guardWhitelistCommand(
            OrzUserCmd cmd, boolean isAdmin, Set<String> userNames, Consumer<MessageEnvelope> callback) {
        if (!isAdmin) {
            emitAdminRequired(callback, feedbackService.adminRequiredTip(cmd, botConfig().cmdPromptChar()));
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

    private void emit(Consumer<MessageEnvelope> callback, String templateKey, Map<String, String> vars, String fallback) {
        MessageEnvelope env = configs.renderTemplate(templateKey, vars, fallback);
        callback.accept(env);
    }
}
