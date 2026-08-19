package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandAuditService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardService;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.logging.LogCaptureService;
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
    private BotCommandListFeedbackService listFeedbackService;
    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final Map<OrzUserCmd, CmdHandler> handlers;
    private WorldMaintenanceService maintenanceService;
    private BlacklistService blacklistService;
    private com.jokerhub.paper.plugin.orzmc.features.review.ReviewService reviewService;
    private com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService;
    private LogCaptureService logCaptureService;
    /** 危险命令判定核心（安全加固 P0-5）：$e 控制台执行前过 guard。未注入时放行（向后兼容测试）。 */
    private CommandGuardService commandGuardService;
    /** 命令审计日志（安全加固 P0-4）：$e 路径记录 bot 来源审计。 */
    private CommandAuditService commandAuditService;

    /** $e 日志收集窗口：40 tick ≈ 2 秒（按 20 TPS 推算，覆盖大多数插件异步命令输出）。 */
    private static final long CONSOLE_OUTPUT_COLLECT_TICKS = 40L;

    /** $e 输出最大行数，超过截断防刷群。 */
    private static final int CONSOLE_OUTPUT_MAX_LINES = 30;

    @FunctionalInterface
    private interface CmdHandler {
        /** 5 参入口：cmd/admin/发送者身份/回调/原始参数。 */
        void handle(
                OrzUserCmd cmd, boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback, String rawArgs);

        /** 4 参便捷入口（senderName=null），兼容测试与无身份调用。 */
        default void handle(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
            handle(cmd, isAdmin, null, callback, rawArgs);
        }
    }

    public BotCommandService(ServerFacade server, TypedConfigProvider configs) {
        this.server = server;
        this.configs = configs;
        this.listFeedbackService = new BotCommandListFeedbackService(server, configs);
        this.handlers = Map.ofEntries(
                Map.entry(OrzUserCmd.SHOW_PLAYERS, (c, a, s, cb, r) -> handleShowPlayers(c, a, cb, r)),
                Map.entry(OrzUserCmd.SHOW_WHITELIST, (c, a, s, cb, r) -> handleShowWhitelist(c, a, cb, r)),
                Map.entry(OrzUserCmd.SHOW_HELP, (c, a, s, cb, r) -> handleShowHelp(c, a, cb, r)),
                Map.entry(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, (c, a, s, cb, r) -> handleAddWhitelist(c, a, cb, r)),
                Map.entry(
                        OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST,
                        (c, a, s, cb, r) -> handleRemoveWhitelist(c, a, cb, r)),
                Map.entry(OrzUserCmd.BACKUP, (c, a, s, cb, r) -> handleBackup(c, a, cb, r)),
                Map.entry(OrzUserCmd.OPTIMIZE_WORLD, (c, a, s, cb, r) -> handleOptimize(c, a, cb, r)),
                Map.entry(OrzUserCmd.BLACKLIST, (c, a, s, cb, r) -> handleBlacklist(c, a, cb, r)),
                Map.entry(OrzUserCmd.REVIEW, (c, a, s, cb, r) -> handleReview(c, a, s, cb, r)),
                Map.entry(OrzUserCmd.PERMISSION, (c, a, s, cb, r) -> handlePermission(c, a, cb, r)),
                Map.entry(
                        OrzUserCmd.EXECUTE_CONSOLE_COMMAND,
                        (c, a, s, cb, r) -> handleExecuteConsoleCommand(c, a, s, cb, r)));
    }

    public void setMaintenanceService(WorldMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    public void setBlacklistService(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    public void setReviewService(com.jokerhub.paper.plugin.orzmc.features.review.ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    public void setRankService(com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService) {
        this.rankService = rankService;
        // 重建列表反馈服务以注入 rankService（在线列表显示权限组）
        com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter formatter =
                new com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter();
        formatter.setRankService(rankService);
        this.listFeedbackService = new BotCommandListFeedbackService(server, configs, formatter);
    }

    /** 注入日志窗口收集服务（$e 命令输出兜底）。未注入时 $e 退化为仅返回执行状态。 */
    public void setLogCaptureService(LogCaptureService logCaptureService) {
        this.logCaptureService = logCaptureService;
    }

    /** 注入危险命令 guard 与审计（安全加固 P0-5）。未注入时 $e 按原路径直接执行（测试向后兼容）。 */
    public void setCommandGuard(CommandGuardService commandGuardService, CommandAuditService commandAuditService) {
        this.commandGuardService = commandGuardService;
        this.commandAuditService = commandAuditService;
    }

    @Override
    public void handleMessage(String message, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        parse(message, isAdmin, null, callback);
    }

    @Override
    public void handleMessage(String message, boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback) {
        parse(message, isAdmin, senderName, callback);
    }

    public void parse(String message, Boolean isAdmin, Consumer<MessageEnvelope> callback) {
        parse(message, isAdmin, null, callback);
    }

    public void parse(String message, Boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback) {
        BotConfig botConfig = botConfig();
        String promptChar = botConfig.cmdPromptChar();
        if (!message.startsWith(promptChar)) return;

        for (OrzUserCmd userCmd : OrzUserCmd.values()) {
            String cmdPrefix = promptChar + userCmd.cmdName();
            if (matchesCommandPrefix(message, cmdPrefix)) {
                // 全角空格（U+3000）归一化为半角再 trim——Java String.trim() 不处理 U+3000，
                // 否则 "$b　?"（全角空格分隔）会绕过 ? 拦截直接触发备份/优化等重量级命令
                String rawArgs =
                        extractArgs(message, cmdPrefix).replace('\u3000', ' ').trim();

                // $cmd ?：在此指令分发前统一拦截。
                // 前缀匹配（$b ?x / $b ?? / $b ? 2 均视为帮助请求），防误触重量级命令；
                // $e 特判精确匹配——控制台命令本身可能以 ? 开头（如 "$e ?list"）
                boolean helpQuery = userCmd == OrzUserCmd.EXECUTE_CONSOLE_COMMAND
                        ? rawArgs.equals("?") || rawArgs.equals("？")
                        : rawArgs.startsWith("?") || rawArgs.startsWith("？");
                if (helpQuery) {
                    String tip = feedbackService.usageTip(userCmd, promptChar);
                    if (!tip.isBlank()) {
                        emitUsage(callback, tip);
                        return;
                    }
                    // 防御：无 usageTip 定义时发总帮助，绝不降级为执行命令
                    // （避免 $b ? / $o ? 等误触发备份/优化等重量级操作）
                    emitHelp(callback);
                    return;
                }

                CmdHandler handler = handlers.get(userCmd);
                if (handler != null) {
                    handler.handle(userCmd, isAdmin, senderName, callback, rawArgs);
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
                WhitelistService svc = WhitelistService.defaultImpl(server.plugin());
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
            WhitelistService svc = WhitelistService.defaultImpl(server.plugin());
            String message = svc.addPlayers(server.server(), userNames);
            emit(callback, "command_whitelist_add_result", Map.of("message", message), message);
        });
    }

    private void handleRemoveWhitelist(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        Set<String> userNames = parseArgs(rawArgs);
        if (!guardWhitelistCommand(cmd, isAdmin, userNames, callback)) return;
        server.runSync(() -> {
            WhitelistService svc = WhitelistService.defaultImpl(server.plugin());
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
            OrzUserCmd cmd, boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (rawArgs.isBlank()) {
            emitUsage(
                    callback,
                    feedbackService.usageTip(
                            OrzUserCmd.EXECUTE_CONSOLE_COMMAND, botConfig().cmdPromptChar()));
            return;
        }
        // 安全加固 P0-5：$e 控制台执行前过 guard。BLOCK → 拦截 + 审计 blocked，不执行；
        // WARN/ALLOW → 审计 executed 后照常执行。guard 未注入时直接执行（测试向后兼容）。
        String auditSender = senderName == null ? CommandAuditService.SOURCE_BOT : senderName;
        CommandGuardService.GuardDecision decision = commandGuardService == null
                ? CommandGuardService.GuardDecision.allow()
                : commandGuardService.guard(rawArgs);
        if (decision.blocked()) {
            if (commandAuditService != null) {
                commandAuditService.record(CommandAuditService.SOURCE_BOT, auditSender, rawArgs, true);
            }
            emit(callback, "command_output", Map.of("message", decision.reason()), decision.reason());
            return;
        }
        if (commandAuditService != null) {
            commandAuditService.record(CommandAuditService.SOURCE_BOT, auditSender, rawArgs, false);
        }
        server.runSync(() -> {
            if (logCaptureService == null) {
                // 未注入日志窗口服务：退化为仅返回执行状态
                ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(rawArgs);
                emit(callback, "command_output", Map.of("message", result.message()), result.message());
                return;
            }
            // 先取水位再执行，命令执行期间的日志行才能落入窗口
            long watermark = logCaptureService.watermark();
            ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(rawArgs);
            // 延迟一个收集窗口后取日志增量：覆盖异步命令输出（LuckPerms 等回调式输出）。
            // 日志窗口是「尽力而为」兜底：窗口内可能混入服务器其他活动日志（已过滤
            // 命令回显/玩家聊天），缓冲溢出时输出头部提示可能缺失
            server.runLater(
                    () -> {
                        List<String> windowLogLines = logCaptureService.drainSince(watermark);
                        String assembled = CommandOutputAssembler.assemble(
                                result.outputLines(), windowLogLines, CONSOLE_OUTPUT_MAX_LINES);
                        // 缺口检测独立于输出内容：即使窗口内有效行全被驱逐/过滤也要提示
                        String message;
                        if (logCaptureService.hasGapSince(watermark)) {
                            message = assembled.isEmpty() ? "⚠️ 日志缓冲溢出，输出可能不完整" : "⚠️ 日志缓冲溢出，输出可能不完整\n" + assembled;
                        } else {
                            message = assembled.isEmpty() ? result.message() : assembled;
                        }
                        emit(callback, "command_output", Map.of("message", message), message);
                    },
                    CONSOLE_OUTPUT_COLLECT_TICKS);
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

    // ---- Permission command ($p u|d) ----

    /** $p u <玩家> / $p d <玩家> — 权限升级/降级一级（钳位：default→member→builder→admin）。 */
    private void handlePermission(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        java.util.logging.Logger.getLogger("OrzMC.BotCmd")
                .info("handlePermission: args=[" + rawArgs + "] isAdmin=" + isAdmin);
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (rankService == null) {
            emit(callback, "command_review_error", Map.of("message", "权限服务不可用"), "权限服务不可用");
            return;
        }
        if (!rankService.isLuckPermsAvailable()) {
            emit(
                    callback,
                    "command_review_error",
                    Map.of("message", "未检测到 LuckPerms，权限管理功能不可用"),
                    "未检测到 LuckPerms，权限管理功能不可用");
            return;
        }
        if (rawArgs.isBlank()) {
            emitUsage(
                    callback,
                    feedbackService.usageTip(OrzUserCmd.PERMISSION, botConfig().cmdPromptChar()));
            return;
        }
        String[] parts = rawArgs.split("\\s+", 2);
        String sub = parts[0].toLowerCase();
        String playerName = parts.length > 1 ? parts[1].trim() : "";
        if (playerName.isBlank()) {
            emitUsage(
                    callback,
                    feedbackService.usageTip(OrzUserCmd.PERMISSION, botConfig().cmdPromptChar()));
            return;
        }
        boolean upgrade;
        switch (sub) {
            case "u", "up" -> upgrade = true;
            case "d", "down" -> upgrade = false;
            default -> {
                emitUsage(
                        callback,
                        feedbackService.usageTip(
                                OrzUserCmd.PERMISSION, botConfig().cmdPromptChar()));
                return;
            }
        }
        var playerId = rankService.resolvePlayerId(playerName);
        if (playerId == null) {
            emit(callback, "command_review_error", Map.of("message", "找不到玩家: " + playerName), "找不到玩家: " + playerName);
            return;
        }
        final var id = playerId;
        // 异步升降级：LP 操作（loadUser/saveUser 等待）在非服务器线程执行，绝不 runSync+join
        // （服务器调度线程同步等待 LP future 会自锁超时，Folia LP 适配器行为）
        java.util.concurrent.CompletableFuture<String> future =
                upgrade ? rankService.promoteAsync(id) : rankService.demoteAsync(id);
        future.whenComplete((target, err) -> {
            if (err != null) {
                emit(
                        callback,
                        "command_review_error",
                        Map.of("message", playerName + " 权限操作异常（详见服务器日志）。"),
                        playerName + " 权限操作异常（详见服务器日志）。");
                return;
            }
            if (target == null) {
                emit(
                        callback,
                        "command_review_error",
                        Map.of(
                                "message",
                                playerName
                                        + (upgrade
                                                ? " 无法升级：已达最高等级或权限数据异常（详见服务器日志）。"
                                                : " 无法降级：已达最低等级或权限数据异常（详见服务器日志）。")),
                        playerName + (upgrade ? " 无法升级：已达最高等级或权限数据异常（详见服务器日志）。" : " 无法降级：已达最低等级或权限数据异常（详见服务器日志）。"));
            } else {
                emit(
                        callback,
                        "command_review_result",
                        Map.of(
                                "message",
                                "已将 " + playerName + (upgrade ? " 升级为" : " 降级为") + RankService.groupDisplayName(target)
                                        + "。"),
                        "已将 " + playerName + (upgrade ? " 升级为" : " 降级为") + RankService.groupDisplayName(target) + "。");
            }
        });
    }

    // ---- Review command ($v l|y|n) ----

    private void handleReview(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        handleReview(cmd, isAdmin, null, callback, rawArgs);
    }

    private void handleReview(
            OrzUserCmd cmd, boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (reviewService == null) {
            emit(callback, "command_review_error", Map.of("message", "审核服务不可用"), "审核服务不可用");
            return;
        }
        if (rawArgs.isBlank()) {
            emitReviewUsage(callback);
            return;
        }
        String[] parts = rawArgs.split("\\s+", 2);
        String sub = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1].trim() : "";
        switch (sub) {
            case "l" -> handleReviewList(callback, rest);
            case "y", "yes" -> handleReviewDecision(callback, rest, true, senderName);
            case "n", "no" -> handleReviewDecision(callback, rest, false, senderName);
            default -> emitReviewUsage(callback);
        }
    }

    private void handleReviewList(Consumer<MessageEnvelope> callback, String pageArg) {
        var pending = reviewService.listPending();
        if (pending.isEmpty()) {
            emit(callback, "command_review_list_empty", Map.of(), "当前没有待审核的申请。");
            return;
        }
        Integer page = parsePageArg(pageArg);
        List<String> lines = new ArrayList<>();
        for (var r : pending) {
            String typeName =
                    reviewService.typeById(r.typeId()).map(t -> t.displayName()).orElse(r.typeId());
            String playerName = playerNameOf(r);
            String group = rankService == null
                    ? ""
                    : "（当前组：" + RankService.groupDisplayName(rankService.currentGroup(r.applicantId())) + "）";
            String summary = reviewService
                    .typeById(r.typeId())
                    .map(t -> t.summarize(r.data()))
                    .orElse("");
            lines.add(
                    "[%s] %s%s：%s（%s 提交）".formatted(typeName, playerName, group, summary, relativeTime(r.createdAt())));
        }
        Paginator.paginate(
                server,
                text -> emit(callback, "command_review_list", Map.of("message", text), text),
                "------待审核申请------",
                lines,
                5,
                page);
    }

    private void handleReviewDecision(
            Consumer<MessageEnvelope> callback, String rest, boolean approved, String senderName) {
        if (rest.isBlank()) {
            emitReviewUsage(callback);
            return;
        }
        // 审核人：优先群发送者身份（网关透传昵称）；未透传时兜底「群管理员」
        String reviewer = (senderName == null || senderName.isBlank()) ? "群管理员" : senderName;
        // 支持：$v y <玩家>  或  $v y <typeId> <玩家>
        String[] parts = rest.split("\\s+", 2);
        String first = parts[0];
        String second = parts.length > 1 ? parts[1].trim() : "";

        // 定位 + 发起审核在同步调度线程执行（Bukkit.getOfflinePlayer 需全局线程），
        // 但不 join 等待——审核通过时的授权（LP 晋升）在非服务器线程执行，结果经 CF
        // 回调发出（落状态回同步线程）。服务器调度线程绝不同步等待 LP future（自锁超时）。
        final boolean byType = reviewService.typeById(first).isPresent() && !second.isBlank();
        final String playerOrType = first;
        final String playerName = second;
        server.runSync(() -> {
            try {
                java.util.concurrent.CompletableFuture<
                                com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result>
                        future;
                if (byType) {
                    var request = reviewService.pendingFor(playerOrType, playerName);
                    if (request.isEmpty()) {
                        future = java.util.concurrent.CompletableFuture.completedFuture(
                                ReviewService.Result.fail("找不到待审申请: " + rest));
                    } else {
                        future = reviewService.review(request.get().id(), approved, reviewer);
                    }
                } else {
                    future = reviewService.reviewByApplicantName(playerOrType, approved, reviewer);
                }
                future.whenComplete((result, err) -> {
                    if (err != null) {
                        result = ReviewService.Result.fail(
                                "审核处理异常: " + (err.getMessage() == null ? "未知错误" : err.getMessage()));
                    }
                    emit(
                            callback,
                            result.success() ? "command_review_result" : "command_review_error",
                            Map.of("message", result.message()),
                            result.message());
                });
            } catch (Throwable t) {
                emit(
                        callback,
                        "command_review_error",
                        Map.of("message", "审核处理异常: " + (t.getMessage() == null ? "未知错误" : t.getMessage())),
                        "审核处理异常: " + (t.getMessage() == null ? "未知错误" : t.getMessage()));
            }
        });
    }

    private String playerNameOf(com.jokerhub.paper.plugin.orzmc.features.review.ReviewRequest r) {
        // 通过 reviewService 的玩家解析端口获取名字（不可用则回退短 UUID）
        try {
            var name = org.bukkit.Bukkit.getOfflinePlayer(r.applicantId()).getName();
            return name == null ? r.applicantId().toString().substring(0, 8) : name;
        } catch (Exception e) {
            return r.applicantId().toString().substring(0, 8);
        }
    }

    private static String relativeTime(long epochMillis) {
        long diff = System.currentTimeMillis() - epochMillis;
        long minutes = diff / 60000L;
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        return hours < 24 ? hours + "小时前" : (hours / 24) + "天前";
    }

    private void emitReviewUsage(Consumer<MessageEnvelope> callback) {
        // 与 $v ? 同一套内容（统一 usageTip 模板），保证 fallback 与主动查询一致
        emitUsage(
                callback,
                feedbackService.usageTip(OrzUserCmd.REVIEW, botConfig().cmdPromptChar()));
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
