package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRule;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRuleFeedback;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * $d 访问规则命令处理器（从 BotCommandService 抽离）。
 *
 * <p>{@code accessRuleService} 通过 {@link Supplier} 注入——组合根经
 * {@link BotCommandService#injectDependencies} 一次性注入，处理器调用时读取最新值；未注入时提示服务不可用。</p>
 */
final class BlacklistCommandHandler extends BotCommandContext {

    private final Supplier<AccessRuleService> accessRuleService;

    BlacklistCommandHandler(
            ServerFacade server, TypedConfigProvider configs, Supplier<AccessRuleService> accessRuleService) {
        super(server, configs);
        this.accessRuleService = accessRuleService;
    }

    void handleBlacklist(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        // 访问规则命令统一调度到全局线程：① persist 的同步磁盘 I/O 不阻塞 WS reader 线程；
        // ② $d 间按到达顺序 FIFO 串行，保证「添加后立刻 list」读到最新规则。
        server.runSync(() -> handleBlacklistSafely(cmd, isAdmin, callback, rawArgs));
    }

    /** 全局线程兜底：异常不再被 InboundEventParser 的 try/catch 捕获，恢复日志并给群内错误反馈。 */
    private void handleBlacklistSafely(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        try {
            handleBlacklistOnServerThread(cmd, isAdmin, callback, rawArgs);
        } catch (Exception e) {
            server.logger().warning("$d 访问规则命令执行异常: " + e);
            try {
                emit(
                        callback,
                        "command_blacklist_error",
                        Map.of("message", "执行出错: " + e.getMessage()),
                        "执行出错，请查看服务端日志");
            } catch (Exception ignored) {
                // renderTemplate 本身异常时不再二次抛出
            }
        }
    }

    private void handleBlacklistOnServerThread(
            OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        AccessRuleService svc = accessRuleService.get();
        if (svc == null) {
            emit(callback, "command_blacklist_error", Map.of("message", "黑名单服务不可用"), "黑名单服务不可用");
            return;
        }
        // 玩家名子命令大小写不敏感（$d Player exact foo 不应被当成 IP 规则误加）
        String lower = rawArgs.toLowerCase(Locale.ROOT);
        if (rawArgs.isEmpty()) {
            listAccessRules(callback, svc);
            return;
        }
        if ("player".equals(lower) || "player list".equals(lower)) {
            listPlayerRules(callback, svc);
            return;
        }
        if (lower.startsWith("-player")) {
            String rest = rawArgs.substring("-player".length());
            if (!rest.isEmpty() && !rest.startsWith(" ")) {
                emit(
                        callback,
                        "command_blacklist_error",
                        Map.of("message", "用法: $d -player <type> <value>"),
                        "用法: $d -player <type> <value>");
                return;
            }
            handlePlayerRule(callback, svc, true, rest.trim());
            return;
        }
        if (lower.startsWith("player")) {
            String rest = rawArgs.substring("player".length());
            if (!rest.isEmpty() && !rest.startsWith(" ")) {
                emit(
                        callback,
                        "command_blacklist_error",
                        Map.of("message", "用法: $d player <type> <value>"),
                        "用法: $d player <type> <value>");
                return;
            }
            handlePlayerRule(callback, svc, false, rest.trim());
            return;
        }
        if (rawArgs.startsWith("-")) {
            // trim：`$d - exact foo` 破折号后带空格时，去掉空格再判玩家名规则语法，避免首词空串绕过守卫
            String pattern = rawArgs.substring(1).trim();
            if (pattern.isEmpty()) {
                emit(
                        callback,
                        "command_blacklist_error",
                        Map.of("message", "用法: $d -<IP>（移除 IP）/ $d -player <type> <value>（移除玩家名规则）"),
                        "用法: $d -<IP>（移除 IP）/ $d -player <type> <value>（移除玩家名规则）");
                return;
            }
            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                emit(
                        callback,
                        "command_blacklist_error",
                        Map.of("message", "玩家名规则请使用: $d -player <type> <value>"),
                        "玩家名规则请使用: $d -player <type> <value>");
                return;
            }
            if (svc.removeIpPattern(pattern)) {
                emit(callback, "command_blacklist_remove", Map.of("message", "已移除: " + pattern), "已移除: " + pattern);
            } else {
                emit(
                        callback,
                        "command_blacklist_error",
                        Map.of("message", "未在黑名单中找到: " + pattern),
                        "未在黑名单中找到: " + pattern);
            }
        } else {
            // trim：与 AccessRuleService 归一化口径一致（greedyString 保留尾随空格，群消息亦可能带空格）
            String pattern = rawArgs.trim();
            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                emit(
                        callback,
                        "command_blacklist_error",
                        Map.of("message", "玩家名规则请使用: $d player <type> <value>"),
                        "玩家名规则请使用: $d player <type> <value>");
                return;
            }
            if (svc.addIpPattern(pattern)) {
                emit(callback, "command_blacklist_add", Map.of("message", "已添加: " + pattern), "已添加: " + pattern);
            } else {
                emit(callback, "command_blacklist_add", Map.of("message", "黑名单已存在: " + pattern), "黑名单已存在: " + pattern);
            }
        }
    }

    private void listAccessRules(Consumer<MessageEnvelope> callback, AccessRuleService svc) {
        List<String> lines = new ArrayList<>();
        List<String> ips = svc.getIpPatterns();
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (ips.isEmpty() && rules.isEmpty()) {
            emit(callback, "command_blacklist_list", Map.of("patterns", "访问规则为空"), "访问规则为空");
            return;
        }
        if (!ips.isEmpty()) {
            lines.add("IP黑名单:");
            ips.forEach(line -> lines.add("  " + line));
        }
        if (!rules.isEmpty()) {
            lines.add("玩家名规则:");
            rules.forEach(rule -> lines.add("  " + rule.display()));
        }
        String content = String.join("\n", lines);
        emit(callback, "command_blacklist_list", Map.of("patterns", content), content);
    }

    private void listPlayerRules(Consumer<MessageEnvelope> callback, AccessRuleService svc) {
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (rules.isEmpty()) {
            emit(callback, "command_blacklist_list", Map.of("patterns", "玩家名规则为空"), "玩家名规则为空");
            return;
        }
        String content = rules.stream()
                .map(PlayerNameRule::display)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        emit(callback, "command_blacklist_list", Map.of("patterns", content), content);
    }

    private void handlePlayerRule(
            Consumer<MessageEnvelope> callback, AccessRuleService svc, boolean remove, String raw) {
        String[] parts = raw.split("\\s+", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            emit(
                    callback,
                    "command_blacklist_error",
                    Map.of("message", "用法: $d " + (remove ? "-" : "") + "player <type> <value>"),
                    "用法: $d " + (remove ? "-" : "") + "player <type> <value>");
            return;
        }
        // 反馈统一走 PlayerNameRuleFeedback（与游戏 /blacklist 共用）；未找到用 error 键而非 remove 键
        PlayerNameRuleFeedback.Outcome outcome = PlayerNameRuleFeedback.feedback(svc, parts[0], parts[1], remove);
        String key = outcome.success()
                ? (remove ? "command_blacklist_remove" : "command_blacklist_add")
                : "command_blacklist_error";
        emit(callback, key, Map.of("message", outcome.message()), outcome.message());
    }
}
