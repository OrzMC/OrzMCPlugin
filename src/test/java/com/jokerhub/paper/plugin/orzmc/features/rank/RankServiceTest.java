package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewNotifier;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RankService 测试（LP API 版，2026-08-07 重构）：
 *
 * <ul>
 *   <li>权限链：default→member→builder→admin（LP track 唯一事实源）</li>
 *   <li>currentGroup：LP 真实组优先，无 LP 一律回退 default（不本地推断）</li>
 *   <li>promote/demote：翻译 LP 结果状态（SUCCESS / END_OF_TRACK / REMOVED_FROM_FIRST_GROUP）</li>
 *   <li>自动晋升：时长达标且当前组 default 才触发（幂等由 LP 保证）</li>
 * </ul>
 */
class RankServiceTest {

    private RankStore store;
    private RankPromoter promoter;
    private ReviewNotifier notifier;
    private RankService service;

    @BeforeEach
    void setUp() {
        store = mock(RankStore.class);
        promoter = mock(RankPromoter.class);
        notifier = mock(ReviewNotifier.class);
        service = new RankService(store, promoter, 10, notifier);
    }

    // ---- 自动晋升（default→member）----

    @Test
    void checkPromotion_belowThreshold_doesNotPromote() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(store.getPlaytimeMinutes(id)).thenReturn(30L); // 0.5h < 10h

        service.checkPromotion(id);

        verify(promoter, never()).promoteAsync(any());
    }

    @Test
    void checkPromotion_atThreshold_defaultGroup_promotes() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(store.getPlaytimeMinutes(id)).thenReturn(600L); // 10h
        when(promoter.currentTrackGroup(id)).thenReturn("default");
        when(promoter.promoteAsync(id)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture("member"));

        service.checkPromotion(id);

        verify(promoter).promoteAsync(id);
    }

    @Test
    void checkPromotion_atThreshold_alreadyMember_doesNotPromote() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(store.getPlaytimeMinutes(id)).thenReturn(600L);
        when(promoter.currentTrackGroup(id)).thenReturn("member"); // 已在 member，幂等

        service.checkPromotion(id);

        verify(promoter, never()).promoteAsync(any());
    }

    @Test
    void checkPromotion_noLuckPerms_skips() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(false);
        when(store.getPlaytimeMinutes(id)).thenReturn(9999L);

        service.checkPromotion(id);

        verify(promoter, never()).promoteAsync(any());
    }

    // ---- 当前权限组（LP 优先，无 LP 回退）----

    @Test
    void currentGroup_lpTrackGroup_wins() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(promoter.currentTrackGroup(id)).thenReturn("admin");

        assertEquals("admin", service.currentGroup(id));
    }

    @Test
    void currentGroup_noLp_returnsDefault() {
        // 无 LP：一律回退 default（访客），不做本地推断（如审核记录）——杜绝虚假展示
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(false);

        assertEquals("default", service.currentGroup(id));
    }

    @Test
    void currentGroup_lpUnavailableAfterApprovedReview_returnsDefault() {
        // 即使存在 APPROVED 审核记录，无 LP 时也回退 default（权限状态只认 LP）
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(false);

        assertEquals("default", service.currentGroup(id));
    }

    // ---- 升级（LP track 钳位）----

    @Test
    void promote_success_returnsTargetGroupAndNotifies() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(promoter.promoteAsync(id)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture("builder"));
        when(promoter.playerName(id)).thenReturn(Optional.of("TestMember"));

        String target = service.promote(id);

        assertEquals("builder", target);
        verify(notifier).gameMessage(eq(id), contains("升级"));
        verify(notifier).groupEvent(eq("rank_promoted"), anyMap());
    }

    @Test
    void promote_atTop_endOfTrack_returnsNull() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(promoter.promoteAsync(id))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null)); // END_OF_TRACK

        String target = service.promote(id);

        assertNull(target);
        verify(notifier, never()).gameMessage(any(), anyString());
        verify(notifier, never()).groupEvent(anyString(), anyMap());
    }

    @Test
    void promote_noLuckPerms_returnsNull() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(false);

        String target = service.promote(id);

        assertNull(target);
        verify(promoter, never()).promoteAsync(any());
    }

    // ---- 降级（LP track 钳位）----

    @Test
    void demote_success_returnsTargetGroupAndNotifies() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(promoter.demoteAsync(id)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture("member"));
        when(promoter.playerName(id)).thenReturn(Optional.of("TestMember"));

        String target = service.demote(id);

        assertEquals("member", target);
        verify(notifier).gameMessage(eq(id), contains("降级"));
        verify(notifier).groupEvent(eq("rank_demoted"), anyMap());
    }

    @Test
    void demote_atBottom_removedFromFirstGroup_returnsNull() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(true);
        when(promoter.demoteAsync(id)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null)); // 链底

        String target = service.demote(id);

        assertNull(target);
        verify(notifier, never()).gameMessage(any(), anyString());
        verify(notifier, never()).groupEvent(anyString(), anyMap());
    }

    @Test
    void demote_noLuckPerms_returnsNull() {
        UUID id = UUID.randomUUID();
        when(promoter.isAvailable()).thenReturn(false);

        String target = service.demote(id);

        assertNull(target);
        verify(promoter, never()).demoteAsync(any());
    }

    // ---- 阈值配置 ----

    @Test
    void memberThresholdMinutes_configuredValue() {
        service = new RankService(store, promoter, 5); // 5h 阈值
        assertEquals(300L, service.memberThresholdMinutes());
    }

    // ---- 展示名 ----

    @Test
    void groupDisplayName_coversAllTiers() {
        assertEquals("管理员", RankService.groupDisplayName("admin"));
        assertEquals("建造者", RankService.groupDisplayName("builder"));
        assertEquals("成员", RankService.groupDisplayName("member"));
        assertEquals("访客", RankService.groupDisplayName("default"));
    }
}
