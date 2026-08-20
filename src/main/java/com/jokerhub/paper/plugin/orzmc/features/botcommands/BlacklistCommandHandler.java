package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * $d IP 黑名单命令处理器（从 BotCommandService 抽离）。
 *
 * <p>{@code blacklistService} 通过 {@link Supplier} 注入——组合根经
 * {@link BotCommandService#injectDependencies} 一次性注入，处理器调用时读取最新值；未注入时提示服务不可用。</p>
 */
final class BlacklistCommandHandler extends BotCommandContext {

    private final Supplier<BlacklistService> blacklistService;

    BlacklistCommandHandler(
            ServerFacade server, TypedConfigProvider configs, Supplier<BlacklistService> blacklistService) {
        super(server, configs);
        this.blacklistService = blacklistService;
    }

    void handleBlacklist(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        BlacklistService svc = blacklistService.get();
        if (svc == null) {
            emit(callback, "command_blacklist_error", Map.of("message", "黑名单服务不可用"), "黑名单服务不可用");
            return;
        }
        if (rawArgs.isEmpty()) {
            List<String> patterns = svc.getPatterns();
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
            svc.remove(rawArgs.substring(1));
            emit(
                    callback,
                    "command_blacklist_remove",
                    Map.of("message", "已移除: " + rawArgs.substring(1)),
                    "已移除: " + rawArgs.substring(1));
        } else {
            svc.add(rawArgs);
            emit(callback, "command_blacklist_add", Map.of("message", "已添加: " + rawArgs), "已添加: " + rawArgs);
        }
    }
}
