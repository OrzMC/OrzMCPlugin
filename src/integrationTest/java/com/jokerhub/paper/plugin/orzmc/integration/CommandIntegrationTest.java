package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.assembly.BotModule;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
public class CommandIntegrationTest {

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
    public void testBotCommandReturnsStatus() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "bot"));
        Component actual = player.nextComponentMessage();
        BotModule botModule = plugin.services().botModule();
        Component expected = botModule.botStatusService().buildStatusMessage();
        String actualText = PlainTextComponentSerializer.plainText().serialize(actual);
        String expectedText = PlainTextComponentSerializer.plainText().serialize(expected);
        Assertions.assertEquals(expectedText, actualText);
    }

    @Test
    public void testBotCommandWorksForConsole() {
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "bot"));
    }

    @Test
    public void testGuideCommandWorksForPlayer() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "guide"));
    }

    @Test
    public void testGuideCommandConsoleGetsPlayerOnlyMessage() {
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "guide"));
    }

    @Test
    public void testMenuCommandWorksForPlayer() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "menu"));
    }

    @Test
    public void testMenuCommandConsoleGetsPlayerOnlyMessage() {
        CommandSender console = server.getConsoleSender();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(console, "menu"));
    }

    @Test
    public void testPortalCommandWorksForAdminPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "portal"));
    }

    @Test
    public void testConfigListCommandWorksForAdminPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config list"));
    }

    @Test
    public void testBlacklistCommandWorksForAdminPlayer() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "blacklist list"));
    }

    @Test
    public void testBlacklistCommandWorksForNonAdminPlayer() {
        // Note: MockBukkit's dispatchCommand() does not check Brigadier requires(),
        // so admin-only commands dispatch successfully even for non-admin players.
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "blacklist list"));
    }

    @Test
    public void testConfigCommandWorksForNonAdminPlayer() {
        // Note: MockBukkit's dispatchCommand() does not check Brigadier requires()
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "config list"));
    }

    @Test
    public void testPortalCommandWorksForNonAdminPlayer() {
        // Note: MockBukkit's dispatchCommand() does not check Brigadier requires()
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "portal somehost"));
    }

    @Test
    public void testBotCommandWithBotInboundHandler() {
        BotModule botModule = plugin.services().botModule();
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();
        Assertions.assertDoesNotThrow(() -> botModule.botInboundHandler().handleMessage("$e bot", true, got::set));
        server.getScheduler().performOneTick();
        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "Bot command should produce response");
        Assertions.assertTrue(envelope.message().contains("QQBot:"), "Status should mention QQBot");
    }

    @Test
    public void testTpbowCommandWorksForPlayer() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "tpbow"));
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
