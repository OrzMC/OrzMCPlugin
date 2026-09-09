package com.jokerhub.paper.plugin.orzmc.features.review;

import com.jokerhub.paper.plugin.orzmc.features.rank.GamemodeCorrectionService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 通用审核服务：申请→审核→处理→通知 全流程编排。
 *
 * <p>框架核心，不依赖任何宿主具体类：
 * <ul>
 *   <li>持久化走 {@link ReviewStore} 端口</li>
 *   <li>通知走 {@link ReviewNotifier} 端口（游戏内 + 群推送）</li>
 *   <li>玩家名↔UUID 解析走 {@link PlayerLookup} 端口（群指令定位用）</li>
 * </ul>
 * 审核类型由消费者模块 {@link #register(ReviewType)} 注入，通过后的处理策略
 * 由类型自身携带的 {@link ReviewHandler} 执行。新增审核类型零框架改动。</p>
 *
 * <p><b>异步审核（无状态漂移）</b>：审核通过时的授权处理（LP 晋升等）可能等待 LP 的
 * 异步 future，而 LP future 完成回调调度到服务器同步调度线程执行——因此审核通过路径
 * 返回 {@link CompletableFuture}，授权期间申请保持 PENDING，授权结果唯一决定最终状态：
 * 成功 → 回同步调度线程落 APPROVED + 双端通知；失败/异常 → 保持 PENDING + 返回失败提示。
 * 授权落状态前会重读校验仍 PENDING（并发撤回/处理时取消本次变更），杜绝漂移。</p>
 *
 * <p><b>并发审核去重</b>：同一申请以 {@code requestId} 为粒度做 in-flight 占位
 * （{@link #inflightReviews}），只放行第一个进入者——防止双 approve 并发授权越级晋升、
 * 以及授权期间被 reject/cancel 造成「状态已变化但 LP 已晋升」的漂移（占位期间
 * review/cancel 均返回「正在处理中」）。</p>
 */
public final class ReviewService {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("OrzMC.ReviewService");

    /** 申请结果。 */
    public record Result(boolean success, String message, String requestId) {
        public static Result ok(String message, String requestId) {
            return new Result(true, message, requestId);
        }

        public static Result fail(String message) {
            return new Result(false, message, null);
        }
    }

    private final ReviewStore store;
    private final ReviewNotifier notifier;
    private final PlayerLookup lookup;
    /** 状态落盘/通知回同步调度线程的执行器（Paper 主线程 / Folia global region 线程）；未注入则内联（单测）。 */
    private final Executor syncExecutor;
    /**
     * 审核通过后的游戏模式矫正（可空：不注入则跳过）。与权限组变化联动——审核通过多为
     * 晋升（授予权限），但未来降级型审核可能回收权限，此处兜底对齐游戏模式与当前权限。
     */
    private final GamemodeCorrectionService gamemodeCorrection;

    private final I18nService i18n;
    // LinkedHashMap 保持注册顺序（/apply 帮助列表稳定），synchronizedMap 保证并发安全
    private final Map<String, ReviewType> registry =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
    /**
     * 进行中的审核占位（requestId 粒度）。
     *
     * <p>并发审核去重（M2/M3）：同一申请只放行第一个进入者——并发双 approve 会并发
     * normalizeSingleGroup + track.promote 导致越级晋升；授权期间被 reject/cancel 会留下
     * 「状态已变化但 LP 已晋升」的漂移。review/cancel 均以 {@code add(requestId)} 原子占位，
     * 处理链（含异步授权 + 落状态）完成/异常后移除；占位期间其余调用返回「正在处理中」。</p>
     */
    private final Set<String> inflightReviews = ConcurrentHashMap.newKeySet();

    public ReviewService(ReviewStore store, ReviewNotifier notifier, PlayerLookup lookup, I18nService i18n) {
        this(store, notifier, lookup, Runnable::run, null, i18n);
    }

    /**
     * @param syncExecutor 回同步调度线程执行状态落盘 + 通知（审核通过后的最终化）。
     *     生产传入 {@code serverFacade::runSync}；框架单测可用 {@code Runnable::run} 内联。
     */
    public ReviewService(
            ReviewStore store, ReviewNotifier notifier, PlayerLookup lookup, Executor syncExecutor, I18nService i18n) {
        this(store, notifier, lookup, syncExecutor, null, i18n);
    }

    /**
     * @param syncExecutor     回同步调度线程执行状态落盘 + 通知（审核通过后的最终化）。
     *     生产传入 {@code serverFacade::runSync}；框架单测可用 {@code Runnable::run} 内联。
     * @param gamemodeCorrection 审核通过后的游戏模式矫正（可空；不注入则跳过）。
     * @param i18n            文案服务（审核业务结果统一默认语言 R1，保框架无 Bukkit 依赖）。
     */
    public ReviewService(
            ReviewStore store,
            ReviewNotifier notifier,
            PlayerLookup lookup,
            Executor syncExecutor,
            GamemodeCorrectionService gamemodeCorrection,
            I18nService i18n) {
        this.store = store;
        this.notifier = notifier;
        this.lookup = lookup;
        this.syncExecutor = syncExecutor != null ? syncExecutor : Runnable::run;
        this.gamemodeCorrection = gamemodeCorrection;
        this.i18n = i18n;
    }

    /** 审核业务结果文案（默认语言 R1）。 */
    private String t(String key, Map<String, String> vars) {
        return i18n == null ? key : i18n.msg(i18n.langFor(), key, vars);
    }

    private String t(String key) {
        return i18n == null ? key : i18n.msg(i18n.langFor(), key);
    }

    /** 审核类型展示名：词表 {@code review.type.<id>}（R1）；i18n 未注入/词表缺失回落注册 displayName。 */
    private String typeName(ReviewType type) {
        if (i18n == null) {
            return type.displayName();
        }
        return i18n.msg(i18n.langFor(), "review.type." + type.id());
    }

    /**
     * 通知/列表摘要（渲染层组装，G3b-2）：动词前缀 + 词表类型名 + 理由，随当前语言。
     * zh 输出与原注册闭包逐字一致（「申请」+ 名称 + 「：」+ 理由），en 为翻译。
     */
    private String summaryText(ReviewType type, Map<String, String> data) {
        String reason = data.get("reason");
        boolean hasReason = reason != null && !reason.isBlank();
        if (i18n == null) {
            return type.displayName() + (hasReason ? "：" + reason : "");
        }
        String prefix =
                i18n.msg(i18n.langFor(), MessageKeys.REVIEW_SUMMARY_PREFIX); // zh「申请」/ en "Applying for "（自带尾空格）
        String label = typeName(type);
        String sep = hasReason ? i18n.msg(i18n.langFor(), MessageKeys.REVIEW_REASON_SEP) : ""; // zh「：」/ en ": "
        return prefix + label + sep + (hasReason ? reason : "");
    }

    /** 玩家在线则发游戏内消息；通知端口未注入或玩家离线时静默。 */
    private void gameMessage(UUID playerId, String message) {
        if (notifier != null) {
            notifier.gameMessage(playerId, message);
        }
    }

    /** 群广播事件；通知端口未注入时静默。 */
    private void groupEvent(String templateKey, Map<String, String> vars) {
        if (notifier != null) {
            notifier.groupEvent(templateKey, vars);
        }
    }

    /** 注册审核类型（消费者模块装配时调用）。 */
    public void register(ReviewType type) {
        registry.put(type.id(), type);
    }

    /** 按 id 取审核类型。 */
    public Optional<ReviewType> typeById(String typeId) {
        return Optional.ofNullable(registry.get(typeId));
    }

    /** 已注册的全部审核类型（按注册顺序，/apply 帮助用）。 */
    public List<ReviewType> registeredTypes() {
        synchronized (registry) {
            return List.copyOf(registry.values());
        }
    }

    // ---- 玩家侧：提交 / 撤回 ----

    /**
     * 提交申请：资格预检 → 防重复 → PENDING 持久化 → 双端通知。
     *
     * @param type        审核类型
     * @param applicantId 申请人 UUID
     * @param data        请求内容（键值对）
     * @return 结果（含新请求 id）
     */
    public Result submit(ReviewType type, UUID applicantId, Map<String, String> data) {
        if (!type.isEligible(applicantId)) {
            return Result.fail(t(MessageKeys.REVIEW_NOT_ELIGIBLE, Map.of("name", typeName(type))));
        }
        if (store.hasPending(type.id(), applicantId)) {
            return Result.fail(t(MessageKeys.REVIEW_ALREADY_PENDING, Map.of("name", typeName(type))));
        }
        String id = newRequestId();
        ReviewRequest request = new ReviewRequest(
                id,
                type.id(),
                applicantId,
                Map.copyOf(data),
                ReviewRequest.Status.PENDING,
                System.currentTimeMillis(),
                0L,
                null);
        store.save(request);

        gameMessage(applicantId, t(MessageKeys.REVIEW_SUBMITTED_NOTIFY));
        groupEvent(
                "review_submitted",
                Map.of(
                        "player", lookup.name(applicantId).orElse("?"),
                        "type", typeName(type),
                        "summary", summaryText(type, request.data())));
        return Result.ok(t(MessageKeys.REVIEW_SUBMITTED_OK), id);
    }

    /**
     * 撤回申请：仅 PENDING 且申请人本人可撤 → CANCELLED 持久化 → 双端通知。
     *
     * <p>与 {@link #review} 共用 in-flight 占位：授权在途时申请被占位，撤回直接返回
     * 「正在处理中」，杜绝「撤回落 CANCELLED 但授权已晋升」的漂移（M3）。占位在
     * 读取/校验/落状态前完成，避免 check-then-act 竞态导致 CANCELLED 覆盖并发审核结果。</p>
     *
     * @param requestId   申请 id
     * @param applicantId 申请人 UUID（校验归属）
     */
    public Result cancel(String requestId, UUID applicantId) {
        // 先占位（与 review 互斥）：授权在途或并发审核时拒绝，防止撤回与授权结果冲突
        if (!inflightReviews.add(requestId)) {
            return Result.fail(t(MessageKeys.REVIEW_PROCESSING_CANCEL));
        }
        try {
            Optional<ReviewRequest> found = store.findById(requestId);
            if (found.isEmpty()) {
                return Result.fail(t(MessageKeys.REVIEW_NOT_FOUND));
            }
            ReviewRequest request = found.get();
            if (!request.applicantId().equals(applicantId)) {
                return Result.fail(t(MessageKeys.REVIEW_OWN_CANCEL_ONLY));
            }
            if (request.status() != ReviewRequest.Status.PENDING) {
                return Result.fail(t(MessageKeys.REVIEW_ALREADY_PROCESSED_CANCEL));
            }
            // CANCELLED 无审核人，reviewer 置 null（撤回由申请人本人发起，非审核行为）
            ReviewRequest cancelled = request.reviewed(ReviewRequest.Status.CANCELLED, null);
            store.save(cancelled);

            String typeName = typeById(request.typeId()).map(this::typeName).orElse(request.typeId());
            gameMessage(applicantId, t(MessageKeys.REVIEW_CANCELLED_NOTIFY, Map.of("name", typeName)));
            groupEvent(
                    "review_cancelled",
                    Map.of(
                            "player", lookup.name(applicantId).orElse("?"),
                            "type", typeName,
                            "summary",
                                    typeById(request.typeId())
                                            .map(t -> summaryText(t, request.data()))
                                            .orElse("")));
            return Result.ok(t(MessageKeys.REVIEW_CANCELLED_OK), requestId);
        } finally {
            inflightReviews.remove(requestId);
        }
    }

    // ---- 管理员侧：审核 ----

    /**
     * 审核申请：仅 PENDING → APPROVED/REJECTED → 通过时异步执行 handler → 双端通知。
     *
     * <p>通过路径返回 {@link CompletableFuture}：授权处理期间申请保持 PENDING，
     * 授权结果<b>唯一</b>决定最终状态（成功→APPROVED；失败/异常→保持 PENDING），杜绝
     * 「LP 已晋升但申请仍 PENDING」的状态漂移。落状态前重读校验仍 PENDING。</p>
     *
     * <p><b>并发去重（M2）</b>：同一申请以 requestId 为粒度 in-flight 占位，只放行第一个
     * 进入者——双 approve 并发授权会并发 normalizeSingleGroup + track.promote 造成越级晋升。
     * 占位在整条处理链（含异步授权 + 落状态）期间保持，其余并发 review/cancel 返回「正在处理中」。</p>
     *
     * @param requestId    申请 id
     * @param approved     通过 or 拒绝
     * @param reviewerName 审核人（群内昵称/游戏名）
     */
    public CompletableFuture<Result> review(String requestId, boolean approved, String reviewerName) {
        // 原子占位：失败说明已有并发 review/cancel 在处理，直接拒绝（去重 + 挡住授权期间撤回）
        if (!inflightReviews.add(requestId)) {
            return completedFail(t(MessageKeys.REVIEW_PROCESSING_REVIEW));
        }
        CompletableFuture<Result> result;
        try {
            result = doReview(requestId, approved, reviewerName);
        } catch (Throwable t) {
            inflightReviews.remove(requestId);
            throw t;
        }
        // 处理链（含异步授权 + 落状态）无论成功/失败/异常均释放占位
        return result.whenComplete((r, err) -> inflightReviews.remove(requestId));
    }

    /** {@link #review} 主体（调用方已持有 in-flight 占位）。 */
    private CompletableFuture<Result> doReview(String requestId, boolean approved, String reviewerName) {
        Optional<ReviewRequest> found = store.findById(requestId);
        if (found.isEmpty()) {
            return completedFail(t(MessageKeys.REVIEW_NOT_FOUND));
        }
        ReviewRequest request = found.get();
        if (request.status() != ReviewRequest.Status.PENDING) {
            return completedFail(t(
                    MessageKeys.REVIEW_ALREADY_PROCESSED_REVIEW,
                    Map.of("status", request.status().toString())));
        }
        ReviewType type = typeById(request.typeId()).orElse(null);
        if (type == null) {
            return completedFail(t(MessageKeys.REVIEW_UNKNOWN_TYPE_REVIEW, Map.of("type", request.typeId())));
        }

        if (!approved || type.handler() == null) {
            // 拒绝（或无需授权处理）：直接落状态 + 双端通知
            return CompletableFuture.completedFuture(
                    finalizeStatus(request, ReviewRequest.Status.REJECTED, reviewerName, type));
        }

        // 通过 + 有授权 handler：异步授权（LP 操作在非服务器线程），结果决定最终状态
        CompletableFuture<Boolean> auth;
        try {
            auth = type.handler().onApproved(request.applicantId());
        } catch (Exception e) {
            LOGGER.warning("审核通过但授权处理异常，申请保持待审: " + request.id() + " - " + e.getMessage());
            return completedFail(t(MessageKeys.REVIEW_AUTH_FAILED, Map.of("detail", e.getMessage())));
        }
        if (auth == null) {
            LOGGER.warning("审核通过但授权处理返回 null future，申请保持待审: " + request.id());
            return completedFail(t(MessageKeys.REVIEW_AUTH_FAILED_TOP));
        }

        return auth.handle((ok, err) -> {
                    if (err != null) {
                        LOGGER.warning("审核通过但授权处理异常，申请保持待审: " + request.id() + " - " + err.getMessage());
                        return Result.fail(t(MessageKeys.REVIEW_AUTH_FAILED, Map.of("detail", err.getMessage())));
                    }
                    if (!Boolean.TRUE.equals(ok)) {
                        LOGGER.warning("审核通过但授权处理返回失败（如链顶/LP 异常），申请保持待审: " + request.id());
                        return Result.fail(t(MessageKeys.REVIEW_AUTH_FAILED_TOP));
                    }
                    return null; // 授权成功：走最终化
                })
                .thenCompose(failResult -> failResult != null
                        ? CompletableFuture.completedFuture(failResult)
                        : finalizeApproved(request, reviewerName, type));
    }

    /**
     * 玩家撤回自己某类型的待审申请（/apply cancel &lt;type&gt;）。
     *
     * @param type        审核类型
     * @param applicantId 申请人 UUID
     */
    public Result cancelForApplicant(ReviewType type, UUID applicantId) {
        Optional<ReviewRequest> pending = store.pendingFor(type.id(), applicantId);
        if (pending.isEmpty()) {
            return Result.fail(t(MessageKeys.REVIEW_PENDING_NONE_SELF, Map.of("name", typeName(type))));
        }
        return cancel(pending.get().id(), applicantId);
    }

    /**
     * 按玩家名审核（/review approve|reject &lt;name&gt;）。
     *
     * <p>该玩家仅一条待审时直接处理；多条待审时提示用类型区分（群指令 $v 按类型定位）。
     * 异步语义同 {@link #review}。</p>
     */
    public CompletableFuture<Result> reviewByApplicantName(String playerName, boolean approved, String reviewerName) {
        Optional<UUID> applicantId = lookup.resolve(playerName);
        if (applicantId.isEmpty()) {
            return completedFail(t(MessageKeys.REVIEW_PLAYER_NOT_FOUND, Map.of("player", playerName)));
        }
        List<ReviewRequest> pending = store.listPending().stream()
                .filter(r -> r.applicantId().equals(applicantId.get()))
                .toList();
        if (pending.isEmpty()) {
            return completedFail(t(MessageKeys.REVIEW_NO_PENDING_APPLICANT, Map.of("player", playerName)));
        }
        if (pending.size() > 1) {
            String types = pending.stream()
                    .map(r -> typeById(r.typeId()).map(this::typeName).orElse(r.typeId()))
                    .distinct()
                    .collect(Collectors.joining("、"));
            return completedFail(t(MessageKeys.REVIEW_MULTIPLE_PENDING, Map.of("player", playerName, "types", types)));
        }
        return review(pending.get(0).id(), approved, reviewerName);
    }

    /** 审核通过后的最终化：回同步调度线程重校验 + 落 APPROVED + 双端通知（无漂移）。 */
    private CompletableFuture<Result> finalizeApproved(ReviewRequest request, String reviewerName, ReviewType type) {
        CompletableFuture<Result> deferred = new CompletableFuture<>();
        syncExecutor.execute(() -> {
            try {
                // 授权期间申请应保持 PENDING（in-flight 占位已挡住并发撤回/处理）。此处重读校验是
                // 兜底：仅当存在框架外写路径改动状态时才触发——放弃落状态并强提示（晋升不自动回收）。
                Optional<ReviewRequest> current = store.findById(request.id());
                if (current.isEmpty() || current.get().status() != ReviewRequest.Status.PENDING) {
                    LOGGER.severe("审核通过但落状态时申请已非待审（status="
                            + (current.isEmpty() ? "已删除" : current.get().status())
                            + "），保持原状: " + request.id()
                            + "——本次晋升可能已生效，不会被自动回收，请人工核对 LP 权限。");
                    deferred.complete(Result.fail(t(MessageKeys.REVIEW_STATE_CHANGED)));
                    return;
                }
                deferred.complete(finalizeStatus(request, ReviewRequest.Status.APPROVED, reviewerName, type));
                // 权限组变化后矫正游戏模式（审核通过多为晋升，授予权限通常无需矫正；
                // 为降级型审核兜底，无权限的模式被切回生存）。correctAsync 经实体调度器投递（Folia 兼容）。
                if (gamemodeCorrection != null) {
                    gamemodeCorrection.correctAsync(org.bukkit.Bukkit.getPlayer(request.applicantId()));
                }
            } catch (Throwable t) {
                LOGGER.warning("审核通过后落状态失败，申请保持待审: " + request.id() + " - " + t.getMessage());
                deferred.complete(Result.fail(t(MessageKeys.REVIEW_STATE_SAVE_FAILED)));
            }
        });
        return deferred;
    }

    /** 落状态（保存 + 双端通知）并生成业务提示；无异常。 */
    private Result finalizeStatus(
            ReviewRequest request, ReviewRequest.Status newStatus, String reviewerName, ReviewType type) {
        boolean approved = newStatus == ReviewRequest.Status.APPROVED;
        store.save(request.reviewed(newStatus, reviewerName));

        String playerName = lookup.name(request.applicantId()).orElse("?");
        String templateKey = approved ? "review_approved" : "review_rejected";
        Map<String, String> vars = Map.of(
                "player",
                playerName,
                "type",
                typeName(type),
                "summary",
                summaryText(type, request.data()),
                "reviewer",
                reviewerName == null ? "?" : reviewerName);
        groupEvent(templateKey, vars);
        gameMessage(
                request.applicantId(),
                approved
                        ? t(MessageKeys.REVIEW_APPROVED_NOTIFY, Map.of("name", typeName(type)))
                        : t(MessageKeys.REVIEW_REJECTED_NOTIFY, Map.of("name", typeName(type))));
        return Result.ok(
                approved
                        ? t(MessageKeys.REVIEW_APPROVED_OK, Map.of("player", playerName, "name", typeName(type)))
                        : t(MessageKeys.REVIEW_REJECTED_OK, Map.of("player", playerName, "name", typeName(type))),
                request.id());
    }

    private static CompletableFuture<Result> completedFail(String message) {
        return CompletableFuture.completedFuture(Result.fail(message));
    }

    // ---- 查询 ----

    /** 全部待审记录（按时间序）。 */
    public List<ReviewRequest> listPending() {
        return store.listPending();
    }

    /** 玩家是否已有该类型待审申请（防重复）。 */
    public boolean hasPending(ReviewType type, UUID applicantId) {
        return store.hasPending(type.id(), applicantId);
    }

    /** 玩家全部申请记录（/apply status 用）。 */
    public List<ReviewRequest> listByApplicant(UUID applicantId) {
        return store.listByApplicant(applicantId);
    }

    /** 群指令定位：类型 + 玩家名 → 该玩家该类型的待审申请。 */
    public Optional<ReviewRequest> pendingFor(String typeId, String playerName) {
        Optional<UUID> id = lookup.resolve(playerName);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        return store.pendingFor(typeId, id.get());
    }

    private static String newRequestId() {
        // 毫秒时间戳 + UUID 前 8 位，避免 hashCode 负数/同毫秒碰撞
        return Long.toHexString(System.currentTimeMillis()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }
}
