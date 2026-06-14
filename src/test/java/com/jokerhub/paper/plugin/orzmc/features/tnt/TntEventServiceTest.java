package com.jokerhub.paper.plugin.orzmc.features.tnt;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.Format;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.TargetType;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.PlayerDisplayNames;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateResolvers;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TntEventServiceTest {

    private TypedConfigProvider configs;
    private OrzTextStyles styles;
    private Notifier notifier;
    private ThrottledNotifier throttledNotifier;
    private TntEventService service;

    private MockedStatic<TemplateResolvers> templateResolversMock;
    private MockedStatic<PlayerDisplayNames> displayNamesMock;

    @BeforeEach
    void setUp() {
        configs = mock(TypedConfigProvider.class);
        styles = mock(OrzTextStyles.class);
        notifier = mock(Notifier.class);
        throttledNotifier = mock(ThrottledNotifier.class);

        TntConfig tntConfig = new TntConfig(
                false, // enable = false (TNT globally disabled)
                true, // enableRespawnAnchor
                0, // placeCooldownSeconds (0 = no cooldown)
                1000L, // notifyThrottleMs
                List.of(), // whitelistRegions (empty)
                List.of()); // exemptEntities

        when(configs.tnt()).thenReturn(tntConfig);
        when(styles.tntPrefix()).thenReturn(Component.text("[TNT]"));
        when(styles.explosionPrefix()).thenReturn(Component.text("[爆炸]"));
        when(styles.playerName(any())).thenReturn(Component.text("player"));
        when(styles.unknownLabel()).thenReturn(Component.text("unknown"));
        when(styles.coordComponent(anyString())).thenReturn(Component.text("(0,0,0)"));
        when(styles.coordString(any())).thenReturn("(0,0,0)");

        TemplateOptions templateOpts = mock(TemplateOptions.class);
        when(templateOpts.coordScale()).thenReturn(1.0);
        when(templateOpts.coordPrecision()).thenReturn(1);
        when(templateOpts.coordUnitLabel()).thenReturn("m");
        when(configs.templateOptions()).thenReturn(templateOpts);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.CHANNEL, "msg", "alert", Format.DEFAULT));

        templateResolversMock = mockStatic(TemplateResolvers.class);
        templateResolversMock
                .when(() -> TemplateResolvers.worldAlias(anyString(), anyString(), any()))
                .thenReturn("world");

        displayNamesMock = mockStatic(PlayerDisplayNames.class);

        service = new TntEventService(configs, styles, notifier, throttledNotifier);
    }

    @AfterEach
    void tearDown() {
        templateResolversMock.close();
        displayNamesMock.close();
    }

    private World mockWorld() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }

    private Block mockBlock(Location loc) {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(loc);
        when(block.getType()).thenReturn(Material.TNT);
        return block;
    }

    // ---- onTNTPrime ----

    @Test
    void onTNTPrime_tntDisabled_notInWhitelist_cancels() {
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block block = mockBlock(loc);
        TNTPrimeEvent event = mock(TNTPrimeEvent.class);
        when(event.getBlock()).thenReturn(block);

        service.onTNTPrime(event);

        verify(event).setCancelled(true);
        verify(notifier).server(any(Component.class));
    }

    @Test
    void onTNTPrime_tntEnabled_doesNotCancel() {
        // Recreate service with TNT enabled
        TntConfig tntConfig = new TntConfig(true, false, 0, 1000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.CHANNEL, "msg", "alert", Format.DEFAULT));
        service = new TntEventService(configs, styles, notifier, throttledNotifier);

        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block block = mockBlock(loc);
        TNTPrimeEvent event = mock(TNTPrimeEvent.class);
        when(event.getBlock()).thenReturn(block);

        service.onTNTPrime(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    // ---- onPlaceBlock ----

    @Test
    void onPlaceBlock_placingTnt_noCooldown_notCancelled() {
        // TNT enabled with whitelist covering location
        TntConfig tntConfig = new TntConfig(true, false, 0, 1000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.CHANNEL, "msg", "alert", Format.DEFAULT));
        service = new TntEventService(configs, styles, notifier, throttledNotifier);

        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block placedBlock = mockBlock(loc);
        Player player = mock(Player.class);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placedBlock);
        when(event.getPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Alice");
        displayNamesMock.when(() -> PlayerDisplayNames.format(player)).thenReturn("Alice");

        service.onPlaceBlock(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onPlaceBlock_placingTnt_disabledAndNotInWhitelist_cancels() {
        TntConfig tntConfig = new TntConfig(false, false, 0, 1000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.CHANNEL, "msg", "alert", Format.DEFAULT));
        service = new TntEventService(configs, styles, notifier, throttledNotifier);

        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block placedBlock = mockBlock(loc);
        Player player = mock(Player.class);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placedBlock);
        when(event.getPlayer()).thenReturn(player);

        service.onPlaceBlock(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onPlaceBlock_placingRespawnAnchor_disabled_cancels() {
        TntConfig tntConfig = new TntConfig(false, false, 0, 1000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        service = new TntEventService(configs, styles, notifier, throttledNotifier);

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.RESPAWN_ANCHOR);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);

        service.onPlaceBlock(event);

        verify(event).setCancelled(true);
    }

    // ---- onBlockPreDispense ----

    @Test
    void onBlockPreDispense_nonTntItem_doesNothing() {
        BlockPreDispenseEvent event = mock(BlockPreDispenseEvent.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        when(event.getItemStack()).thenReturn(item);

        service.onBlockPreDispense(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void onBlockPreDispense_tntItem_disabled_cancels() {
        BlockPreDispenseEvent event = mock(BlockPreDispenseEvent.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.TNT);
        when(event.getItemStack()).thenReturn(item);
        Block block = mock(Block.class);
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(loc);
        when(block.getType()).thenReturn(Material.TNT);
        when(event.getBlock()).thenReturn(block);

        service.onBlockPreDispense(event);

        verify(event).setCancelled(true);
    }

    // ---- onBlockExplode ----

    @Test
    void onBlockExplode_airBlock_doesNothing() {
        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(event.getBlock()).thenReturn(block);

        service.onBlockExplode(event);

        verify(throttledNotifier, never()).runDefault(anyString(), any());
    }

    @Test
    void onBlockExplode_nonAirBlock_throttles() {
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(10);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(20);

        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        when(block.getLocation()).thenReturn(loc);
        when(event.getBlock()).thenReturn(block);

        service.onBlockExplode(event);

        verify(throttledNotifier).runDefault(anyString(), any());
    }

    // ---- onEntityExplode ----

    @Test
    void onEntityExplode_exemptEntity_doesNothing() {
        // Creeper is in the default exempt list
        TntConfig tntConfig = new TntConfig(false, true, 0, 1000L, List.of(), List.of("CREEPER"));
        when(configs.tnt()).thenReturn(tntConfig);
        service = new TntEventService(configs, styles, notifier, throttledNotifier);

        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntityType()).thenReturn(EntityType.CREEPER);

        service.onEntityExplode(event);

        verify(throttledNotifier, never()).runDefault(anyString(), any());
    }

    @Test
    void onEntityExplode_nonExemptEntity_sendsNotification() {
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(10);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(20);

        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntityType()).thenReturn(EntityType.ENDERMAN);
        when(event.getLocation()).thenReturn(loc);

        service.onEntityExplode(event);

        verify(throttledNotifier).runDefault(anyString(), any());
    }
}
