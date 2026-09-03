package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** LuckPermsBootstrap 测试：幂等、继承链/track 链序由插件保证、权限节点不动。 */
class LuckPermsBootstrapTest {

    private LuckPerms api;
    private TrackManager trackManager;
    private GroupManager groupManager;
    private Group defaultGroup;
    private Logger logger;
    /** 已「创建」的组（createAndLoadGroup 完成后 getGroup 返回）。 */
    private final Map<String, Group> created = new HashMap<>();

    private final Map<String, String> groupParents = new HashMap<>();

    @BeforeEach
    void setUp() {
        api = mock(LuckPerms.class);
        trackManager = mock(TrackManager.class);
        groupManager = mock(GroupManager.class);
        defaultGroup = mock(Group.class);
        logger = Logger.getLogger("test");
        when(api.getTrackManager()).thenReturn(trackManager);
        when(api.getGroupManager()).thenReturn(groupManager);
        // NodeBuilderRegistry：group(name) 记录参数，build() 返回带 getGroupName 的 node
        NodeBuilderRegistry registry = mock(NodeBuilderRegistry.class);
        InheritanceNode.Builder nb = mock(InheritanceNode.Builder.class);
        AtomicReference<String> lastGroup = new AtomicReference<>();
        when(api.getNodeBuilderRegistry()).thenReturn(registry);
        when(registry.forInheritance()).thenReturn(nb);
        when(nb.group(anyString())).thenAnswer(inv -> {
            lastGroup.set(inv.getArgument(0));
            return nb;
        });
        when(nb.build()).thenAnswer(inv -> {
            InheritanceNode node = mock(InheritanceNode.class);
            when(node.getGroupName()).thenReturn(lastGroup.get());
            return node;
        });
        // forPermission：permission(name) 记录参数，build() 返回带 getPermission 的 node（prison 组用）
        PermissionNode.Builder permNb = mock(PermissionNode.Builder.class);
        AtomicReference<String> lastPermission = new AtomicReference<>();
        when(registry.forPermission()).thenReturn(permNb);
        when(permNb.permission(anyString())).thenAnswer(inv -> {
            lastPermission.set(inv.getArgument(0));
            return permNb;
        });
        when(permNb.build()).thenAnswer(inv -> {
            PermissionNode node = mock(PermissionNode.class);
            when(node.getPermission()).thenReturn(lastPermission.get());
            return node;
        });
        created.clear();
        groupParents.clear();
        created.put("default", defaultGroup);
        // 组缺失时 getGroup=null；createAndLoadGroup 完成后存入 created（track 建链可引用）
        when(groupManager.getGroup(anyString())).thenAnswer(inv -> created.get(inv.getArgument(0)));
        when(groupManager.createAndLoadGroup(anyString())).thenAnswer(inv -> {
            Group g = mockGroup(inv.getArgument(0), null);
            created.put(inv.getArgument(0), g);
            return CompletableFuture.completedFuture(g);
        });
        when(groupManager.saveGroup(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    /** 创建带指定继承节点的组 mock（parent=null 表示无继承）。 */
    private Group mockGroup(String name, String parent) {
        Group g = mock(Group.class);
        NodeMap nm = mock(NodeMap.class);
        java.util.Collection<Node> nodes = new java.util.ArrayList<>();
        if (parent != null) {
            InheritanceNode node = mock(InheritanceNode.class);
            when(node.getGroupName()).thenReturn(parent);
            nodes.add(node);
        }
        when(nm.toCollection()).thenReturn(nodes);
        when(g.data()).thenReturn(nm);
        if (parent != null) {
            groupParents.put(name, parent);
        }
        return g;
    }

    /** 已存在的 prison 组（带 essentials.msg 权限，无继承——独立组）。 */
    private Group mockPrisonGroupWithMsg() {
        Group g = mock(Group.class);
        NodeMap nm = mock(NodeMap.class);
        java.util.Collection<Node> nodes = new java.util.ArrayList<>();
        PermissionNode perm = mock(PermissionNode.class);
        when(perm.getPermission()).thenReturn("essentials.msg");
        nodes.add(perm);
        when(nm.toCollection()).thenReturn(nodes);
        when(g.data()).thenReturn(nm);
        return g;
    }

    private void mockTrackMissing() {
        when(trackManager.getTrack("rank")).thenReturn(null);
        Track track = mock(Track.class);
        when(trackManager.createAndLoadTrack("rank")).thenReturn(CompletableFuture.completedFuture(track));
        when(trackManager.saveTrack(track)).thenReturn(CompletableFuture.completedFuture(null));
    }

    private void mockTrackExists(List<String> chain) {
        Track track = mock(Track.class);
        when(track.getGroups()).thenReturn(chain);
        when(trackManager.getTrack("rank")).thenReturn(track);
    }

    private void assertInheritanceAdded(Group group, String parentName) {
        verify(group.data())
                .add(argThat(node -> node instanceof InheritanceNode
                        && ((InheritanceNode) node).getGroupName().equals(parentName)));
    }

    @Test
    void initialize_createsMissingGroupsThenTrack() {
        mockTrackMissing();

        new LuckPermsBootstrap(api, logger).initialize();

        // 组先建（member/builder/admin + 继承链）
        verify(groupManager).createAndLoadGroup("member");
        verify(groupManager).createAndLoadGroup("builder");
        verify(groupManager).createAndLoadGroup("admin");
        assertInheritanceAdded(created.get("member"), "default");
        assertInheritanceAdded(created.get("builder"), "member");
        assertInheritanceAdded(created.get("admin"), "builder");
        // 再建 track（引用已创建的组）
        verify(trackManager).createAndLoadTrack("rank");
        verify(trackManager, atLeastOnce()).saveTrack(any());
    }

    @Test
    void initialize_trackExists_chainMatches_skipsRebuild() {
        mockTrackExists(List.of("default", "member", "builder", "admin"));

        new LuckPermsBootstrap(api, logger).initialize();

        verify(trackManager, never()).createAndLoadTrack("rank");
        verify(trackManager, never()).deleteTrack(any());
        verify(trackManager, never()).saveTrack(any());
    }

    @Test
    void initialize_trackExists_chainMismatch_rebuilds() {
        mockTrackExists(List.of("default", "member", "admin"));
        Track fresh = mock(Track.class);
        when(trackManager.createAndLoadTrack("rank")).thenReturn(CompletableFuture.completedFuture(fresh));
        when(trackManager.saveTrack(fresh)).thenReturn(CompletableFuture.completedFuture(null));

        new LuckPermsBootstrap(api, logger).initialize();

        verify(trackManager).deleteTrack(any());
        verify(trackManager).createAndLoadTrack("rank");
        verify(trackManager, atLeastOnce()).saveTrack(any());
    }

    @Test
    void initialize_groupsExist_inheritanceCorrect_skipsSave() {
        mockTrackExists(List.of("default", "member", "builder", "admin"));
        created.put("member", mockGroup("member", "default"));
        created.put("builder", mockGroup("builder", "member"));
        created.put("admin", mockGroup("admin", "builder"));
        // prison 组已存在且带 essentials.msg（独立组无继承）→ 无需 create/save
        created.put("prison", mockPrisonGroupWithMsg());

        new LuckPermsBootstrap(api, logger).initialize();

        verify(groupManager, never()).createAndLoadGroup(any());
        verify(groupManager, never()).saveGroup(any());
        verify(groupManager, never()).deleteGroup(any());
    }

    @Test
    void initialize_prisonGroupMissing_createsWithMsgPermission() {
        mockTrackMissing();
        created.put("member", mockGroup("member", "default"));
        created.put("builder", mockGroup("builder", "member"));
        created.put("admin", mockGroup("admin", "builder"));
        when(groupManager.getGroup("prison")).thenReturn(null);

        new LuckPermsBootstrap(api, logger).initialize();

        verify(groupManager).createAndLoadGroup("prison");
        // prison 组建好后挂 essentials.msg 权限并落库
        verify(created.get("prison").data())
                .add(argThat(node -> node instanceof PermissionNode
                        && "essentials.msg".equals(((PermissionNode) node).getPermission())));
        verify(groupManager, atLeastOnce()).saveGroup(created.get("prison"));
    }

    @Test
    void initialize_groupsExist_wrongInheritance_correctsParent() {
        mockTrackExists(List.of("default", "member", "builder", "admin"));
        // member/admin 继承正确；builder 继承 default（错误，应为 member）
        created.put("member", mockGroup("member", "default"));
        created.put("builder", mockGroup("builder", "default"));
        created.put("admin", mockGroup("admin", "builder"));

        new LuckPermsBootstrap(api, logger).initialize();

        // 只有 builder 被校正（清除旧继承 + 重挂 member）
        verify(builderData()).clear(any(java.util.function.Predicate.class));
        assertInheritanceAdded(created.get("builder"), "member");
        verify(groupManager, times(1)).saveGroup(created.get("builder"));
    }

    private NodeMap builderData() {
        return created.get("builder").data();
    }

    @Test
    void initialize_partialMissing_onlyCreatesMissing() {
        mockTrackExists(List.of("default", "member", "builder", "admin"));
        created.put("member", mockGroup("member", "default"));
        created.put("builder", mockGroup("builder", "member"));
        // admin 缺失 → 只建 admin（继承 builder）
        when(groupManager.getGroup("admin")).thenReturn(null);

        new LuckPermsBootstrap(api, logger).initialize();

        verify(groupManager).createAndLoadGroup("admin");
        verify(groupManager, never()).createAndLoadGroup("member");
        verify(groupManager, never()).createAndLoadGroup("builder");
        assertInheritanceAdded(created.get("admin"), "builder");
    }

    @Test
    void initialize_trackCreateFailure_logsWarningWithoutCrash() {
        when(trackManager.getTrack("rank")).thenReturn(null);
        when(trackManager.createAndLoadTrack("rank"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("storage down")));

        assertDoesNotThrow(() -> new LuckPermsBootstrap(api, logger).initialize());
        verify(trackManager, never()).saveTrack(any());
    }
}
