package com.jokerhub.paper.plugin.orzmc.infra.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.server.RegionSchedulerProvider;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalServiceTest {

    private ConfigService configService;
    private PortalService portalService;
    private WorldProvider worldProvider;
    private World world;

    private FileConfiguration portalCfg;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        portalCfg = new YamlConfiguration();
        when(configService.getConfig("portals")).thenReturn(portalCfg);

        // WorldProvider returns null by default (mock), so spawnLabel/clearPortalBlocks
        // short-circuit on null world check — tests focus on map/state logic only.
        worldProvider = mock(WorldProvider.class);
        portalService = new PortalService(configService, worldProvider);
    }

    // ---- findTarget ----

    @Test
    void findTarget_returnsNull_whenNoPortals() {
        Location loc = new Location(world, 100, 64, 200);
        assertNull(portalService.findTarget(loc));
    }

    @Test
    void findTarget_exactMatch() {
        loadPortal("world:100:64:200", "host:25565", "X");
        reloadPortals();

        Location loc = new Location(world, 100, 64, 200);
        assertEquals("host:25565", portalService.findTarget(loc));
    }

    @Test
    void findTarget_neighborFallback() {
        loadPortal("world:100:64:200", "host:25565", "X");
        reloadPortals();

        // Player at (101, 65, 201) — 1 block away each axis — still matches
        Location loc = new Location(world, 101, 65, 201);
        assertEquals("host:25565", portalService.findTarget(loc));
    }

    @Test
    void findTarget_outsideNeighbor_returnsNull() {
        loadPortal("world:100:64:200", "host:25565", "X");
        reloadPortals();

        // Interior reaches (101,63,200); 2 blocks beyond that → outside 3x3x3
        Location loc = new Location(world, 103, 64, 200);
        assertNull(portalService.findTarget(loc));
    }

    // ---- findTargetExact（无邻域容差，供 Folia move 补偿路径）----

    @Test
    void findTargetExact_hitOnInteriorBlock() {
        loadPortal("world:100:64:200", "host:25565", "X");
        reloadPortals();

        // (100,64,200) 是传送门内部格 → 精确命中
        assertEquals("host:25565", portalService.findTargetExact(new Location(world, 100, 64, 200)));
    }

    @Test
    void findTargetExact_neighborBlock_noFallback() {
        loadPortal("world:100:64:200", "host:25565", "X");
        reloadPortals();

        // (101,65,201) 距内部格 1 格：findTarget 邻域容差会命中，findTargetExact 必须返回 null
        Location loc = new Location(world, 101, 65, 201);
        assertNull(portalService.findTargetExact(loc));
        assertEquals("host:25565", portalService.findTarget(loc));
    }

    // ---- setup (loadFromStorage) ----

    @Test
    void setup_emptyConfig() {
        portalService.setup();
        // No portals loaded — no exception expected
        assertNull(portalService.findTarget(new Location(world, 0, 0, 0)));
    }

    @Test
    void setup_skipsMalformedEntry() {
        // A key that doesn't parse to 4 colon-separated parts should be skipped
        portalCfg.set("portals.bad_target.bad_key_no_colons", "X");
        portalService.setup();
        // Should not throw
        assertNull(portalService.findTarget(new Location(world, 0, 0, 0)));
    }

    @Test
    void setup_multiplePortals() {
        loadPortal("world:100:64:200", "hub1:25565", "X");
        loadPortal("world:200:64:300", "hub2:25566", "Z");
        reloadPortals();

        assertEquals("hub1:25565", portalService.findTarget(new Location(world, 100, 64, 200)));
        assertEquals("hub2:25566", portalService.findTarget(new Location(world, 200, 64, 300)));
    }

    // ---- removeByTarget ----

    @Test
    void removeByTarget_removesMatchingPortal() {
        loadPortal("world:100:64:200", "host:25565", "X");
        loadPortal("world:200:64:300", "other:25566", "Z");
        reloadPortals();

        int removed = portalService.removeByTarget("host:25565");
        assertEquals(1, removed);

        // Removed portal no longer findable
        assertNull(portalService.findTarget(new Location(world, 100, 64, 200)));
        // Other portal still exists
        assertEquals("other:25566", portalService.findTarget(new Location(world, 200, 64, 300)));
    }

    @Test
    void removeByTarget_noMatch_returnsZero() {
        int removed = portalService.removeByTarget("nonexistent:25565");
        assertEquals(0, removed);
    }

    // ---- Folia 区域亲和：标签渲染经 region scheduler 投递到传送门中心所在 chunk ----

    @Test
    void setup_dispatchesLabelSpawnToPortalChunk() {
        when(worldProvider.getWorld("world")).thenReturn(world);
        List<Object[]> dispatches = new ArrayList<>();
        // 只记录不执行任务体，避免 spawnEntity 返回 null 的模拟噪音
        portalService = new PortalService(configService, worldProvider, (RegionSchedulerProvider)
                (w, cx, cz, task) -> dispatches.add(new Object[] {cx, cz}));

        loadPortal("world:100:64:200", "host:25565", "X");
        portalService.setup();

        // spawnLabel 投递到传送门中心 (100,200) 所在 chunk(6,12)
        boolean spawned = dispatches.stream().anyMatch(d -> (int) d[0] == 6 && (int) d[1] == 12);
        assertTrue(spawned, "应投递 spawnLabel 到 chunk(6,12)，实际: " + dispatchKeys(dispatches));
    }

    @Test
    void removeByTarget_dispatchesCleanupToPortalChunks() {
        when(worldProvider.getWorld("world")).thenReturn(world);
        List<Object[]> dispatches = new ArrayList<>();
        portalService = new PortalService(configService, worldProvider, (RegionSchedulerProvider)
                (w, cx, cz, task) -> dispatches.add(new Object[] {cx, cz}));

        loadPortal("world:100:64:200", "host:25565", "X");
        portalService.setup();
        dispatches.clear();

        portalService.removeByTarget("host:25565");

        // cleaner.clear（3×3 标签网格）与 labelRenderer.clearLabels 都经 region scheduler 投递
        assertFalse(dispatches.isEmpty(), "拆除应触发方块/标签清理的 region 投递");
    }

    private static String dispatchKeys(List<Object[]> dispatches) {
        return dispatches.stream().map(d -> d[0] + "," + d[1]).toList().toString();
    }

    // ---- helper: inject portal data into YAML config and reload ----

    private void loadPortal(String centerKey, String target, String axis) {
        String safeTarget = target.replace('.', '_');
        portalCfg.set("portals." + safeTarget + "." + centerKey, axis);
    }

    private void reloadPortals() {
        portalService.setup();
    }
}
