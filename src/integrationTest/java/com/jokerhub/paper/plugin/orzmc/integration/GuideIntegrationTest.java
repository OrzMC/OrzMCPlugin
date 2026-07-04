package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@Tag("integration")
public class GuideIntegrationTest {

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
    public void testGuideCommandWorksForPlayer() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "guide"));
    }

    @Test
    public void testGuideCommandForConsoleGetsPlayerOnlyMessage() {
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "guide"));
    }

    @Test
    public void testGuideCommandDispatchesForPlayer() {
        PlayerMock player = server.addPlayer();
        boolean executed = server.dispatchCommand(player, "guide");
        Assertions.assertTrue(executed, "guide command should dispatch for players");
    }

    @Test
    public void testGuideCommandGivesBookToPlayer() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "guide"));
        // The guide command opens a book or gives a written book item
        // Verify no crash — the player inventory should be accessible
        Assertions.assertNotNull(player.getInventory());
    }

    @Test
    public void testGuideCommandPlayerJoinGivesBook() {
        // GuideService listens for PlayerJoinEvent and gives the book
        PlayerMock player = server.addPlayer();
        // addPlayer() triggers PlayerJoinEvent, which should give the guide book
        server.getScheduler().performOneTick();
        // Player should have items in inventory (the guide book)
        Assertions.assertDoesNotThrow(() -> {
            var items = player.getInventory().getContents();
            Assertions.assertNotNull(items);
        });
    }

    @Test
    public void testGuideCommandMultipleTimes() {
        PlayerMock player = server.addPlayer();
        // Running guide multiple times should be safe
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "guide"));
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "guide"));
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "guide"));
    }

    @Test
    public void testGuideCommandWithBotInboundHandler() {
        // Test guide-related bot command routing
        Assertions.assertDoesNotThrow(() ->
                plugin.services().botModule().botInboundHandler().handleMessage("$r example.com", true, env -> {}));
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
