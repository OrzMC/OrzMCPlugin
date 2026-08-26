package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandAuditService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.logging.LogCaptureService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.Map;
import java.util.function.Consumer;

public final class BotCommandService extends BotCommandContext implements BotInboundHandler {
    private BotCommandListFeedbackService listFeedbackService;
    private final Map<OrzUserCmd, CmdHandler> handlers;
    private WorldMaintenanceService maintenanceService;
    private AccessRuleService accessRuleService;
    private com.jokerhub.paper.plugin.orzmc.features.review.ReviewService reviewService;
    private com.jokerhub.paper.plugin.orzmc.features.rank.RankService rankService;
    private LogCaptureService logCaptureService;
    /** 危险命令判定核心（安全加固 P0-5）：$e 控制台执行前过 guard。未注入时放行（向后兼容测试）。 */
    private CommandGuardService commandGuardService;
    /** 命令审计日志（安全加固 P0-4）：$e 路径记录 bot 来源审计。 */
    private CommandAuditService commandAuditService;
    /** $v 群审核命令处理器（Supplier 注入 reviewService/rankService，调用时读取最新值）。 */
    private final ReviewCommandHandler reviewCommandHandler;
    /** $p 群权限升降级命令处理器（Supplier 注入 rankService）。 */
    private final PermissionCommandHandler permissionCommandHandler;
    /** $e 控制台命令执行处理器（Supplier 注入 guard/audit/logCapture）。 */
    private final ConsoleCommandHandler consoleCommandHandler;
    /** $a/$r/$w 白名单命令处理器（Supplier 注入 listFeedbackService，rankService 注入后重建）。 */
    private final WhitelistCommandHandler whitelistCommandHandler;
    /** $l 在线玩家列表命令处理器（Supplier 注入 listFeedbackService）。 */
    private final PlayerListCommandHandler playerListCommandHandler;
    /** $b/$o 地图备份/优化命令处理器（Supplier 注入 maintenanceService）。 */
    private final MaintenanceCommandHandler maintenanceCommandHandler;
    /** $d 访问规则命令处理器（Supplier 注入 accessRuleService）。 */
    private final BlacklistCommandHandler blacklistCommandHandler;

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
        super(server, configs);
        this.listFeedbackService = new BotCommandListFeedbackService(server, configs);
        this.reviewCommandHandler = new ReviewCommandHandler(server, configs, () -> reviewService, () -> rankService);
        this.permissionCommandHandler = new PermissionCommandHandler(server, configs, () -> rankService);
        this.consoleCommandHandler = new ConsoleCommandHandler(
                server, configs, () -> commandGuardService, () -> commandAuditService, () -> logCaptureService);
        this.whitelistCommandHandler = new WhitelistCommandHandler(server, configs, () -> listFeedbackService);
        this.playerListCommandHandler = new PlayerListCommandHandler(server, configs, () -> listFeedbackService);
        this.maintenanceCommandHandler = new MaintenanceCommandHandler(server, configs, () -> maintenanceService);
        this.blacklistCommandHandler = new BlacklistCommandHandler(server, configs, () -> accessRuleService);
        this.handlers = Map.ofEntries(
                Map.entry(
                        OrzUserCmd.SHOW_PLAYERS,
                        (c, a, s, cb, r) -> playerListCommandHandler.handleShowPlayers(c, a, cb, r)),
                Map.entry(
                        OrzUserCmd.SHOW_WHITELIST,
                        (c, a, s, cb, r) -> whitelistCommandHandler.handleShowWhitelist(c, a, cb, r)),
                Map.entry(OrzUserCmd.SHOW_HELP, (c, a, s, cb, r) -> emitHelp(cb)),
                Map.entry(
                        OrzUserCmd.ADD_PLAYER_TO_WHITELIST,
                        (c, a, s, cb, r) -> whitelistCommandHandler.handleAddWhitelist(c, a, cb, r)),
                Map.entry(
                        OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST,
                        (c, a, s, cb, r) -> whitelistCommandHandler.handleRemoveWhitelist(c, a, cb, r)),
                Map.entry(OrzUserCmd.BACKUP, (c, a, s, cb, r) -> maintenanceCommandHandler.handleBackup(c, a, cb, r)),
                Map.entry(
                        OrzUserCmd.OPTIMIZE_WORLD,
                        (c, a, s, cb, r) -> maintenanceCommandHandler.handleOptimize(c, a, cb, r)),
                Map.entry(
                        OrzUserCmd.BLACKLIST, (c, a, s, cb, r) -> blacklistCommandHandler.handleBlacklist(c, a, cb, r)),
                Map.entry(OrzUserCmd.REVIEW, (c, a, s, cb, r) -> reviewCommandHandler.handle(c, a, s, cb, r)),
                Map.entry(OrzUserCmd.PERMISSION, (c, a, s, cb, r) -> permissionCommandHandler.handle(c, a, cb, r)),
                Map.entry(
                        OrzUserCmd.EXECUTE_CONSOLE_COMMAND,
                        (c, a, s, cb, r) -> consoleCommandHandler.handle(c, a, s, cb, r)));
    }

    /**
     * 一次性注入全部跨模块依赖（取代 6 个 {@code setXxxService} 二阶段 setter）。
     *
     * <p>组合根须在 {@link BotCommandService} 构造后、WebSocket 连接前调用本方法，消除「先连上、
     * 后注入」的半初始化窗口。rankService 非空时重建列表反馈服务（在线列表显示权限组）；其余
     * 依赖可空，未注入时由对应处理器降级处理（见各 Handler 的 Supplier 说明）。</p>
     */
    public void injectDependencies(BotCommandDependencies deps) {
        this.maintenanceService = deps.maintenanceService();
        this.accessRuleService = deps.accessRuleService();
        this.reviewService = deps.reviewService();
        this.logCaptureService = deps.logCaptureService();
        this.commandGuardService = deps.commandGuardService();
        this.commandAuditService = deps.commandAuditService();
        this.rankService = deps.rankService();
        // 优先使用组合根注入的共享 formatter（与上下线广播同一实例，保证格式一致）；
        // 未注入时按旧路径用 rankService 重建（测试向后兼容）。
        com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter formatter = deps.listFormatter();
        if (formatter == null && deps.rankService() != null) {
            formatter = new com.jokerhub.paper.plugin.orzmc.infra.player.OnlineListFormatter();
            formatter.setRankService(deps.rankService());
        }
        if (formatter != null) {
            this.listFeedbackService = new BotCommandListFeedbackService(server, configs, formatter);
        }
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
}
