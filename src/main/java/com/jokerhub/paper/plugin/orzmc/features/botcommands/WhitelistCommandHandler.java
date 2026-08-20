package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.whitelist.WhitelistService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.paging.Paginator;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * $a/$r/$w 白名单命令处理器（从 BotCommandService 抽离）。
 *
 * <p>{@code listFeedbackService} 通过 {@link Supplier} 注入——其内部 {@code OnlineListFormatter}
 * 需在 rankService 注入后重建（在线列表显示权限组），处理器调用时读取最新实例，避免陈旧引用。</p>
 */
final class WhitelistCommandHandler extends BotCommandContext {

    private final Supplier<BotCommandListFeedbackService> listFeedbackService;
    /** 单例白名单服务（替代每处 defaultImpl 新建，避免无状态对象重复实例化）。 */
    private final WhitelistService whitelistService;

    WhitelistCommandHandler(
            ServerFacade server,
            TypedConfigProvider configs,
            Supplier<BotCommandListFeedbackService> listFeedbackService) {
        super(server, configs);
        this.listFeedbackService = listFeedbackService;
        this.whitelistService = WhitelistService.defaultImpl(server.plugin());
    }

    void handleShowWhitelist(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        server.runAsync(() -> {
            try {
                WhitelistConfig whitelistConfig = configs.whitelist();
                WhitelistService svc = whitelistService;
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

    void handleAddWhitelist(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        Set<String> userNames = parseArgs(rawArgs);
        if (!guardWhitelistCommand(cmd, isAdmin, userNames, callback)) return;
        server.runSync(() -> {
            WhitelistService svc = whitelistService;
            String message = svc.addPlayers(server.server(), userNames);
            emit(callback, "command_whitelist_add_result", Map.of("message", message), message);
        });
    }

    void handleRemoveWhitelist(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        Set<String> userNames = parseArgs(rawArgs);
        if (!guardWhitelistCommand(cmd, isAdmin, userNames, callback)) return;
        server.runSync(() -> {
            WhitelistService svc = whitelistService;
            String message = svc.removePlayers(server.server(), userNames);
            emit(callback, "command_whitelist_remove_result", Map.of("message", message), message);
        });
    }

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
                            listFeedbackService.get().buildWhitelistHeader(updatedLines.size());
                    if (!removed.isEmpty()) {
                        BotCommandListFeedbackService.CleanupNotice notice =
                                listFeedbackService.get().buildCleanupNotice(removed);
                        emit(
                                callback,
                                "command_whitelist_cleanup",
                                listFeedbackService.get().cleanupVars(notice),
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
                listFeedbackService.get().buildWhitelistHeader(lines.size());
        emitWhitelistPages(callback, headerInfo.header(), lines, delayTicks, page);
    }

    private void emitWhitelistPages(
            Consumer<MessageEnvelope> callback, String header, ArrayList<String> lines, int delayTicks, Integer page) {
        Paginator.paginatePages(
                server,
                (pageIndex, total, headerText, body) -> {
                    BotCommandListFeedbackService.WhitelistPage pageInfo =
                            listFeedbackService.get().buildWhitelistPage(headerText, pageIndex, total, body);
                    emit(callback, "command_whitelist_page", pageInfo.vars(), pageInfo.fallback());
                },
                header,
                lines,
                delayTicks,
                page);
    }
}
