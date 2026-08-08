package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.track.DemotionResult;
import net.luckperms.api.track.PromotionResult;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * LuckPermsPromoter 测试（直接 LP API 版）。
 *
 * <p>本类仅在 LP 已启用时由装配层实例化（条件实例化），单测用 mockStatic 模拟 LP API：
 * 验证 promote/demote 的成功路径（含 saveUser 落库）、链顶/链底钳位翻译、
 * currentTrackGroup 最高组判定、isInGroup 查询。Noop 降级路径见 NoopRankPromoterTest。</p>
 */
class LuckPermsPromoterTest {

    private PluginManager pluginManager;
    private LuckPermsPromoter promoter;
    private MockedStatic<org.bukkit.Bukkit> bukkitMock;
    private MockedStatic<LuckPermsProvider> providerMock;
    private final UUID id = UUID.randomUUID();

    private LuckPerms api;
    private TrackManager trackManager;
    private Track track;
    private UserManager userManager;
    private User user;

    @BeforeEach
    void setUp() {
        pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled("LuckPerms")).thenReturn(true);
        bukkitMock = mockStatic(org.bukkit.Bukkit.class);
        bukkitMock.when(() -> org.bukkit.Bukkit.getPluginManager()).thenReturn(pluginManager);
        bukkitMock.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);

        promoter = new LuckPermsPromoter(u -> "TestPlayer");

        api = mock(LuckPerms.class);
        trackManager = mock(TrackManager.class);
        track = mock(Track.class);
        userManager = mock(UserManager.class);
        user = mock(User.class);

        when(trackManager.getTrack(LuckPermsPromoter.TRACK)).thenReturn(track);
        when(api.getTrackManager()).thenReturn(trackManager);
        when(api.getUserManager()).thenReturn(userManager);
        when(userManager.getUser(id)).thenReturn(user);
        // normalizeSingleGroup（promote/demote 前置归一键）依赖的 mock 链
        when(track.getGroups()).thenReturn(java.util.List.of("default", "member", "builder", "admin"));
        when(user.getInheritedGroups(any())).thenReturn(java.util.List.of());
        net.luckperms.api.model.data.NodeMap nodeMap = mock(net.luckperms.api.model.data.NodeMap.class);
        when(user.data()).thenReturn(nodeMap);
        when(userManager.saveUser(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        providerMock = mockStatic(LuckPermsProvider.class);
        providerMock.when(LuckPermsProvider::get).thenReturn(api);
        // QueryOptions.builder 内部依赖 ContextManager（queryOptionsGlobal 使用），统一 mock
        mockEmptyContext();
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
        providerMock.close();
    }

    private void mockEmptyContext() {
        net.luckperms.api.context.ContextManager cm = mock(net.luckperms.api.context.ContextManager.class);
        // ContextSetFactory 供 ImmutableContextSet.empty() 内部调用（真实静态方法依赖它）
        net.luckperms.api.context.ContextSetFactory factory = mock(net.luckperms.api.context.ContextSetFactory.class);
        net.luckperms.api.context.ImmutableContextSet emptyCtx =
                mock(net.luckperms.api.context.ImmutableContextSet.class);
        when(emptyCtx.isEmpty()).thenReturn(true);
        when(factory.immutableEmpty()).thenReturn(emptyCtx);
        when(cm.getContextSetFactory()).thenReturn(factory);
        when(cm.getContext(user)).thenReturn(Optional.empty());
        // QueryOptions.builder 内部经 ContextManager.queryOptionsBuilder(mode) 创建
        net.luckperms.api.query.QueryOptions.Builder qob = mock(net.luckperms.api.query.QueryOptions.Builder.class);
        when(cm.queryOptionsBuilder(any())).thenReturn(qob);
        when(qob.context(any())).thenReturn(qob);
        net.luckperms.api.query.QueryOptions qo = mock(net.luckperms.api.query.QueryOptions.class);
        when(qo.context()).thenReturn(emptyCtx);
        when(qob.build()).thenReturn(qo);
        when(api.getContextManager()).thenReturn(cm);
    }

    // ---- 可用性 ----

    @Test
    void isLuckPermsEnabled_lpLoaded_returnsTrue() {
        assertTrue(promoter.isLuckPermsEnabled());
        assertTrue(promoter.isAvailable());
    }

    // ---- promote 成功（含 saveUser 落库）----

    @Test
    void promote_success_returnsTargetGroupAndSaves() {
        mockEmptyContext();
        PromotionResult result = mock(PromotionResult.class);
        when(result.getStatus()).thenReturn(PromotionResult.Status.SUCCESS);
        when(result.getGroupTo()).thenReturn(Optional.of("member"));
        when(track.promote(any(), any())).thenReturn(result);
        when(userManager.saveUser(user)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("member", promoter.promote(id));
        verify(track).promote(eq(user), any(net.luckperms.api.context.ImmutableContextSet.class));
        verify(userManager, times(2)).saveUser(user); // normalize 清理落库 + promote 落库
    }

    @Test
    void promote_addedToFirstGroup_isSuccess() {
        mockEmptyContext();
        when(track.getGroups()).thenReturn(java.util.List.of("default", "member", "builder", "admin"));
        PromotionResult result = mock(PromotionResult.class);
        when(result.getStatus()).thenReturn(PromotionResult.Status.ADDED_TO_FIRST_GROUP);
        when(result.getGroupTo()).thenReturn(Optional.of("member"));
        when(track.promote(any(), any())).thenReturn(result);
        when(userManager.saveUser(user)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("member", promoter.promote(id));
    }

    @Test
    void promote_addedToFirstGroup_atChainStart_continuesToNextLevel() {
        // 回归：用户不在 track 时 LP 先加到链首（default），须连续 promote 到下一级（member），
        // 避免「升级为访客」的误导（$p u 新玩家应至少到 member）
        mockEmptyContext();
        when(track.getGroups()).thenReturn(java.util.List.of("default", "member", "builder", "admin"));
        PromotionResult first = mock(PromotionResult.class);
        when(first.getStatus()).thenReturn(PromotionResult.Status.ADDED_TO_FIRST_GROUP);
        when(first.getGroupTo()).thenReturn(Optional.of("default"));
        PromotionResult second = mock(PromotionResult.class);
        when(second.getStatus()).thenReturn(PromotionResult.Status.SUCCESS);
        when(second.getGroupTo()).thenReturn(Optional.of("member"));
        when(track.promote(any(), any())).thenReturn(first, second);
        when(userManager.saveUser(user)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("member", promoter.promote(id));
        verify(track, times(2)).promote(any(), any());
        verify(userManager, times(2)).saveUser(user); // normalize 清理落库 + 首入链落库
    }

    @Test
    void currentTrackGroup_usesGlobalQueryOptions() {
        // 回归：track 判定必须用 global 上下文查询——玩家在线时（world/gamemode 上下文）
        // 的叠加组不得参与判定（joker/TestMember 曾因此 /rank 误判 + AMBIGUOUS_CALL）
        mockEmptyContext();
        when(track.getGroups()).thenReturn(java.util.List.of("default", "member", "builder", "admin"));

        promoter.currentTrackGroup(id);

        verify(user)
                .getInheritedGroups(
                        argThat(qo -> qo.context() != null && qo.context().isEmpty()));
    }

    @Test
    void promote_endOfTrack_returnsNullWithoutSave() {
        mockEmptyContext();
        PromotionResult result = mock(PromotionResult.class);
        when(result.getStatus()).thenReturn(PromotionResult.Status.END_OF_TRACK);
        when(track.promote(any(), any())).thenReturn(result);

        assertNull(promoter.promote(id));
        verify(userManager, times(1)).saveUser(any()); // track 失败不落库，但 normalize 清理落库 1 次
    }

    // ---- demote 成功（含 saveUser 落库）----

    @Test
    void demote_success_returnsTargetGroupAndSaves() {
        mockEmptyContext();
        DemotionResult result = mock(DemotionResult.class);
        when(result.getStatus()).thenReturn(DemotionResult.Status.SUCCESS);
        when(result.getGroupTo()).thenReturn(Optional.of("builder"));
        when(track.demote(any(), any())).thenReturn(result);
        when(userManager.saveUser(user)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("builder", promoter.demote(id));
        verify(track).demote(eq(user), any(net.luckperms.api.context.ImmutableContextSet.class));
        verify(userManager, times(2)).saveUser(user); // normalize 清理落库 + 首入链落库
    }

    @Test
    void demote_removedFromFirstGroup_returnsNullWithoutSave() {
        mockEmptyContext();
        DemotionResult result = mock(DemotionResult.class);
        when(result.getStatus()).thenReturn(DemotionResult.Status.REMOVED_FROM_FIRST_GROUP);
        when(track.demote(any(), any())).thenReturn(result);

        assertNull(promoter.demote(id));
        verify(userManager, times(1)).saveUser(any()); // 链底不落库，但 normalize 清理落库 1 次
    }

    // ---- 组查询 ----

    @Test
    void currentTrackGroup_returnsHighestTrackGroup() {
        // 玩家在 member + builder → 取最高 builder
        when(track.getGroups()).thenReturn(java.util.List.of("default", "member", "builder", "admin"));
        java.util.List<net.luckperms.api.model.group.Group> inherited =
                java.util.List.of(mockGroup("member"), mockGroup("builder"));
        when(user.getInheritedGroups(any())).thenReturn(inherited);

        assertEquals("builder", promoter.currentTrackGroup(id));
    }

    private net.luckperms.api.model.group.Group mockGroup(String name) {
        net.luckperms.api.model.group.Group g = mock(net.luckperms.api.model.group.Group.class);
        when(g.getName()).thenReturn(name);
        return g;
    }

    @Test
    void isInGroup_memberPresent_returnsTrue() {
        java.util.List<net.luckperms.api.model.group.Group> inherited = java.util.List.of(mockGroup("member"));
        when(user.getInheritedGroups(any())).thenReturn(inherited);

        assertTrue(promoter.isInGroup(id, "member"));
        assertFalse(promoter.isInGroup(id, "admin"));
    }

    // ---- 玩家解析 ----

    @Test
    void resolvePlayerId_knownPlayer_returnsUuid() {
        org.bukkit.OfflinePlayer p = mock(org.bukkit.OfflinePlayer.class);
        when(p.hasPlayedBefore()).thenReturn(true);
        when(p.getUniqueId()).thenReturn(id);
        bukkitMock.when(() -> org.bukkit.Bukkit.getOfflinePlayer("TestPlayer")).thenReturn(p);

        assertEquals(id, promoter.resolvePlayerId("TestPlayer"));
    }

    @Test
    void playerName_resolvesViaResolver() {
        assertEquals(Optional.of("TestPlayer"), promoter.playerName(id));
    }
}
