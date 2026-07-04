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
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalLabelRendererTest {

    private WorldProvider worldProvider;
    private World world;
    private Logger logger;
    private PortalLabelRenderer renderer;

    @BeforeEach
    void setUp() {
        worldProvider = mock(WorldProvider.class);
        world = mock(World.class);
        logger = mock(Logger.class);
        renderer = new PortalLabelRenderer(worldProvider, logger);

        when(worldProvider.getWorld("world")).thenReturn(world);
    }

    @Test
    void spawnLabel_worldNotExists_doesNothing() {
        when(worldProvider.getWorld("void")).thenReturn(null);

        renderer.spawnLabel("void", 0, 64, 0, "target:25565");

        verify(world, never()).spawnEntity(any(), any());
    }

    @Test
    void spawnLabel_createsTwoArmorStands() {
        ArmorStand mockStand = mock(ArmorStand.class);
        when(world.spawnEntity(any(Location.class), eq(EntityType.ARMOR_STAND))).thenReturn(mockStand);

        renderer.spawnLabel("world", 100, 64, 200, "target:25565");

        // Two armor stands: title and address
        verify(world, times(2)).spawnEntity(any(Location.class), eq(EntityType.ARMOR_STAND));
        verify(mockStand, times(2)).setInvisible(true);
        verify(mockStand, times(2)).setMarker(true);
        verify(mockStand, times(2)).setGravity(false);
        verify(mockStand, times(2)).setCustomNameVisible(true);
    }

    @Test
    void spawnLabel_skipsIfLabelExists() {
        ArmorStand existingStand = mock(ArmorStand.class);
        when(existingStand.customName()).thenReturn(Component.text("target:25565"));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(existingStand));

        renderer.spawnLabel("world", 100, 64, 200, "target:25565");

        // No new armor stands should be spawned
        verify(world, never()).spawnEntity(any(), any());
    }

    @Test
    void clearLabels_worldNotExists_logsWarning() {
        when(worldProvider.getWorld("void")).thenReturn(null);

        renderer.clearLabels("void", 0, 64, 0, "target:25565");

        verify(logger).warning(contains("不存在"));
    }

    @Test
    void clearLabels_removesMatchingArmorStands() {
        ArmorStand matchingStand = mock(ArmorStand.class);
        when(matchingStand.customName()).thenReturn(Component.text("跨服传送 target:25565"));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(matchingStand));

        renderer.clearLabels("world", 100, 64, 200, "target:25565");

        verify(matchingStand).remove();
    }

    @Test
    void clearLabels_removesOnlyMatchingStands() {
        ArmorStand matching = mock(ArmorStand.class);
        when(matching.customName()).thenReturn(Component.text("target:25565"));
        ArmorStand nonMatching = mock(ArmorStand.class);
        when(nonMatching.customName()).thenReturn(Component.text("other_label"));

        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(matching, nonMatching));

        renderer.clearLabels("world", 100, 64, 200, "target:25565");

        verify(matching).remove();
        verify(nonMatching, never()).remove();
    }

    @Test
    void placeInfoSign_placesWallSignWithText() {
        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(signBlock);

        WallSign wallSignData = mock(WallSign.class);
        when(signBlock.getBlockData()).thenReturn(wallSignData);

        Sign signState = mock(Sign.class);
        SignSide signSide = mock(SignSide.class);
        when(signState.getSide(Side.FRONT)).thenReturn(signSide);
        when(signBlock.getState()).thenReturn(signState);

        Location center = new Location(world, 100, 64, 200);

        renderer.placeInfoSign(world, center, Axis.X, 1, 0, "example.com", 25565);

        verify(signBlock).setType(eq(Material.OAK_WALL_SIGN), eq(false));
        verify(signSide).line(eq(0), any(Component.class));
        verify(signSide).line(eq(1), any(Component.class));
        verify(signState).update(eq(true), eq(false));
    }

    @Test
    void placeInfoSign_skipsIfBlockOccupied() {
        Block occupiedBlock = mock(Block.class);
        when(occupiedBlock.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(occupiedBlock);

        Location center = new Location(world, 100, 64, 200);

        renderer.placeInfoSign(world, center, Axis.X, 1, 0, "example.com", 25565);

        verify(occupiedBlock, never()).setType(any(), anyBoolean());
    }

    @Test
    void clearNearbyArmorStands_removesMatchingStands() {
        ArmorStand matching = mock(ArmorStand.class);
        when(matching.customName()).thenReturn(Component.text("跨服传送 target:25565"));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(matching));

        Location center = new Location(world, 100, 64, 200);
        renderer.clearNearbyArmorStands(world, center, 3.0, "target:25565");

        verify(matching).remove();
    }
}
