package com.jokerhub.paper.plugin.orzmc.features.review;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    // LinkedHashMap 保持注册顺序（/apply 帮助列表稳定），synchronizedMap 保证并发安全
    private final Map<String, ReviewType> registry =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    public ReviewService(ReviewStore store, ReviewNotifier notifier, PlayerLookup lookup) {
        this.store = store;
        this.notifier = notifier;
        this.lookup = lookup;
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
            return Result.fail("你不满足「" + type.displayName() + "」的申请条件。");
        }
        if (store.hasPending(type.id(), applicantId)) {
            return Result.fail("你已提交过「" + type.displayName() + "」申请，请等待管理员审核。");
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

        gameMessage(applicantId, "申请已提交，管理员审核通过后将自动生效。");
        groupEvent(
                "review_submitted",
                Map.of(
                        "player", lookup.name(applicantId).orElse("?"),
                        "type", type.displayName(),
                        "summary", type.summarize(request.data())));
        return Result.ok("申请已提交，等待管理员审核。", id);
    }

    /**
     * 撤回申请：仅 PENDING 且申请人本人可撤 → CANCELLED 持久化 → 双端通知。
     *
     * @param requestId   申请 id
     * @param applicantId 申请人 UUID（校验归属）
     */
    public Result cancel(String requestId, UUID applicantId) {
        Optional<ReviewRequest> found = store.findById(requestId);
        if (found.isEmpty()) {
            return Result.fail("找不到该申请。");
        }
        ReviewRequest request = found.get();
        if (!request.applicantId().equals(applicantId)) {
            return Result.fail("只能撤回自己的申请。");
        }
        if (request.status() != ReviewRequest.Status.PENDING) {
            return Result.fail("该申请已处理，无法撤回。");
        }
        // CANCELLED 无审核人，reviewer 置 null（撤回由申请人本人发起，非审核行为）
        ReviewRequest cancelled = request.reviewed(ReviewRequest.Status.CANCELLED, null);
        store.save(cancelled);

        String typeName =
                typeById(request.typeId()).map(ReviewType::displayName).orElse(request.typeId());
        gameMessage(applicantId, "已撤回「" + typeName + "」申请。");
        groupEvent(
                "review_cancelled",
                Map.of(
                        "player", lookup.name(applicantId).orElse("?"),
                        "type", typeName,
                        "summary",
                                typeById(request.typeId())
                                        .map(t -> t.summarize(request.data()))
                                        .orElse("")));
        return Result.ok("已撤回申请。", requestId);
    }

    // ---- 管理员侧：审核 ----

    /**
     * 审核申请：仅 PENDING → APPROVED/REJECTED → 通过时执行 handler → 双端通知。
     *
     * @param requestId    申请 id
     * @param approved     通过 or 拒绝
     * @param reviewerName 审核人（群内昵称/游戏名）
     */
    public Result review(String requestId, boolean approved, String reviewerName) {
        Optional<ReviewRequest> found = store.findById(requestId);
        if (found.isEmpty()) {
            return Result.fail("找不到该申请。");
        }
        ReviewRequest request = found.get();
        if (request.status() != ReviewRequest.Status.PENDING) {
            return Result.fail("该申请已处理（" + request.status() + "）。");
        }
        ReviewType type = typeById(request.typeId()).orElse(null);
        if (type == null) {
            return Result.fail("未知审核类型: " + request.typeId());
        }

        // 先执行 handler（授权等副作用），成功后再落状态；
        // 失败则状态保持 PENDING，避免「已通过但授权未生效」的不一致
        if (approved && type.handler() != null) {
            boolean handled;
            try {
                handled = type.handler().onApproved(request.applicantId());
            } catch (Exception e) {
                LOGGER.warning("审核通过但授权处理异常，申请保持待审: " + request.id() + " - " + e.getMessage());
                return Result.fail("授权处理失败（" + e.getMessage() + "），请重试或联系管理员。");
            }
            if (!handled) {
                LOGGER.warning("审核通过但授权处理返回失败（如链顶/LP 异常），申请保持待审: " + request.id());
                return Result.fail("授权处理失败（目标可能已在最高等级或 LuckPerms 异常），请重试或联系管理员。");
            }
        }

        ReviewRequest.Status newStatus = approved ? ReviewRequest.Status.APPROVED : ReviewRequest.Status.REJECTED;
        store.save(request.reviewed(newStatus, reviewerName));

        String playerName = lookup.name(request.applicantId()).orElse("?");
        String templateKey = approved ? "review_approved" : "review_rejected";
        Map<String, String> vars = Map.of(
                "player",
                playerName,
                "type",
                type.displayName(),
                "summary",
                type.summarize(request.data()),
                "reviewer",
                reviewerName == null ? "?" : reviewerName);
        groupEvent(templateKey, vars);
        gameMessage(
                request.applicantId(),
                approved ? "你的「" + type.displayName() + "」申请已通过！" : "你的「" + type.displayName() + "」申请被拒绝。");
        return Result.ok(
                approved
                        ? "已通过 " + playerName + " 的「" + type.displayName() + "」申请。"
                        : "已拒绝 " + playerName + " 的「" + type.displayName() + "」申请。",
                requestId);
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
            return Result.fail("你当前没有「" + type.displayName() + "」的待审申请。");
        }
        return cancel(pending.get().id(), applicantId);
    }

    /**
     * 按玩家名审核（/review approve|reject &lt;name&gt;）。
     *
     * <p>该玩家仅一条待审时直接处理；多条待审时提示用类型区分（群指令 $v 按类型定位）。</p>
     */
    public Result reviewByApplicantName(String playerName, boolean approved, String reviewerName) {
        Optional<UUID> applicantId = lookup.resolve(playerName);
        if (applicantId.isEmpty()) {
            return Result.fail("找不到玩家: " + playerName);
        }
        List<ReviewRequest> pending = store.listPending().stream()
                .filter(r -> r.applicantId().equals(applicantId.get()))
                .toList();
        if (pending.isEmpty()) {
            return Result.fail(playerName + " 没有待审核的申请。");
        }
        if (pending.size() > 1) {
            String types = pending.stream()
                    .map(r -> typeById(r.typeId()).map(ReviewType::displayName).orElse(r.typeId()))
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("、"));
            return Result.fail(playerName + " 有多条待审申请（" + types + "），请用群指令 $v 按类型处理。");
        }
        return review(pending.get(0).id(), approved, reviewerName);
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
