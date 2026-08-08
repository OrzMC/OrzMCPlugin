package com.jokerhub.paper.plugin.orzmc.features.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ReviewService 测试：通用审核框架全流程（提交→审核→处理→通知）。
 *
 * <p>重点验证：
 * <ul>
 *   <li>资格预检 / 防重复提交</li>
 *   <li>提交 → PENDING + 双端通知（游戏内 + 群 review_submitted）</li>
 *   <li>撤回（仅本人 PENDING）→ CANCELLED + 群 review_cancelled</li>
 *   <li>通过 → handler 执行 + 群 review_approved + 游戏内通知申请人</li>
 *   <li>拒绝 → 无 handler + 群 review_rejected</li>
 *   <li>按玩家名定位待审</li>
 * </ul>
 */
class ReviewServiceTest {

    private ReviewStore store;
    private ReviewNotifier notifier;
    private PlayerLookup lookup;
    private ReviewService service;
    private ReviewType builderType;

    private final UUID applicant = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = mock(ReviewStore.class);
        notifier = mock(ReviewNotifier.class);
        lookup = mock(PlayerLookup.class);
        service = new ReviewService(store, notifier, lookup);
        builderType = new ReviewType(
                "builder-promotion",
                "晋升建造者",
                "builder",
                rawArgs -> {
                    var data = new java.util.LinkedHashMap<String, String>();
                    data.put("target-group", "builder");
                    if (rawArgs != null && !rawArgs.isBlank()) data.put("reason", rawArgs);
                    return data;
                },
                id -> true,
                data -> "申请晋升 builder" + (data.get("reason") == null ? "" : "：" + data.get("reason")),
                id -> true);
        service.register(builderType);
        when(lookup.name(applicant)).thenReturn(Optional.of("TestMember"));
        when(lookup.name(other)).thenReturn(Optional.of("OtherPlayer"));
        when(lookup.resolve("TestMember")).thenReturn(Optional.of(applicant));
    }

    private ReviewRequest pendingRequest(String id) {
        return new ReviewRequest(
                id,
                "builder-promotion",
                applicant,
                Map.of("target-group", "builder"),
                ReviewRequest.Status.PENDING,
                1000L,
                0L,
                null);
    }

    // ---- 提交 ----

    @Test
    void submit_eligible_createsPendingAndNotifies() {
        when(store.hasPending("builder-promotion", applicant)).thenReturn(false);

        var result = service.submit(builderType, applicant, builderType.parseArgs("想用 WorldEdit"));

        assertTrue(result.success());
        assertNotNull(result.requestId());
        verify(store)
                .save(argThat(r -> r.typeId().equals("builder-promotion")
                        && r.status() == ReviewRequest.Status.PENDING
                        && r.data().get("reason").equals("想用 WorldEdit")));
        verify(notifier).gameMessage(eq(applicant), contains("已提交"));
        verify(notifier).groupEvent(eq("review_submitted"), anyMap());
    }

    @Test
    void submit_notEligible_rejected() {
        ReviewType restricted = new ReviewType(
                "builder-promotion", "晋升建造者", "builder", a -> Map.of(), id -> false, d -> "x", id -> false);
        service.register(restricted);

        var result = service.submit(restricted, applicant, Map.of());

        assertFalse(result.success());
        verify(store, never()).save(any());
        verify(notifier, never()).groupEvent(anyString(), anyMap());
    }

    @Test
    void submit_duplicatePending_rejected() {
        when(store.hasPending("builder-promotion", applicant)).thenReturn(true);

        var result = service.submit(builderType, applicant, Map.of());

        assertFalse(result.success());
        verify(store, never()).save(any());
    }

    // ---- 撤回 ----

    @Test
    void cancel_ownPending_cancelsAndNotifies() {
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));

        var result = service.cancel("r1", applicant);

        assertTrue(result.success());
        verify(store).save(argThat(r -> r.status() == ReviewRequest.Status.CANCELLED));
        verify(notifier).groupEvent(eq("review_cancelled"), anyMap());
        verify(notifier).gameMessage(eq(applicant), contains("已撤回"));
    }

    @Test
    void cancel_notOwnRequest_rejected() {
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));

        var result = service.cancel("r1", other);

        assertFalse(result.success());
        verify(store, never()).save(any());
    }

    @Test
    void cancel_alreadyReviewed_rejected() {
        ReviewRequest reviewed = new ReviewRequest(
                "r1", "builder-promotion", applicant, Map.of(), ReviewRequest.Status.APPROVED, 1000L, 2000L, "admin");
        when(store.findById("r1")).thenReturn(Optional.of(reviewed));

        var result = service.cancel("r1", applicant);

        assertFalse(result.success());
    }

    // ---- 审核 ----

    @Test
    void review_approve_runsHandlerAndNotifies() {
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));
        // handler 有副作用验证：用一个可观察 handler
        final int[] calls = {0};
        ReviewType observable =
                new ReviewType("builder-promotion", "晋升建造者", "builder", a -> Map.of(), id -> true, d -> "x", id -> {
                    calls[0]++;
                    return true;
                });
        service.register(observable);

        var result = service.review("r1", true, "admin");

        assertTrue(result.success());
        assertEquals(1, calls[0]);
        verify(store)
                .save(argThat(r -> r.status() == ReviewRequest.Status.APPROVED && "admin".equals(r.reviewerName())));
        verify(notifier).groupEvent(eq("review_approved"), anyMap());
        verify(notifier).gameMessage(eq(applicant), contains("已通过"));
    }

    @Test
    void review_reject_noHandlerAndNotifies() {
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));

        var result = service.review("r1", false, "admin");

        assertTrue(result.success());
        verify(store).save(argThat(r -> r.status() == ReviewRequest.Status.REJECTED));
        verify(notifier).groupEvent(eq("review_rejected"), anyMap());
        verify(notifier).gameMessage(eq(applicant), contains("被拒绝"));
    }

    @Test
    void review_alreadyProcessed_rejected() {
        ReviewRequest approved = new ReviewRequest(
                "r1", "builder-promotion", applicant, Map.of(), ReviewRequest.Status.APPROVED, 1000L, 2000L, "admin");
        when(store.findById("r1")).thenReturn(Optional.of(approved));

        var result = service.review("r1", true, "admin");

        assertFalse(result.success());
        verify(store, never()).save(any());
    }

    // ---- 按玩家名定位 ----

    @Test
    void reviewByApplicantName_singlePending_approves() {
        when(lookup.resolve("TestMember")).thenReturn(Optional.of(applicant));
        when(store.listPending()).thenReturn(List.of(pendingRequest("r1")));
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));

        var result = service.reviewByApplicantName("TestMember", true, "群管理员");

        assertTrue(result.success());
        verify(store).save(argThat(r -> r.status() == ReviewRequest.Status.APPROVED));
    }

    @Test
    void reviewByApplicantName_unknownPlayer_rejected() {
        when(lookup.resolve("Nobody")).thenReturn(Optional.empty());

        var result = service.reviewByApplicantName("Nobody", true, "admin");

        assertFalse(result.success());
    }

    @Test
    void reviewByApplicantName_multiplePending_asksForType() {
        when(lookup.resolve("TestMember")).thenReturn(Optional.of(applicant));
        when(store.listPending())
                .thenReturn(List.of(
                        pendingRequest("r1"),
                        new ReviewRequest(
                                "r2",
                                "whitelist-apply",
                                applicant,
                                Map.of(),
                                ReviewRequest.Status.PENDING,
                                2000L,
                                0L,
                                null)));

        var result = service.reviewByApplicantName("TestMember", true, "admin");

        assertFalse(result.success());
        assertTrue(result.message().contains("多条待审"));
    }

    @Test
    void review_handlerThrows_keepsPendingAndFails() {
        // handler 失败（如 LP 授权异常）：状态保持 PENDING，不落 APPROVED，返回失败
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));
        ReviewType failing =
                new ReviewType("builder-promotion", "晋升建造者", "builder", a -> Map.of(), id -> true, d -> "x", id -> {
                    throw new RuntimeException("LP 命令执行失败");
                });
        service.register(failing);

        var result = service.review("r1", true, "admin");

        assertFalse(result.success());
        assertTrue(result.message().contains("授权处理失败"));
        // 未保存 APPROVED 状态
        verify(store, never()).save(argThat(r -> r.status() == ReviewRequest.Status.APPROVED));
        verify(notifier, never()).groupEvent(eq("review_approved"), anyMap());
    }

    @Test
    void review_handlerReturnsFalse_keepsPendingAndFails() {
        // handler 静默失败（返回 false，如 promote 返回 null）：同样保持 PENDING，不落 APPROVED
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));
        ReviewType failing = new ReviewType(
                "builder-promotion", "晋升建造者", "builder", a -> Map.of(), id -> true, d -> "x", id -> false); // 授权处理返回失败
        service.register(failing);

        var result = service.review("r1", true, "admin");

        assertFalse(result.success());
        assertTrue(result.message().contains("授权处理失败"));
        verify(store, never()).save(argThat(r -> r.status() == ReviewRequest.Status.APPROVED));
        verify(notifier, never()).groupEvent(eq("review_approved"), anyMap());
    }

    @Test
    void review_handlerReturnsTrue_approvesAndNotifies() {
        when(store.findById("r1")).thenReturn(Optional.of(pendingRequest("r1")));
        ReviewType okType = new ReviewType(
                "builder-promotion", "晋升建造者", "builder", a -> Map.of(), id -> true, d -> "x", id -> true); // 授权成功
        service.register(okType);
        when(lookup.name(applicant)).thenReturn(Optional.of("TestMember"));

        var result = service.review("r1", true, "admin");

        assertTrue(result.success());
        verify(store).save(argThat(r -> r.status() == ReviewRequest.Status.APPROVED));
        verify(notifier).groupEvent(eq("review_approved"), anyMap());
    }
}
