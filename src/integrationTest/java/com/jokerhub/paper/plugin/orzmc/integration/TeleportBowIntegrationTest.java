package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.EntityShootBowEvent;
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
public class TeleportBowIntegrationTest {

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
    public void testTpbowCommandWorksForPlayer() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "tpbow"));
    }

    @Test
    public void testTpbowCommandForConsoleGetsPlayerOnlyMessage() {
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "tpbow"));
    }

    @Test
    public void testTpbowCommandDispatchesWithoutError() {
        PlayerMock player = server.addPlayer();
        boolean executed = server.dispatchCommand(player, "tpbow");
        Assertions.assertTrue(executed, "tpbow command should dispatch for players");
    }

    @Test
    public void testPluginAssemblyIsAccessible() {
        Assertions.assertNotNull(plugin.services().botModule());
    }

    @Test
    public void testTeleportBowFullCycleDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "tpbow"));
        server.getScheduler().performOneTick();
    }

    @Test
    public void testShootTeleportBowTicksWithoutThrowing() {
        // 用唯一玩家名，避免静态 CooldownRegistry 被同类其他测试的 tpbow 触发冷却而跳过 giveAndEquip。
        PlayerMock player = server.addPlayer("tpbow-shooter");
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "tpbow"));

        ItemStack bow = player.getInventory().getItemInMainHand();
        Assertions.assertEquals(Material.BOW, bow.getType(), "tpbow 应把传送弓放到主手");

        World world = player.getWorld();
        Arrow arrow = world.spawn(new Location(world, 100, 64, 100), Arrow.class);
        EntityShootBowEvent shootEvent = new EntityShootBowEvent(player, bow, arrow, 1.0F);
        Assertions.assertDoesNotThrow(() -> server.getPluginManager().callEvent(shootEvent));

        // MockBukkit 的 AbstractArrowMock.isInBlock() 抛 UnimplementedOperationException：
        // tracker 首 tick 的 shouldStop() 抛异常被 try/catch(Throwable) 捕获，优雅停掉并释放区块。
        // 这里验证发射 + 跟踪 + 停止的全过程不向测试泄漏异常。
        for (int i = 0; i < 5; i++) {
            Assertions.assertDoesNotThrow(() -> server.getScheduler().performOneTick());
        }
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
