package com.jokerhub.paper.plugin.orzmc.infra.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.WorldProvider;
import com.jokerhub.paper.plugin.orzmc.infra.server.RegionSchedulerProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Axis;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalCleanerTest {

    private WorldProvider worldProvider;
    private World world;
    private Logger logger;
    private RegionSchedulerProvider provider;
    private PortalCleaner cleaner;

    @BeforeEach
    void setUp() {
        worldProvider = mock(WorldProvider.class);
        world = mock(World.class);
        logger = mock(Logger.class);
        // 默认同步直跑（不投递）：让既有断言在任务体内即时执行；投递目标用 captureProvider 单独验证
        provider = (w, cx, cz, task) -> task.run();
        cleaner = new PortalCleaner(worldProvider, logger, provider);

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

        ArmorStand matchingStand = mock(ArmorStand.class);
        when(matchingStand.customName()).thenReturn(Component.text("跨服传送 target:25565"));
        when(matchingStand.getLocation()).thenReturn(new Location(world, 0.5, 66.0, 0.5));

        // Folia：实体清理走 chunk.getEntities()，标签在 chunk(0,0) 内
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Chunk standChunk = mock(Chunk.class);
        when(standChunk.getEntities()).thenReturn(new Entity[] {matchingStand});
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(mock(Chunk.class));
        when(world.getChunkAt(0, 0)).thenReturn(standChunk);

        PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        verify(matchingStand).remove();
    }

    @Test
    void clear_skipsNonMatchingArmorStands() {
        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);

        ArmorStand nonMatching = mock(ArmorStand.class);
        when(nonMatching.customName()).thenReturn(Component.text("Some other label"));
        when(nonMatching.getLocation()).thenReturn(new Location(world, 0.5, 66.0, 0.5));

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Chunk standChunk = mock(Chunk.class);
        when(standChunk.getEntities()).thenReturn(new Entity[] {nonMatching});
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(mock(Chunk.class));
        when(world.getChunkAt(0, 0)).thenReturn(standChunk);

        PortalService.PortalDef def = new PortalService.PortalDef("world", 0, 64, 0, Axis.X, "target:25565");
        cleaner.clear(def);

        verify(nonMatching, never()).remove();
    }

    @Test
    void clear_skipsNullNamedArmorStands() {
        Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);

        ArmorStand nullNameStand = mock(ArmorStand.class);
        when(nullNameStand.customName()).thenReturn(null);
        when(nullNameStand.getLocation()).thenReturn(new Location(world, 0.5, 66.0, 0.5));

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Chunk standChunk = mock(Chunk.class);
        when(standChunk.getEntities()).thenReturn(new Entity[] {nullNameStand});
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(mock(Chunk.class));
        when(world.getChunkAt(0, 0)).thenReturn(standChunk);

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

    // ---- Folia 区域亲和：方块/实体清理必须投递到足迹覆盖的 chunk ----

    @Test
    void clear_dispatchesToChunksCoveringFootprint() {
        // cx=14（轴 X）→ 足迹 x∈[12,17] 跨 chunk(0,*) 与 chunk(1,*)；z∈[19,21] 落在 chunk 1
        List<Object[]> dispatches = new ArrayList<>();
        cleaner = new PortalCleaner(worldProvider, logger, (w, cx, cz, task) -> {
            dispatches.add(new Object[] {cx, cz});
            task.run();
        });
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(mock(Chunk.class));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        PortalService.PortalDef def = new PortalService.PortalDef("world", 14, 64, 20, Axis.X, "target:25565");
        cleaner.clear(def);

        List<Long> dispatchedKeys =
                dispatches.stream().map(d -> key((int) d[0], (int) d[1])).toList();
        // 方块足迹 chunk：(14-2)>>4=0..(14+3)>>4=1，z 方向 (20-1)>>4=1..(20+1)>>4=1
        assertTrue(dispatchedKeys.contains(key(0, 1)), "应投递 chunk(0,1)，实际: " + dispatchedKeys);
        assertTrue(dispatchedKeys.contains(key(1, 1)), "应投递 chunk(1,1)，实际: " + dispatchedKeys);
    }

    private static long key(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }
}
