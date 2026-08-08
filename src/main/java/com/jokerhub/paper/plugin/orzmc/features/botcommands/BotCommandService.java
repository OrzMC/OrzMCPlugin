package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
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
    private BotCommandListFeedbackService listFeedbackService;
    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final Map<OrzUserCmd, CmdHandler> handlers;
    private WorldMaintenanceService maintenanceService;
    private BlacklistService blacklistService;
    private com.jokerhub.paper.plugin.orzmc.features.review.ReviewService reviewService;
    private com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService;

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
                        (c, a, s, cb, r) -> handleExecuteConsoleCommand(c, a, cb, r)));
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
            emit(
                    callback,
                    "command_review_error",
                    Map.of("message", "用法: $p u <玩家> 升级 / $p d <玩家> 降级"),
                    "用法: $p u <玩家> 升级 / $p d <玩家> 降级");
            return;
        }
        String[] parts = rawArgs.split("\\s+", 2);
        String sub = parts[0].toLowerCase();
        String playerName = parts.length > 1 ? parts[1].trim() : "";
        if (playerName.isBlank()) {
            emit(
                    callback,
                    "command_review_error",
                    Map.of("message", "用法: $p " + sub + " <玩家>"),
                    "用法: $p " + sub + " <玩家>");
            return;
        }
        boolean upgrade;
        switch (sub) {
            case "u", "up" -> upgrade = true;
            case "d", "down" -> upgrade = false;
            default -> {
                emit(
                        callback,
                        "command_review_error",
                        Map.of("message", "用法: $p u <玩家> 升级 / $p d <玩家> 降级"),
                        "用法: $p u <玩家> 升级 / $p d <玩家> 降级");
                return;
            }
        }
        var playerId = rankService.resolvePlayerId(playerName);
        if (playerId == null) {
            emit(callback, "command_review_error", Map.of("message", "找不到玩家: " + playerName), "找不到玩家: " + playerName);
            return;
        }
        final var id = playerId;
        var done = new java.util.concurrent.CompletableFuture<String>();
        server.runSync(() -> {
            try {
                done.complete(upgrade ? rankService.promote(id) : rankService.demote(id));
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        String target = done.join();
        if (target == null) {
            emit(
                    callback,
                    "command_review_error",
                    Map.of(
                            "message",
                            playerName + (upgrade ? " 无法升级：已达最高等级或权限数据异常（详见服务器日志）。" : " 无法降级：已达最低等级或权限数据异常（详见服务器日志）。")),
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
            String group = rankService == null ? "" : "（当前组：" + rankService.currentGroup(r.applicantId()) + "）";
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

        // 群指令走异步线程（WebSocket/orzdebug），审核执行 + 状态落盘必须回主线程，
        // 否则 Bukkit 配置保存可能不生效
        final ReviewService.Result reviewResult;
        final boolean byType = reviewService.typeById(first).isPresent() && !second.isBlank();
        final String playerOrType = first;
        final String playerName = second;
        var done = new java.util.concurrent.CompletableFuture<
                com.jokerhub.paper.plugin.orzmc.features.review.ReviewService.Result>();
        server.runSync(() -> {
            try {
                if (byType) {
                    var request = reviewService.pendingFor(playerOrType, playerName);
                    if (request.isEmpty()) {
                        done.complete(ReviewService.Result.fail("找不到待审申请: " + rest));
                    } else {
                        done.complete(reviewService.review(request.get().id(), approved, reviewer));
                    }
                } else {
                    done.complete(reviewService.reviewByApplicantName(playerOrType, approved, reviewer));
                }
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        var result = done.join();
        emit(
                callback,
                result.success() ? "command_review_result" : "command_review_error",
                Map.of("message", result.message()),
                result.message());
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
        String tip = "用法：\n" + "$v l — 待审列表\n" + "$v l 2 — 第 2 页\n" + "$v y <玩家> — 通过\n" + "$v n <玩家> — 拒绝";
        emit(callback, "command_review_error", Map.of("message", tip), tip);
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
