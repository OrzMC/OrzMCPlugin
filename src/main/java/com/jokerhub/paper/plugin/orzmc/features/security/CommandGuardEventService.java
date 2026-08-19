package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * 危险命令拦截事件编排（安全加固 P0-3）。
 *
 * <p>把 {@link CommandGuardService} 的纯判定结果接入 Bukkit 事件侧：</p>
 * <ul>
 *   <li>玩家聊天栏命令 {@link PlayerCommandPreprocessEvent} + 控制台/RCON 命令
 *       {@link ServerCommandEvent} 统一走 {@link #handle(CommandSender, String, Consumer)}；</li>
 *   <li>命中 {@code BLOCK} → 取消事件 + 发送者中文反馈 + 审计记录 blocked +（按
 *       {@code notify_admins} 配置）PRIVATE 私信管理员（模板 {@code command_guard_blocked}，
 *       Java 兜底文案防模板缺失）；</li>
 *   <li>命中 {@code WARN}（未限定目标选择器）→ 审计记录 executed + warning 日志但放行；</li>
 *   <li>ALLOW → 审计记录 executed 后放行。</li>
 * </ul>
 */
public final class CommandGuardEventService {

    private final CommandGuardService guard;
    private final TypedConfigProvider configs;
    private final Notifier notifier;
    private final CommandFeedbackService feedback;
    private final CommandAuditService audit;
    private final Logger logger;
    private final ThrottledNotifier logThrottle;
    private final ThrottledNotifier notifyThrottle;

    public CommandGuardEventService(
            CommandGuardService guard,
            TypedConfigProvider configs,
            Notifier notifier,
            CommandFeedbackService feedback,
            CommandAuditService audit,
            Logger logger) {
        this(guard, configs, notifier, feedback, audit, logger, new ThrottledNotifier(), new ThrottledNotifier());
    }

    /** 测试/装配用：显式注入限频器。 */
    public CommandGuardEventService(
            CommandGuardService guard,
            TypedConfigProvider configs,
            Notifier notifier,
            CommandFeedbackService feedback,
            CommandAuditService audit,
            Logger logger,
            ThrottledNotifier logThrottle,
            ThrottledNotifier notifyThrottle) {
        this.guard = guard;
        this.configs = configs;
        this.notifier = notifier;
        this.feedback = feedback;
        this.audit = audit;
        this.logger = logger;
        this.logThrottle = logThrottle;
        this.notifyThrottle = notifyThrottle;
    }

    /** 玩家聊天栏命令（含前导 /）。 */
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        handle(event.getPlayer(), event.getMessage(), event::setCancelled);
    }

    /** 控制台 / RCON / 命令方块命令。 */
    public void onServerCommand(ServerCommandEvent event) {
        handle(event.getSender(), event.getCommand(), event::setCancelled);
    }

    private void handle(CommandSender sender, String commandLine, Consumer<Boolean> cancel) {
        CommandGuardService.GuardDecision decision = guard.guard(commandLine);
        switch (decision.kind()) {
            case BLOCK -> {
                cancel.accept(true);
                sender.sendMessage(feedback.securityBlockedTip(decision.reason()));
                audit.record(auditSource(sender), sender.getName(), commandLine, true);
                if (configs.securityGuard().notifyAdmins()) {
                    notifyAdmin(commandLine, sender, decision.reason());
                }
            }
            case WARN -> {
                audit.record(auditSource(sender), sender.getName(), commandLine, false);
                // 日志节流：命令方块循环注入等高频来源 5 秒最多 1 条 warning（防日志刷屏），
                // 其余降级为 fine；审计记录不受影响（audit.record 写文件不刷日志）
                if (logThrottle.shouldRun("command_guard_warn_log", 5000)) {
                    logger.warning("[OrzMC] 危险命令放行（未限定目标选择器）: "
                            + commandLine
                            + "（来源: " + sourceLabel(sender) + "，发送者: " + sender.getName() + "）");
                } else {
                    logger.fine("[OrzMC] 危险命令放行（节流，详见审计）: "
                            + commandLine
                            + "（来源: " + sourceLabel(sender) + "，发送者: " + sender.getName() + "）");
                }
            }
            case ALLOW -> {
                audit.record(auditSource(sender), sender.getName(), commandLine, false);
            }
        }
    }

    private void notifyAdmin(String commandLine, CommandSender sender, String reason) {
        // 限频：管理员通知 10 秒最多 1 条（防命令方块循环触发 BLOCK 刷爆通知）
        if (!notifyThrottle.shouldRun("command_guard_block_notify", 10000)) {
            return;
        }
        String source = sourceLabel(sender);
        String fallback = "⚠ 高危命令已被拦截\n命令: " + commandLine
                + "\n来源: " + source
                + " | 发送者: " + sender.getName()
                + "\n原因: " + reason;
        MessageEnvelope env = configs.renderTemplate(
                TemplateKeys.COMMAND_GUARD_BLOCKED,
                Map.of(
                        "command", commandLine,
                        "source", source,
                        "sender", sender.getName(),
                        "reason", reason),
                fallback);
        notifier.event(TemplateKeys.COMMAND_GUARD_BLOCKED, env);
    }

    private static String sourceLabel(CommandSender sender) {
        return sender instanceof Player ? "玩家" : "控制台/RCON";
    }

    /** 审计来源分类（audit 记录用英文 token：game / console）。 */
    private static String auditSource(CommandSender sender) {
        return sender instanceof Player ? CommandAuditService.SOURCE_GAME : CommandAuditService.SOURCE_CONSOLE;
    }
}
