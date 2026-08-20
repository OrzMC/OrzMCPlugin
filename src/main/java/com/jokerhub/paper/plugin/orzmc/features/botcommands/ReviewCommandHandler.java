package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewRequest;
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewService;
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
            emit(callback, "command_review_error", Map.of("message", "审核服务不可用"), "审核服务不可用");
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
            emit(callback, "command_review_list_empty", Map.of(), "当前没有待审核的申请。");
            return;
        }
        Integer page = parsePageArg(pageArg);
        List<String> lines = new ArrayList<>();
        for (var r : pending) {
            String typeName = reviewService
                    .get()
                    .typeById(r.typeId())
                    .map(t -> t.displayName())
                    .orElse(r.typeId());
            String playerName = playerNameOf(r);
            RankService rank = rankService.get();
            String group = rank == null
                    ? ""
                    : "（当前组：" + RankService.groupDisplayName(rank.currentGroup(r.applicantId())) + "）";
            String summary = reviewService
                    .get()
                    .typeById(r.typeId())
                    .map(t -> t.summarize(r.data()))
                    .orElse("");
            lines.add(
                    "[%s] %s%s：%s（%s 提交）".formatted(typeName, playerName, group, summary, relativeTime(r.createdAt())));
        }
        Paginator.paginate(
                server,
                text -> emit(callback, "command_review_list", Map.of("message", text), text),
                "------待审核申请------",
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
        String reviewer = (senderName == null || senderName.isBlank()) ? "群管理员" : senderName;
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
                        future = java.util.concurrent.CompletableFuture.completedFuture(
                                ReviewService.Result.fail("找不到待审申请: " + rest));
                    } else {
                        future = reviewService.get().review(request.get().id(), approved, reviewer);
                    }
                } else {
                    future = reviewService.get().reviewByApplicantName(playerOrType, approved, reviewer);
                }
                future.whenComplete((result, err) -> {
                    if (err != null) {
                        result = ReviewService.Result.fail(
                                "审核处理异常: " + (err.getMessage() == null ? "未知错误" : err.getMessage()));
                    }
                    emit(
                            callback,
                            result.success() ? "command_review_result" : "command_review_error",
                            Map.of("message", result.message()),
                            result.message());
                });
            } catch (Throwable t) {
                emit(
                        callback,
                        "command_review_error",
                        Map.of("message", "审核处理异常: " + (t.getMessage() == null ? "未知错误" : t.getMessage())),
                        "审核处理异常: " + (t.getMessage() == null ? "未知错误" : t.getMessage()));
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

    private static String relativeTime(long epochMillis) {
        long diff = System.currentTimeMillis() - epochMillis;
        long minutes = diff / 60000L;
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        return hours < 24 ? hours + "小时前" : (hours / 24) + "天前";
    }

    private void emitReviewUsage(Consumer<MessageEnvelope> callback) {
        // 与 $v ? 同一套内容（统一 usageTip 模板），保证 fallback 与主动查询一致
        emitUsage(
                callback,
                feedbackService.usageTip(OrzUserCmd.REVIEW, botConfig().cmdPromptChar()));
    }
}
