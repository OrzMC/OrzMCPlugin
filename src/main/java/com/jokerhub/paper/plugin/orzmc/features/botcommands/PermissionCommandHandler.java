package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nServiceHolder;
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

    /** 升降级总超时：超过即回失败，避免「永久无回执」。 */
    private static final long PERMISSION_OP_TIMEOUT_SECONDS = 60L;

    private final Supplier<RankService> rankService;

    PermissionCommandHandler(ServerFacade server, TypedConfigProvider configs, Supplier<RankService> rankService) {
        super(server, configs);
        this.rankService = rankService;
    }

    void handle(OrzUserCmd cmd, boolean isAdmin, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        RankService rank = rankService.get();
        if (rank == null) {
            emitMsg(callback, "command_review_error", I18nServiceHolder.msg("bot.p.svc_unavailable"));
            return;
        }
        if (!rank.isLuckPermsAvailable()) {
            emitMsg(callback, "command_review_error", I18nServiceHolder.msg("bot.p.no_lp"));
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
            emitMsg(
                    callback,
                    "command_review_error",
                    I18nServiceHolder.msg("bot.p.player_not_found", Map.of("player", playerName)));
            return;
        }
        final var id = playerId;
        // 异步升降级：LP 操作（loadUser/saveUser 等待）在非服务器线程执行，绝不 runSync+join
        // （服务器调度线程同步等待 LP future 会自锁超时，Folia LP 适配器行为）。
        // 总超时：LP 内部每次调用 3s 超时，但执行器排队/异常路径仍可能永不完成——
        // 无超时的话用户永远收不到回执（发了 $p 却没有任何反馈）。
        java.util.concurrent.CompletableFuture<String> future = (upgrade ? rank.promoteAsync(id) : rank.demoteAsync(id))
                .orTimeout(PERMISSION_OP_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        future.whenComplete((target, err) -> {
            if (err != null) {
                emitMsg(
                        callback,
                        "command_review_error",
                        I18nServiceHolder.msg("bot.p.op_error", Map.of("player", playerName)));
                return;
            }
            if (target == null) {
                emitMsg(
                        callback,
                        "command_review_error",
                        I18nServiceHolder.msg(
                                upgrade ? "bot.p.up_fail" : "bot.p.down_fail", Map.of("player", playerName)));
            } else {
                String group = I18nServiceHolder.msg("rank.group." + target);
                emitMsg(
                        callback,
                        I18nServiceHolder.msg(
                                upgrade ? "bot.p.up_ok" : "bot.p.down_ok",
                                Map.of("player", playerName, "group", group)));
            }
        });
    }
}
