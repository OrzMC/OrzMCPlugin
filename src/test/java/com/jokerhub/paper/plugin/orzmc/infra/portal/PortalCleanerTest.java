package com.jokerhub.paper.plugin.orzmc.infra.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalCleanerTest {

    private WorldProvider worldProvider;
    private World world;
    private Logger logger;
    private PortalCleaner cleaner;

    @BeforeEach
    void setUp() {
        worldProvider = mock(WorldProvider.class);
        world = mock(World.class);
        logger = mock(Logger.class);
        cleaner = new PortalCleaner(worldProvider, logger);

        when(worldProvider.getWorld("world")).thenReturn(world);
    }

    @Test
    void clear_worldNotExists_logsWarning() {
        when(worldProvider.getWorld("void")).thenReturn(null);

        PortalService.PortalDef def = new PortalService.PortalDef("void", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        verify(logger).warning(contains("不存在"));
    }

    @Test
    void clear_removesObsidianAndPortalBlocks() {
        Block mockBlock = mock(Block.class);
        when(mockBlock.getType()).thenReturn(Material.OBSIDIAN);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mockBlock);

        // Mock chunk loading
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(null);

        PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        // Verify obsidian/portal blocks are set to air
        verify(mockBlock, atLeastOnce()).setType(eq(Material.AIR), eq(false));
    }

    @Test
    void clear_removesMatchingArmorStands() {
        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(null);

        ArmorStand matchingStand = mock(ArmorStand.class);
        when(matchingStand.customName()).thenReturn(Component.text("跨服传送 target:25565"));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(matchingStand));

        PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        verify(matchingStand).remove();
    }

    @Test
    void clear_skipsNonMatchingArmorStands() {
        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(null);

        ArmorStand nonMatching = mock(ArmorStand.class);
        when(nonMatching.customName()).thenReturn(Component.text("Some other label"));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(nonMatching));

        PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        verify(nonMatching, never()).remove();
    }

    @Test
    void clear_skipsNullNamedArmorStands() {
        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(null);

        ArmorStand nullNameStand = mock(ArmorStand.class);
        when(nullNameStand.customName()).thenReturn(null);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(nullNameStand));

        PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        verify(nullNameStand, never()).remove();
    }

    @Test
    void removeIfPortalBlock_onlyClearsSpecificMaterials() {
        // Test via clear() - only OBSIDIAN, NETHER_PORTAL, GLOWSTONE, END_ROD,
        // LIGHT_BLUE_STAINED_GLASS, STONE_BRICKS should be removed
        Block dirtBlock = mock(Block.class);
        when(dirtBlock.getType()).thenReturn(Material.DIRT);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(dirtBlock);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(null);

        PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        // Dirt should NOT be removed
        verify(dirtBlock, never()).setType(any(), anyBoolean());
    }

    @Test
    void removeIfPortalBlock_clearsPortalDecorBlocks() {
        // Test that portal-related materials ARE cleared
        for (Material mat : List.of(
                Material.NETHER_PORTAL,
                Material.GLOWSTONE,
                Material.END_ROD,
                Material.LIGHT_BLUE_STAINED_GLASS,
                Material.STONE_BRICKS)) {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(mat);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
            when(world.getChunkAt(anyInt(), anyInt())).thenReturn(null);

            PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
            cleaner.clear(def);

            verify(block, atLeastOnce()).setType(eq(Material.AIR), eq(false));
        }
    }
}
