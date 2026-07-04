package com.jokerhub.paper.plugin.orzmc.infra.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalBuilderTest {

    private Player player;
    private World world;
    private HashMap<String, String> interiorTargets;
    private PortalBuilder builder;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        world = mock(World.class);
        interiorTargets = new HashMap<>();
        builder = new PortalBuilder(interiorTargets);

        when(player.getWorld()).thenReturn(world);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getName()).thenReturn("world");
    }

    private Location mockLocation(Vector dir, int bx, int by, int bz) {
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getDirection()).thenReturn(dir);
        when(loc.getBlockX()).thenReturn(bx);
        when(loc.getBlockY()).thenReturn(by);
        when(loc.getBlockZ()).thenReturn(bz);
        when(player.getLocation()).thenReturn(loc);
        return loc;
    }

    @Test
    void build_facingEast_createsZAxisPortal() {
        mockLocation(new Vector(1, 0, 0), 100, 64, 200);
        mockBlocks(Material.AIR);

        PortalBuilder.PortalBuildResult result = builder.build(player, "target:25565");

        assertNotNull(result);
        assertEquals("world", result.worldName());
        assertEquals("target:25565", result.target());
        assertEquals(Axis.Z, result.portalAxis());
        assertTrue(result.cx() > 0);
        assertTrue(result.cy() > 0);
    }

    @Test
    void build_facingSouth_createsXAxisPortal() {
        mockLocation(new Vector(0, 0, 1), 100, 64, 200);
        mockBlocks(Material.AIR);

        PortalBuilder.PortalBuildResult result = builder.build(player, "other:25566");

        assertNotNull(result);
        assertEquals(Axis.X, result.portalAxis());
        assertEquals("other:25566", result.target());
    }

    @Test
    void build_placesObsidianFrame() {
        mockLocation(new Vector(0, 0, 1), 10, 60, 30);

        Block mockBlock = mock(Block.class, RETURNS_DEEP_STUBS);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mockBlock);
        when(mockBlock.getType()).thenReturn(Material.AIR);
        Orientable orientable = mock(Orientable.class);
        when(mockBlock.getBlockData()).thenReturn((BlockData) orientable);

        builder.build(player, "test:25565");

        verify(mockBlock, atLeast(14)).setType(eq(Material.OBSIDIAN), eq(false));
        verify(mockBlock, atLeast(6)).setType(eq(Material.NETHER_PORTAL), eq(false));
    }

    @Test
    void build_placesGoldBlockPad() {
        mockLocation(new Vector(-1, 0, 0), 50, 70, 100);

        Block mockBlock = mock(Block.class, RETURNS_DEEP_STUBS);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mockBlock);
        when(mockBlock.getType()).thenReturn(Material.AIR);
        Orientable orientable = mock(Orientable.class);
        when(mockBlock.getBlockData()).thenReturn((BlockData) orientable);

        builder.build(player, "pad:25565");

        verify(mockBlock, atLeast(6)).setType(eq(Material.GOLD_BLOCK), eq(false));
    }

    @Test
    void build_recordsInteriorTargets() {
        mockLocation(new Vector(0, 1, 0), 20, 50, 40);

        Block mockBlock = mock(Block.class, RETURNS_DEEP_STUBS);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mockBlock);
        when(mockBlock.getType()).thenReturn(Material.AIR);
        Orientable orientable = mock(Orientable.class);
        when(mockBlock.getBlockData()).thenReturn((BlockData) orientable);

        builder.build(player, "record:25565");

        assertFalse(interiorTargets.isEmpty());
        assertTrue(interiorTargets.values().stream().allMatch("record:25565"::equals));
    }

    @Test
    void build_findsEmptySpaceAboveBlockedArea() {
        mockLocation(new Vector(1, 0, 0), 0, 60, 0);

        Block obstructedBlock = mock(Block.class);
        when(obstructedBlock.getType()).thenReturn(Material.STONE);

        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        Orientable orientable = mock(Orientable.class);

        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            Block b = y == 60 ? obstructedBlock : airBlock;
            if (b == airBlock) when(airBlock.getBlockData()).thenReturn((BlockData) orientable);
            return b;
        });

        PortalBuilder.PortalBuildResult result = builder.build(player, "find:25565");

        assertNotNull(result);
        assertTrue(result.cy() > 60, "Should find space above blocked y=60");
    }

    private void mockBlocks(Material type) {
        Block mockBlock = mock(Block.class, RETURNS_DEEP_STUBS);
        when(mockBlock.getType()).thenReturn(type);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mockBlock);
        Orientable orientable = mock(Orientable.class);
        when(mockBlock.getBlockData()).thenReturn((BlockData) orientable);
    }
}
