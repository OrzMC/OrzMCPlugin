package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * $p u|d 群权限升降级命令处理器（从 BotCommandService 抽离）。
 *
 * <p>{@code rankService} 通过 {@link Supplier} 注入——组合根经
 * {@link BotCommandService#injectDependencies} 一次性注入，处理器调用时读取最新值。升降级走 {@code promoteAsync/demoteAsync}
 * 异步（LP 操作在非服务器线程），绝不 runSync+join（自锁超时，见 folia-luckperms-gotchas.md）。</p>
 */
final class PermissionCommandHandler extends BotCommandContext {

    private final Supplier<RankService> rankService;

    PermissionCommandHandler(ServerFacade server, TypedConfigProvider configs, Supplier<RankService> rankService) {
        super(server, configs);
        this.rankService = rankService;
    }

    void handle(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        RankService rank = rankService.get();
        if (rank == null) {
            emit(callback, "command_review_error", Map.of("message", "权限服务不可用"), "权限服务不可用");
            return;
        }
        if (!rank.isLuckPermsAvailable()) {
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
        var playerId = rank.resolvePlayerId(playerName);
        if (playerId == null) {
            emit(callback, "command_review_error", Map.of("message", "找不到玩家: " + playerName), "找不到玩家: " + playerName);
            return;
        }
        final var id = playerId;
        // 异步升降级：LP 操作（loadUser/saveUser 等待）在非服务器线程执行，绝不 runSync+join
        // （服务器调度线程同步等待 LP future 会自锁超时，Folia LP 适配器行为）
        java.util.concurrent.CompletableFuture<String> future = upgrade ? rank.promoteAsync(id) : rank.demoteAsync(id);
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
}
