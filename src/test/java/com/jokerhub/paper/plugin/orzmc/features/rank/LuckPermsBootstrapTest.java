package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** LuckPermsBootstrap 测试：幂等、只补缺失、已有不覆盖、先组后 track。 */
class LuckPermsBootstrapTest {

    private LuckPerms api;
    private TrackManager trackManager;
    private GroupManager groupManager;
    private Group defaultGroup;
    private Logger logger;
    /** 已「创建」的组（createAndLoadGroup 完成后 getGroup 返回）。 */
    private final Map<String, Group> created = new HashMap<>();

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
        created.clear();
        created.put("default", defaultGroup);
        // 组缺失时 getGroup=null；createAndLoadGroup 完成后存入 created（track 建链可引用）
        when(groupManager.getGroup(anyString())).thenAnswer(inv -> created.get(inv.getArgument(0)));
        when(groupManager.createAndLoadGroup(anyString())).thenAnswer(inv -> {
            Group g = mock(Group.class);
            when(g.data()).thenReturn(mock(net.luckperms.api.model.data.NodeMap.class));
            created.put(inv.getArgument(0), g);
            return CompletableFuture.completedFuture(g);
        });
        when(groupManager.saveGroup(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private void mockTrackMissing() {
        when(trackManager.getTrack("rank")).thenReturn(null);
        Track track = mock(Track.class);
        when(trackManager.createAndLoadTrack("rank")).thenReturn(CompletableFuture.completedFuture(track));
        when(trackManager.saveTrack(track)).thenReturn(CompletableFuture.completedFuture(null));
    }

    private void mockTrackExists() {
        when(trackManager.getTrack("rank")).thenReturn(mock(Track.class));
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
        verify(groupManager, times(3)).saveGroup(any());
        assertInheritanceAdded(created.get("member"), "default");
        assertInheritanceAdded(created.get("builder"), "member");
        assertInheritanceAdded(created.get("admin"), "builder");
        // 再建 track（引用已创建的组）
        verify(trackManager).createAndLoadTrack("rank");
        verify(trackManager, atLeastOnce()).saveTrack(any());
    }

    @Test
    void initialize_trackExists_skipsTrackCreation() {
        mockTrackExists();

        new LuckPermsBootstrap(api, logger).initialize();

        verify(trackManager, never()).createAndLoadTrack("rank");
        verify(trackManager, never()).saveTrack(any());
    }

    @Test
    void initialize_groupsExist_skipsGroupCreation() {
        mockTrackExists();
        created.put("member", mock(Group.class));
        created.put("builder", mock(Group.class));
        created.put("admin", mock(Group.class));

        new LuckPermsBootstrap(api, logger).initialize();

        verify(groupManager, never()).createAndLoadGroup(any());
        verify(groupManager, never()).saveGroup(any());
    }

    @Test
    void initialize_partialMissing_onlyCreatesMissing() {
        mockTrackExists();
        created.put("member", mock(Group.class));
        created.put("builder", mock(Group.class));
        // admin 缺失 → 只建 admin（继承 builder）

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
