package com.jokerhub.paper.plugin.orzmc.features.tnt;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TntPolicyTest {

    private TntPolicy policy;

    @BeforeEach
    void setUp() {
        TypedConfigs.TntConfig cfg = new TypedConfigs.TntConfig(
                true,
                true,
                10,
                2000L,
                List.of(Map.of("minX", 0, "maxX", 10, "minY", 0, "maxY", 255, "minZ", 0, "maxZ", 10, "world", "world")),
                List.of("ARMOR_STAND", "ITEM_FRAME"));
        policy = new TntPolicy(cfg);
    }

    @Test
    void isEnableTnt() {
        assertTrue(policy.isEnableTnt());
    }

    @Test
    void isEnableRespawnAnchor() {
        assertTrue(policy.isEnableRespawnAnchor());
    }

    @Test
    void getPlaceCooldownSeconds() {
        assertEquals(10, policy.getPlaceCooldownSeconds());
    }

    @Test
    void getNotifyThrottleMs() {
        assertEquals(2000L, policy.getNotifyThrottleMs());
    }

    @Test
    void getExemptEntities() {
        assertEquals(List.of("ARMOR_STAND", "ITEM_FRAME"), policy.getExemptEntities());
    }

    // ---- Region whitelist ----

    @Test
    void isNotInWhiteList_insideRegion_returnsFalse() {
        assertFalse(policy.isNotInWhiteList("world", 5, 64, 5));
    }

    @Test
    void isNotInWhiteList_outsideRegion_returnsTrue() {
        assertTrue(policy.isNotInWhiteList("world", 50, 64, 50));
    }

    @Test
    void isNotInWhiteList_differentWorld_returnsTrue() {
        assertTrue(policy.isNotInWhiteList("other_world", 5, 64, 5));
    }

    @Test
    void isNotInWhiteList_edgeMin_inside() {
        assertFalse(policy.isNotInWhiteList("world", 0, 0, 0));
    }

    @Test
    void isNotInWhiteList_edgeMax_inside() {
        assertFalse(policy.isNotInWhiteList("world", 10, 255, 10));
    }

    @Test
    void isNotInWhiteList_withLocation() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        assertFalse(policy.isNotInWhiteList(new Location(world, 5, 64, 5)));
    }

    @Test
    void isNotInWhiteList_withLocation_outside() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        assertTrue(policy.isNotInWhiteList(new Location(world, 50, 64, 50)));
    }

    // ---- Region.contains ----

    @Test
    void regionContains_matches() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        assertTrue(new TntPolicy.Region(0, 10, 0, 255, 0, 10, "world").contains(new Location(world, 5, 64, 5)));
    }

    @Test
    void regionContains_wrongWorld() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("nether");
        assertFalse(new TntPolicy.Region(0, 10, 0, 255, 0, 10, "world").contains(new Location(world, 5, 64, 5)));
    }

    @Test
    void regionContainsByName_matches() {
        assertTrue(new TntPolicy.Region(0, 10, 0, 255, 0, 10, "world").contains("world", 5, 64, 5));
    }

    @Test
    void regionContainsByName_outOfBounds() {
        assertFalse(new TntPolicy.Region(0, 10, 0, 255, 0, 10, "world").contains("world", 20, 64, 20));
    }
}
