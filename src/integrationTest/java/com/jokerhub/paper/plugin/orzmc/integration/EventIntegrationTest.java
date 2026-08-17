package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockPlaceEvent;
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
public class EventIntegrationTest {

    /** 玩家上下线通知聚合窗口（config.yml window_ms=3000ms / 50 = 60 ticks），多 1 tick 确保冲刷执行。 */
    private static final long FLUSH_TICKS = 61;

    private ServerMock server;
    private OrzMC plugin;
    private CapturingSink sink;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
        // 关闭服务端白名单：MockBukkit 会在 PlayerJoinEvent 后把非白名单玩家移出在线列表，
        // 保持玩家在线的测试语义（与真实服 E2E 的 force_whitelist=false 对齐）。
        server.setWhitelist(false);
        sink = new CapturingSink();
        plugin.services().botModule().notifier().registerSink(sink);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testPlayerJoinEventTriggersNotification() {
        PlayerMock player = server.addPlayer();
        // addPlayer() fires PlayerJoinEvent；通知经聚合延迟一个窗口，推进调度器触发冲刷
        server.getScheduler().performTicks(FLUSH_TICKS);
        Assertions.assertTrue(
                sink.keys.stream().anyMatch(k -> k.equals("player_join")),
                "player_join event should be captured after player join");
    }

    @Test
    public void testPlayerQuitEventTriggersNotification() {
        PlayerMock player = server.addPlayer();
        server.getScheduler().performTicks(FLUSH_TICKS); // 先冲刷上线，避免与下线同窗口合并为摘要
        sink.clear();

        // disconnect() 触发 PlayerQuitEvent 并移出在线列表（与真实服语义一致）
        Assertions.assertDoesNotThrow(player::disconnect);

        server.getScheduler().performTicks(FLUSH_TICKS); // 冲刷下线
        Assertions.assertTrue(
                sink.keys.stream().anyMatch(k -> k.equals("player_quit")), "player_quit event should be captured");
    }

    @Test
    public void testTNTBlockPlaceCancelledWhenDisabled() {
        // TNT is disabled by default (config.yml: tnt.enable: false)
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        Block block = player.getLocation().getBlock();
        BlockState replacedState = block.getState(); // AIR state

        // Set the block to TNT to represent the placed block type
        block.setType(Material.TNT);

        Block placedAgainst = player.getWorld()
                .getBlockAt(
                        player.getLocation().getBlockX(),
                        player.getLocation().getBlockY() - 1,
                        player.getLocation().getBlockZ());
        ItemStack itemInHand = new ItemStack(Material.TNT);

        BlockPlaceEvent event =
                new BlockPlaceEvent(block, replacedState, placedAgainst, itemInHand, player, true, EquipmentSlot.HAND);

        Assertions.assertDoesNotThrow(
                () -> server.getPluginManager().callEvent(event),
                "TNT BlockPlaceEvent should dispatch without exception");

        // When TNT is disabled, the event should be cancelled
        // The placed block is at a location NOT in the whitelist (only (0,0,0) regions are whitelisted)
        // so isNotInWhiteList returns true, meaning placement should be cancelled
        Assertions.assertTrue(event.isCancelled(), "TNT block place should be cancelled when TNT is disabled");
    }

    @Test
    public void testTNTEventDispatchedWithoutError() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        Block block = player.getLocation().getBlock();
        BlockState replacedState = block.getState();
        Block placedAgainst = player.getWorld()
                .getBlockAt(
                        player.getLocation().getBlockX(),
                        player.getLocation().getBlockY() - 1,
                        player.getLocation().getBlockZ());

        block.setType(Material.TNT);

        BlockPlaceEvent event = new BlockPlaceEvent(
                block, replacedState, placedAgainst, new ItemStack(Material.TNT), player, true, EquipmentSlot.HAND);

        Assertions.assertDoesNotThrow(
                () -> server.getPluginManager().callEvent(event),
                "TNT BlockPlaceEvent should dispatch without exception");
    }
}
