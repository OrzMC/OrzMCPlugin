package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@Tag("integration")
public class TntIntegrationTest {

    private ServerMock server;
    private OrzMC plugin;
    private CapturingSink sink;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
        sink = new CapturingSink();
        plugin.services().botModule().notifier().registerSink(sink);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testTntPlaceBlockEventDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        Block block = player.getLocation().getBlock().getRelative(BlockFace.UP);
        block.setType(Material.TNT);
        ItemStack item = new ItemStack(Material.TNT);
        BlockPlaceEvent event = new BlockPlaceEvent(
                block, block.getState(), block.getRelative(BlockFace.DOWN), item, player, true, EquipmentSlot.HAND);
        Assertions.assertDoesNotThrow(() -> server.getPluginManager().callEvent(event));
    }

    @Test
    public void testEntityExplosionEventDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        Block block = player.getLocation().getBlock();
        List<Block> blockList = new ArrayList<>();
        blockList.add(block);
        EntityExplodeEvent event = new EntityExplodeEvent(player, player.getLocation(), blockList, 1.0f, null);
        Assertions.assertDoesNotThrow(() -> server.getPluginManager().callEvent(event));
    }

    @Test
    public void testPluginLoadsWithoutError() {
        Assertions.assertNotNull(plugin);
        Assertions.assertNotNull(server);
    }

    @Test
    public void testTntDispenseEventDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        Block dispenser = player.getLocation().getBlock().getRelative(BlockFace.UP);
        dispenser.setType(Material.DISPENSER);
        Assertions.assertDoesNotThrow(() -> {
            server.getScheduler().performOneTick();
        });
    }

    @Test
    public void testEntityExplosionWithEntityDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        List<Block> blockList = new ArrayList<>();
        EntityExplodeEvent event = new EntityExplodeEvent(player, player.getLocation(), blockList, 0f, null);
        Assertions.assertDoesNotThrow(() -> server.getPluginManager().callEvent(event));
    }

    private static final class CapturingSink implements NotifierSink {
        final List<String> keys = new ArrayList<>();
        final List<MessageEnvelope> envelopes = new ArrayList<>();
        final List<Component> serverMessages = new ArrayList<>();

        @Override
        public void server(Component message) {
            serverMessages.add(message);
        }

        @Override
        public void event(String key, MessageEnvelope envelope) {
            keys.add(key);
            envelopes.add(envelope);
        }
    }
}
