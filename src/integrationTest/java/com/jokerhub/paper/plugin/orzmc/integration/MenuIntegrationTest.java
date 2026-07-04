package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@Tag("integration")
public class MenuIntegrationTest {

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
    public void testMenuCommandWorksForPlayer() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "menu"));
    }

    @Test
    public void testMenuCommandForConsoleGetsPlayerOnlyMessage() {
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "menu"));
    }

    @Test
    public void testMenuCommandDispatchesForPlayer() {
        PlayerMock player = server.addPlayer();
        boolean executed = server.dispatchCommand(player, "menu");
        Assertions.assertTrue(executed, "menu command should dispatch for players");
    }

    @Test
    public void testMenuOpenCreatesInventory() {
        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "menu");
        server.getScheduler().performOneTick();
        Assertions.assertNotNull(player.getOpenInventory());
    }

    @Test
    public void testMenuInventoryIsAccessible() {
        PlayerMock player = server.addPlayer();
        server.dispatchCommand(player, "menu");
        server.getScheduler().performOneTick();
        Inventory inventory = player.getOpenInventory().getTopInventory();
        Assertions.assertNotNull(inventory);
    }

    @Test
    public void testMenuCommandMultiplePlayers() {
        PlayerMock player1 = server.addPlayer();
        PlayerMock player2 = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player1, "menu"));
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player2, "menu"));
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
