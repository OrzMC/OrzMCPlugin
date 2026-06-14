package com.jokerhub.paper.plugin.orzmc.infra.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalServiceTest {

    private ConfigService configService;
    private PortalService portalService;
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
        portalService = new PortalService(configService, mock(WorldProvider.class));
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

    // ---- loadFromStorage ----

    @Test
    void loadFromStorage_emptyConfig() {
        portalService.loadFromStorage();
        // No portals loaded — no exception expected
        assertNull(portalService.findTarget(new Location(world, 0, 0, 0)));
    }

    @Test
    void loadFromStorage_skipsMalformedEntry() {
        // A key that doesn't parse to 4 colon-separated parts should be skipped
        portalCfg.set("portals.bad_target.bad_key_no_colons", "X");
        portalService.loadFromStorage();
        // Should not throw
        assertNull(portalService.findTarget(new Location(world, 0, 0, 0)));
    }

    @Test
    void loadFromStorage_multiplePortals() {
        loadPortal("world:100:64:200", "hub1:25565", "X");
        loadPortal("world:200:64:300", "hub2:25566", "Z");
        reloadPortals();

        assertEquals("hub1:25565", portalService.findTarget(new Location(world, 100, 64, 200)));
        assertEquals("hub2:25566", portalService.findTarget(new Location(world, 200, 64, 300)));
    }

    // ---- saveToStorage ----

    @Test
    void saveToStorage_persistsPortalEntries() {
        loadPortal("world:100:64:200", "host:25565", "X");
        reloadPortals();

        portalService.saveToStorage();

        verify(configService).saveConfig("portals");
    }

    @Test
    void saveToStorage_clearsOldEntriesBeforeWrite() {
        loadPortal("world:100:64:200", "host:25565", "X");
        reloadPortals();
        portalService.saveToStorage();

        int removed = portalService.removeByTarget("host:25565");
        assertEquals(1, removed);

        portalService.saveToStorage();
        verify(configService, times(3)).saveConfig("portals");
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

    // ---- helper: inject portal data into YAML config and reload ----

    private void loadPortal(String centerKey, String target, String axis) {
        String safeTarget = target.replace('.', '_');
        portalCfg.set("portals." + safeTarget + "." + centerKey, axis);
    }

    private void reloadPortals() {
        portalService.loadFromStorage();
    }
}
