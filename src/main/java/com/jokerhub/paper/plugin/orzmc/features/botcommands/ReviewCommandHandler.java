package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewRequest;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nServiceHolder;
import com.jokerhub.paper.plugin.orzmc.infra.paging.Paginator;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * $v 群审核命令处理器（从 BotCommandService 抽离）。
 *
 * <p>依赖 {@code reviewService}/{@code rankService} 通过 {@link Supplier} 注入——二者由组合根
 * 经 {@link BotCommandService#injectDependencies} 一次性注入，处理器在调用时读取最新值，避免陈旧引用。</p>
 */
final class ReviewCommandHandler extends BotCommandContext {

    private final Supplier<ReviewService> reviewService;
    private final Supplier<RankService> rankService;

    ReviewCommandHandler(
            ServerFacade server,
            TypedConfigProvider configs,
            Supplier<ReviewService> reviewService,
            Supplier<RankService> rankService) {
        super(server, configs);
        this.reviewService = reviewService;
        this.rankService = rankService;
    }

    void handle(
            OrzUserCmd cmd, boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback, String rawArgs) {
        if (!guardAdminCommand(cmd, isAdmin, callback)) return;
        if (reviewService.get() == null) {
            emitMsg(callback, "command_review_error", I18nServiceHolder.msg("bot.v.svc_unavailable"));
            return;
        }
        if (rawArgs.isBlank()) {
            emitReviewUsage(callback);
            return;
        }
        String[] parts = rawArgs.split("\\s+", 2);
        String sub = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1].trim() : "";
        switch (sub) {
            case "l" -> handleReviewList(callback, rest);
            case "y", "yes" -> handleReviewDecision(callback, rest, true, senderName);
            case "n", "no" -> handleReviewDecision(callback, rest, false, senderName);
            default -> emitReviewUsage(callback);
        }
    }

    private void handleReviewList(Consumer<MessageEnvelope> callback, String pageArg) {
        var pending = reviewService.get().listPending();
        if (pending.isEmpty()) {
            emitMsg(callback, "command_review_list_empty", I18nServiceHolder.msg("bot.v.list_empty"));
            return;
        }
        Integer page = parsePageArg(pageArg);
        List<String> lines = new ArrayList<>();
        for (var r : pending) {
            String typeName = reviewService
                    .get()
                    .typeById(r.typeId())
                    .map(t -> I18nServiceHolder.msg("review.type." + t.id()))
                    .orElse(r.typeId());
            String playerName = playerNameOf(r);
            RankService rank = rankService.get();
            String groupSuffix = rank == null
                    ? ""
                    : I18nServiceHolder.msg(
                            "bot.v.current_group",
                            Map.of("group", I18nServiceHolder.msg("rank.group." + rank.currentGroup(r.applicantId()))));
            String summary = reviewService
                    .get()
                    .typeById(r.typeId())
                    .map(t -> t.summarize(r.data()))
                    .orElse("");
            lines.add(I18nServiceHolder.msg(
                    "bot.v.list_item",
                    Map.of(
                            "type", typeName,
                            "player", playerName,
                            "group", groupSuffix,
                            "summary", summary,
                            "time", relativeTime(r.createdAt()))));
        }
        Paginator.paginatePages(
                server,
                (pageIndex, total, headerText, body) -> {
                    String text = headerText + "\n"
                            + I18nServiceHolder.msg(
                                    "bot.list.page_meta",
                                    Map.of("page", String.valueOf(pageIndex), "total", String.valueOf(total)))
                            + "\n"
                            + body;
                    emit(callback, "command_review_list", Map.of("message", text), text);
                },
                I18nServiceHolder.msg("bot.v.list_header"),
                // 空列表在上方 guard 已提前短路；此处占位仅为签名完整（不会触发）
                I18nServiceHolder.msg("bot.v.list_empty"),
                lines,
                5,
                page);
    }

    private void handleReviewDecision(
            Consumer<MessageEnvelope> callback, String rest, boolean approved, String senderName) {
        if (rest.isBlank()) {
            emitReviewUsage(callback);
            return;
        }
        // 审核人：优先群发送者身份（网关透传昵称）；未透传时兜底「群管理员」
        String reviewer = (senderName == null || senderName.isBlank())
                ? I18nServiceHolder.msg("bot.v.reviewer_fallback")
                : senderName;
        // 支持：$v y <玩家>  或  $v y <typeId> <玩家>
        String[] parts = rest.split("\\s+", 2);
        String first = parts[0];
        String second = parts.length > 1 ? parts[1].trim() : "";

        // 定位 + 发起审核在同步调度线程执行（Bukkit.getOfflinePlayer 需全局线程），
        // 但不 join 等待——审核通过时的授权（LP 晋升）在非服务器线程执行，结果经 CF
        // 回调发出（落状态回同步线程）。服务器调度线程绝不同步等待 LP future（自锁超时）。
        final boolean byType = reviewService.get().typeById(first).isPresent() && !second.isBlank();
        final String playerOrType = first;
        final String playerName = second;
        server.runSync(() -> {
            try {
                java.util.concurrent.CompletableFuture<ReviewService.Result> future;
                if (byType) {
                    var request = reviewService.get().pendingFor(playerOrType, playerName);
                    if (request.isEmpty()) {
                        future = java.util.concurrent.CompletableFuture.completedFuture(ReviewService.Result.fail(
                                I18nServiceHolder.msg("bot.v.not_found", Map.of("criteria", rest))));
                    } else {
                        future = reviewService.get().review(request.get().id(), approved, reviewer);
                    }
                } else {
                    future = reviewService.get().reviewByApplicantName(playerOrType, approved, reviewer);
                }
                future.whenComplete((result, err) -> {
                    if (err != null) {
                        result = ReviewService.Result.fail(reviewError(err.getMessage()));
                    }
                    emit(
                            callback,
                            result.success() ? "command_review_result" : "command_review_error",
                            Map.of("message", result.message()),
                            result.message());
                });
            } catch (Throwable t) {
                emitMsg(callback, "command_review_error", reviewError(t.getMessage()));
            }
        });
    }

    private String playerNameOf(ReviewRequest r) {
        // 通过 reviewService 的玩家解析端口获取名字（不可用则回退短 UUID）
        try {
            var name = org.bukkit.Bukkit.getOfflinePlayer(r.applicantId()).getName();
            return name == null ? r.applicantId().toString().substring(0, 8) : name;
        } catch (Exception e) {
            return r.applicantId().toString().substring(0, 8);
        }
    }

    private static String reviewError(String detail) {
        return I18nServiceHolder.msg(
                "bot.v.error",
                Map.of("detail", detail == null ? I18nServiceHolder.msg("bot.v.unknown_error") : detail));
    }

    private static String relativeTime(long epochMillis) {
        long diff = System.currentTimeMillis() - epochMillis;
        long minutes = diff / 60000L;
        if (minutes < 1) {
            return I18nServiceHolder.msg("bot.v.just_now");
        }
        if (minutes < 60) {
            return I18nServiceHolder.msg("bot.v.minutes_ago", Map.of("count", String.valueOf(minutes)));
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return I18nServiceHolder.msg("bot.v.hours_ago", Map.of("count", String.valueOf(hours)));
        }
        return I18nServiceHolder.msg("bot.v.days_ago", Map.of("count", String.valueOf(hours / 24)));
    }

    private void emitReviewUsage(Consumer<MessageEnvelope> callback) {
        // 与 $v ? 同一套内容（统一 usageTip 模板），保证 fallback 与主动查询一致
        emitUsage(
                callback,
                feedbackService.usageTip(OrzUserCmd.REVIEW, botConfig().cmdPromptChar()));
    }
}
