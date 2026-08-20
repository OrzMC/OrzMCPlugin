package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.entity.Player;

/**
 * $l 在线玩家列表命令处理器（从 BotCommandService 抽离）。
 *
 * <p>{@code listFeedbackService} 通过 {@link Supplier} 注入——其内部 {@code OnlineListFormatter}
 * 需在 rankService 注入后重建（在线列表显示权限组），处理器调用时读取最新实例。</p>
 */
final class PlayerListCommandHandler extends BotCommandContext {

    private final Supplier<BotCommandListFeedbackService> listFeedbackService;

    PlayerListCommandHandler(
            ServerFacade server,
            TypedConfigProvider configs,
            Supplier<BotCommandListFeedbackService> listFeedbackService) {
        super(server, configs);
        this.listFeedbackService = listFeedbackService;
    }

    void handleShowPlayers(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        server.runAsync(() -> {
            try {
                ArrayList<Player> onlinePlayers = listFeedbackService.get().currentOnlinePlayers();
                BotCommandListFeedbackService.OnlineList online = listFeedbackService
                        .get()
                        .buildOnlineList(onlinePlayers, server.server().getMaxPlayers());
                emit(callback, "command_players", listFeedbackService.get().onlineVars(online), online.fallback());
            } catch (Exception e) {
                server.logger().log(Level.SEVERE, "onlinePlayersInfo 异步任务异常", e);
            }
        });
    }
}
