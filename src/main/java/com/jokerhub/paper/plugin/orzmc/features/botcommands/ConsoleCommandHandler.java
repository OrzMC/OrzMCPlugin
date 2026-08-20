package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandAuditService;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.LogCaptureService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * $e 控制台命令执行处理器（从 BotCommandService 抽离）。
 *
 * <p>{@code commandGuardService}/{@code commandAuditService}/{@code logCaptureService} 通过
 * {@link Supplier} 注入（组合根经 {@link BotCommandService#injectDependencies} 一次性注入），调用时读取最新值；未注入时退化为直接执行/无审计
 * （测试向后兼容）。</p>
 */
final class ConsoleCommandHandler extends BotCommandContext {

    /** $e 日志收集窗口：40 tick ≈ 2 秒（按 20 TPS 推算，覆盖大多数插件异步命令输出）。 */
    private static final long CONSOLE_OUTPUT_COLLECT_TICKS = 40L;

    /** $e 输出最大行数，超过截断防刷群。 */
    private static final int CONSOLE_OUTPUT_MAX_LINES = 30;

    private final Supplier<CommandGuardService> commandGuardService;
    private final Supplier<CommandAuditService> commandAuditService;
    private final Supplier<LogCaptureService> logCaptureService;

    ConsoleCommandHandler(
            ServerFacade server,
            TypedConfigProvider configs,
            Supplier<CommandGuardService> commandGuardService,
            Supplier<CommandAuditService> commandAuditService,
            Supplier<LogCaptureService> logCaptureService) {
        super(server, configs);
        this.commandGuardService = commandGuardService;
        this.commandAuditService = commandAuditService;
        this.logCaptureService = logCaptureService;
    }

    void handle(
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
        CommandGuardService guard = commandGuardService.get();
        CommandAuditService audit = commandAuditService.get();
        String auditSender = senderName == null ? CommandAuditService.SOURCE_BOT : senderName;
        CommandGuardService.GuardDecision decision =
                guard == null ? CommandGuardService.GuardDecision.allow() : guard.guard(rawArgs);
        if (decision.blocked()) {
            if (audit != null) {
                audit.record(CommandAuditService.SOURCE_BOT, auditSender, rawArgs, true);
            }
            emit(callback, "command_output", Map.of("message", decision.reason()), decision.reason());
            return;
        }
        if (audit != null) {
            audit.record(CommandAuditService.SOURCE_BOT, auditSender, rawArgs, false);
        }
        server.runSync(() -> {
            LogCaptureService capture = logCaptureService.get();
            if (capture == null) {
                // 未注入日志窗口服务：退化为仅返回执行状态
                ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(rawArgs);
                emit(callback, "command_output", Map.of("message", result.message()), result.message());
                return;
            }
            // 先取水位再执行，命令执行期间的日志行才能落入窗口
            long watermark = capture.watermark();
            ServerFacade.ConsoleCommandResult result = server.executeConsoleCommand(rawArgs);
            // 延迟一个收集窗口后取日志增量：覆盖异步命令输出（LuckPerms 等回调式输出）。
            // 日志窗口是「尽力而为」兜底：窗口内可能混入服务器其他活动日志（已过滤
            // 命令回显/玩家聊天），缓冲溢出时输出头部提示可能缺失
            server.runLater(
                    () -> {
                        List<String> windowLogLines = capture.drainSince(watermark);
                        String assembled = CommandOutputAssembler.assemble(
                                result.outputLines(), windowLogLines, CONSOLE_OUTPUT_MAX_LINES);
                        // 缺口检测独立于输出内容：即使窗口内有效行全被驱逐/过滤也要提示
                        String message;
                        if (capture.hasGapSince(watermark)) {
                            message = assembled.isEmpty() ? "⚠️ 日志缓冲溢出，输出可能不完整" : "⚠️ 日志缓冲溢出，输出可能不完整\n" + assembled;
                        } else {
                            message = assembled.isEmpty() ? result.message() : assembled;
                        }
                        emit(callback, "command_output", Map.of("message", message), message);
                    },
                    CONSOLE_OUTPUT_COLLECT_TICKS);
        });
    }
}
