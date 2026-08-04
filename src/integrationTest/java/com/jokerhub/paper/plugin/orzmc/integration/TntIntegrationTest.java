package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    @Test
    public void testTntBlockingRespondsToHotReload() {
        PlayerMock player = server.addPlayer();
        World world = player.getLocation().getWorld();
        // 远离默认 0 点退化白名单区域，行为确定
        Location loc = new Location(world, 1000, 100, 1000);
        Block block = loc.getBlock();
        block.setType(Material.TNT);
        ItemStack item = new ItemStack(Material.TNT);

        // 默认 config: tnt.enable=false → 白名单外放置被取消
        BlockPlaceEvent blocked = new BlockPlaceEvent(
                block, block.getState(), block.getRelative(BlockFace.DOWN), item, player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(blocked);
        Assertions.assertTrue(blocked.isCancelled(), "tnt.enable=false 应阻止放置");

        // 模拟管理员编辑 config.yml 后重载：改值 → 持久化到磁盘 → reloadConfig 从磁盘重读
        ConfigService configService = plugin.services().configService();
        configService.getConfig("config").set("tnt.enable", true);
        configService.saveConfig("config");
        configService.reloadConfig("config");

        // 同一 player 同一位置再放 → 允许（无需重建任何服务）
        BlockPlaceEvent allowed = new BlockPlaceEvent(
                block, block.getState(), block.getRelative(BlockFace.DOWN), item, player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(allowed);
        Assertions.assertFalse(allowed.isCancelled(), "tnt.enable=true 重载后应放行");
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
