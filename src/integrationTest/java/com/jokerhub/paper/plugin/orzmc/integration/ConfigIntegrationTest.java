package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigPath;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class ConfigIntegrationTest {

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
    public void testConfigReloadCommand() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config reload"));
    }

    @Test
    public void testConfigReloadSpecific() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config reload config"));
    }

    @Test
    public void testConfigListCommand() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config list"));
    }

    @Test
    public void testConfigListNonAdminPlayer() {
        // MockBukkit does not check Brigadier requires(), so config commands
        // can dispatch for non-admin players too.
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config list"));
    }

    @Test
    public void testConfigGetCommand() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config get tnt.enable"));
    }

    @Test
    public void testConfigSetCommand() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config set tnt.enable false"));
    }

    @Test
    public void testConfigDumpCommand() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config dump"));
    }

    @Test
    public void testConfigResetCommand() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config reset tnt.enable"));
    }

    @Test
    public void testConfigPathRegistry() {
        Map<String, ConfigPath> allPaths = ConfigPath.all();
        Assertions.assertFalse(allPaths.isEmpty(), "ConfigPath registry should not be empty");
        Assertions.assertTrue(allPaths.containsKey("tnt.enable"), "Should contain tnt.enable path");
        Assertions.assertTrue(
                allPaths.containsKey("whitelist.force_whitelist"), "Should contain whitelist.force_whitelist path");
    }

    @Test
    public void testConfigCommandConsoleSender() {
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "config list"));
    }

    private static final class CapturingSink implements NotifierSink {
        private final List<String> keys = new ArrayList<>();
        private final List<MessageEnvelope> envelopes = new ArrayList<>();
        private final List<Component> serverMessages = new ArrayList<>();

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
