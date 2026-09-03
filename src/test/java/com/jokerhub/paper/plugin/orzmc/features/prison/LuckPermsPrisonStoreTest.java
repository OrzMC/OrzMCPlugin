package com.jokerhub.paper.plugin.orzmc.features.prison;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * LuckPermsPrisonStore 测试（直接 LP API 版）。
 *
 * <p>验证坐牢/解除：成功路径（parent/primary 切换 + 原组/原位置元数据 + saveUser 落库）、
 * 幂等（已在牢房不覆盖原组记忆）、非坐牢玩家清理残留元数据、以及 <b>落库失败回滚内存态</b>
 * （评审 ②：saveUser 失败时 parent/meta/primary 全部还原到操作前，杜绝内存态与磁盘态漂移）。</p>
 *
 * <p>mock 范式对齐 LuckPermsPromoterTest/LuckPermsBootstrapTest：mockStatic(Bukkit +
 * LuckPermsProvider)；NodeMap 用 Mockito doAnswer 驱动真实内存列表，使 add/remove/clear 可读回。</p>
 */
class LuckPermsPrisonStoreTest {

    private static final String PRISON = PrisonLpGateway.PRISON_GROUP;
    private static final String META_GROUP = "prisoner_original_group";
    private static final String META_LOCATION = "prisoner_original_location";

    private MockedStatic<Bukkit> bukkitMock;
    private MockedStatic<LuckPermsProvider> providerMock;
    private final UUID id = UUID.randomUUID();

    private PluginManager pluginManager;
    private LuckPerms api;
    private UserManager userManager;
    private TrackManager trackManager;
    private Track track;
    private User user;
    /** 真实内存节点列表（NodeMap mock 的 doAnswer 落点，操作后可读回断言）。 */
    private List<Node> nodes;

    private LuckPermsPrisonStore store;

    @BeforeEach
    void setUp() {
        pluginManager = mock(PluginManager.class);
        when(pluginManager.isPluginEnabled("LuckPerms")).thenReturn(true);
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);

        api = mock(LuckPerms.class);
        userManager = mock(UserManager.class);
        trackManager = mock(TrackManager.class);
        track = mock(Track.class);
        user = mock(User.class);
        nodes = new ArrayList<>();

        NodeMap nodeMap = mock(NodeMap.class);
        when(nodeMap.toCollection()).thenAnswer(inv -> new ArrayList<>(nodes));
        doAnswer(inv -> {
                    nodes.add(inv.getArgument(0));
                    return null;
                })
                .when(nodeMap)
                .add(any(Node.class));
        doAnswer(inv -> {
                    nodes.remove(inv.getArgument(0));
                    return null;
                })
                .when(nodeMap)
                .remove(any(Node.class));
        doAnswer(inv -> {
                    nodes.removeIf(inv.getArgument(0));
                    return null;
                })
                .when(nodeMap)
                .clear(any(java.util.function.Predicate.class));

        when(user.data()).thenReturn(nodeMap);
        when(userManager.getUser(id)).thenReturn(user);
        when(api.getUserManager()).thenReturn(userManager);
        when(api.getTrackManager()).thenReturn(trackManager);
        when(trackManager.getTrack("rank")).thenReturn(track);
        when(track.getGroups()).thenReturn(List.of("default", "member", "builder", "admin"));
        when(userManager.saveUser(any())).thenReturn(CompletableFuture.completedFuture(null));

        NodeBuilderRegistry registry = mock(NodeBuilderRegistry.class);
        // forInheritance：记录 group 参数，build() 返回带 getGroupName 的 mock 节点
        InheritanceNode.Builder inhBuilder = mock(InheritanceNode.Builder.class);
        AtomicReference<String> groupRef = new AtomicReference<>();
        when(api.getNodeBuilderRegistry()).thenReturn(registry);
        when(registry.forInheritance()).thenReturn(inhBuilder);
        when(inhBuilder.group(anyString())).thenAnswer(inv -> {
            groupRef.set(inv.getArgument(0));
            return inhBuilder;
        });
        when(inhBuilder.build()).thenAnswer(inv -> {
            InheritanceNode node = mock(InheritanceNode.class);
            when(node.getGroupName()).thenReturn(groupRef.get());
            return node;
        });
        // forMeta：记录 key/value，build() 返回带 getMetaKey/getMetaValue 的 mock 节点
        MetaNode.Builder metaBuilder = mock(MetaNode.Builder.class);
        AtomicReference<String> metaKeyRef = new AtomicReference<>();
        AtomicReference<String> metaValueRef = new AtomicReference<>();
        when(registry.forMeta()).thenReturn(metaBuilder);
        when(metaBuilder.key(anyString())).thenAnswer(inv -> {
            metaKeyRef.set(inv.getArgument(0));
            return metaBuilder;
        });
        when(metaBuilder.value(anyString())).thenAnswer(inv -> {
            metaValueRef.set(inv.getArgument(0));
            return metaBuilder;
        });
        when(metaBuilder.build()).thenAnswer(inv -> {
            MetaNode node = mock(MetaNode.class);
            when(node.getMetaKey()).thenReturn(metaKeyRef.get());
            when(node.getMetaValue()).thenReturn(metaValueRef.get());
            return node;
        });

        providerMock = mockStatic(LuckPermsProvider.class);
        providerMock.when(LuckPermsProvider::get).thenReturn(api);

        store = new LuckPermsPrisonStore();
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
        providerMock.close();
    }

    // ---- 节点/状态构造辅助 ----

    private InheritanceNode mockInheritance(String group) {
        InheritanceNode node = mock(InheritanceNode.class);
        when(node.getGroupName()).thenReturn(group);
        return node;
    }

    private MetaNode mockMeta(String key, String value) {
        MetaNode node = mock(MetaNode.class);
        when(node.getMetaKey()).thenReturn(key);
        when(node.getMetaValue()).thenReturn(value);
        return node;
    }

    private boolean hasInheritance(String group) {
        return nodes.stream().anyMatch(n -> n instanceof InheritanceNode inh && group.equals(inh.getGroupName()));
    }

    private String metaValue(String key) {
        for (Node n : nodes) {
            if (n instanceof MetaNode meta && key.equals(meta.getMetaKey())) {
                return meta.getMetaValue();
            }
        }
        return null;
    }

    // ---- imprison ----

    @Test
    void imprison_success_switchesToPrisonAndSaves() {
        // builder 玩家坐牢：继承切 prison、primary 切 prison、原组/原位置写元数据并落库
        nodes.add(mockInheritance("builder"));
        when(user.getPrimaryGroup()).thenReturn("builder");

        PrisonLpGateway.ImprisonOutcome outcome =
                store.imprison(id, "world,1,2,3").join();

        assertTrue(outcome.success());
        assertEquals("builder", outcome.originalGroup());
        assertTrue(hasInheritance(PRISON));
        assertFalse(hasInheritance("builder"));
        assertEquals("builder", metaValue(META_GROUP));
        assertEquals("world,1,2,3", metaValue(META_LOCATION));
        verify(user).setPrimaryGroup(PRISON);
        verify(userManager).saveUser(user);
    }

    @Test
    void imprison_offlineWithoutLocation_skipsLocationMeta() {
        nodes.add(mockInheritance("builder"));
        when(user.getPrimaryGroup()).thenReturn("builder");

        PrisonLpGateway.ImprisonOutcome outcome = store.imprison(id, null).join();

        assertTrue(outcome.success());
        assertNull(metaValue(META_LOCATION));
        assertEquals("builder", metaValue(META_GROUP));
    }

    @Test
    void imprison_alreadyPrisoner_idempotentKeepsOriginalGroup() {
        // 已在牢房：幂等成功，保留既有原组记忆，不覆盖（防重复坐牢把原组覆盖成 prison）
        nodes.add(mockInheritance(PRISON));
        nodes.add(mockMeta(META_GROUP, "admin"));
        when(user.getPrimaryGroup()).thenReturn(PRISON);

        PrisonLpGateway.ImprisonOutcome outcome =
                store.imprison(id, "world,9,9,9").join();

        assertTrue(outcome.success());
        assertEquals("admin", outcome.originalGroup());
        assertEquals("admin", metaValue(META_GROUP)); // 原组未被覆盖
        assertNull(metaValue(META_LOCATION)); // 不写新位置
        verify(userManager, never()).saveUser(any());
    }

    @Test
    void imprison_saveUserFailure_rollsBackMemoryState() {
        // 评审 ②：落库失败必须回滚内存态（继承/primary/元数据全部还原），返回失败
        nodes.add(mockInheritance("builder"));
        when(user.getPrimaryGroup()).thenReturn("builder");
        when(userManager.saveUser(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("storage down")));

        PrisonLpGateway.ImprisonOutcome outcome =
                store.imprison(id, "world,1,2,3").join();

        assertFalse(outcome.success());
        assertEquals("builder", outcome.originalGroup());
        assertFalse(hasInheritance(PRISON)); // 已回滚：prison 继承消失
        assertTrue(hasInheritance("builder")); // 原组恢复
        assertNull(metaValue(META_GROUP));
        assertNull(metaValue(META_LOCATION));
        verify(user).setPrimaryGroup("builder"); // restore 还原 primary
    }

    // ---- release ----

    @Test
    void release_success_restoresOriginalGroupAndClearsMeta() {
        nodes.add(mockInheritance(PRISON));
        nodes.add(mockMeta(META_GROUP, "builder"));
        nodes.add(mockMeta(META_LOCATION, "world,5,6,7"));
        when(user.getPrimaryGroup()).thenReturn(PRISON);

        PrisonLpGateway.ReleaseOutcome outcome = store.release(id).join();

        assertTrue(outcome.success());
        assertTrue(outcome.wasPrisoner());
        assertEquals("builder", outcome.originalGroup());
        assertEquals("world,5,6,7", outcome.originalLocation());
        assertTrue(hasInheritance("builder")); // 恢复原组
        assertFalse(hasInheritance(PRISON));
        assertNull(metaValue(META_GROUP)); // 元数据清除
        assertNull(metaValue(META_LOCATION));
        verify(user).setPrimaryGroup("builder");
        verify(userManager).saveUser(user);
    }

    @Test
    void release_notPrisoner_cleansResidualMeta() {
        // 非坐牢玩家 release：清残留元数据、返回当前组、不传送（wasPrisoner=false）
        nodes.add(mockInheritance("builder"));
        nodes.add(mockMeta(META_GROUP, "builder"));
        nodes.add(mockMeta(META_LOCATION, "world,5,6,7"));
        when(user.getPrimaryGroup()).thenReturn("builder");

        PrisonLpGateway.ReleaseOutcome outcome = store.release(id).join();

        assertTrue(outcome.success());
        assertFalse(outcome.wasPrisoner());
        assertEquals("builder", outcome.originalGroup());
        assertTrue(hasInheritance("builder"));
        assertNull(metaValue(META_GROUP));
        assertNull(metaValue(META_LOCATION));
        verify(userManager).saveUser(user);
    }

    @Test
    void release_saveUserFailure_rollsBackToPrisonState() {
        // 评审 ②：release 落库失败回滚——仍保持 prison 组 + 原组记忆，返回失败
        nodes.add(mockInheritance(PRISON));
        nodes.add(mockMeta(META_GROUP, "builder"));
        nodes.add(mockMeta(META_LOCATION, "world,5,6,7"));
        when(user.getPrimaryGroup()).thenReturn(PRISON);
        when(userManager.saveUser(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("storage down")));

        PrisonLpGateway.ReleaseOutcome outcome = store.release(id).join();

        assertFalse(outcome.success());
        assertTrue(outcome.wasPrisoner());
        assertTrue(hasInheritance(PRISON)); // 回滚：仍坐牢
        assertFalse(hasInheritance("builder"));
        assertEquals("builder", metaValue(META_GROUP)); // 原组记忆还在
        assertEquals("world,5,6,7", metaValue(META_LOCATION));
    }

    // ---- isPrisoner ----

    @Test
    void isPrisoner_primaryGroupIsPrison_returnsTrue() {
        when(user.getPrimaryGroup()).thenReturn(PRISON);
        assertTrue(store.isPrisoner(id));
    }

    @Test
    void isPrisoner_builderUser_returnsFalse() {
        nodes.add(mockInheritance("builder"));
        when(user.getPrimaryGroup()).thenReturn("builder");
        assertFalse(store.isPrisoner(id));
    }

    @Test
    void isPrisoner_lpNotLoaded_returnsFalse() {
        // 软依赖缺失：isAvailable 短路直接 false，不触任何 LP API
        when(pluginManager.isPluginEnabled("LuckPerms")).thenReturn(false);
        assertFalse(store.isPrisoner(id));
        verify(api, never()).getUserManager();
    }
}
