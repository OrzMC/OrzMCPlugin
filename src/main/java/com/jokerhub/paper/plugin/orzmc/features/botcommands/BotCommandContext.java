package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Bot 命令共享上下文（从 BotCommandService 抽离的基类）。
 *
 * <p>承载所有 $cmd 处理器共用的依赖（server/configs/feedbackService）与辅助方法
 * （emit/usage/admin 提示/权限守卫/参数解析），供 BotCommandService 及各处理器继承，
 * 消除 God class 内的大段重复样板。</p>
 */
abstract class BotCommandContext {

    protected final ServerFacade server;
    protected final TypedConfigProvider configs;
    protected final BotCommandFeedbackService feedbackService = new BotCommandFeedbackService();

    BotCommandContext(ServerFacade server, TypedConfigProvider configs) {
        this.server = server;
        this.configs = configs;
    }

    protected BotConfig botConfig() {
        try {
            return configs.bot();
        } catch (Exception e) {
            server.logger().warning("读取 botConfig 失败，使用默认值: " + e.getMessage());
            return new BotConfig("$", null, null);
        }
    }

    protected void emitAdminRequired(Consumer<MessageEnvelope> callback, String tip) {
        emit(callback, "command_admin_required", Map.of("message", tip), tip);
    }

    protected void emitUsage(Consumer<MessageEnvelope> callback, String tip) {
        emit(callback, "command_usage", Map.of("message", tip), tip);
    }

    protected void emit(
            Consumer<MessageEnvelope> callback, String templateKey, Map<String, String> vars, String fallback) {
        MessageEnvelope env = configs.renderTemplate(templateKey, vars, fallback);
        callback.accept(env);
    }

    protected boolean guardAdminCommand(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        if (isAdmin) return true;
        emitAdminRequired(
                callback, feedbackService.adminRequiredTip(cmd, botConfig().cmdPromptChar()));
        return false;
    }

    protected boolean guardWhitelistCommand(
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

    protected boolean guardOptimizeEnabled(Consumer<MessageEnvelope> callback) {
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

    protected Integer parsePageArg(String rawArgs) {
        if (rawArgs.isBlank()) return null;
        String token = rawArgs.split("[, ]+")[0];
        try {
            return Integer.parseInt(token);
        } catch (Exception e) {
            server.logger().warning("白名单页码解析失败: " + token + " - " + e.getMessage());
            return null;
        }
    }

    protected Set<String> parseArgs(String rawArgs) {
        if (rawArgs.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(rawArgs.split("[, ]+")));
    }
}
