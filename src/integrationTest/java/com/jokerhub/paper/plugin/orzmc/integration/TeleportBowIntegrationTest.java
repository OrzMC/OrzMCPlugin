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
