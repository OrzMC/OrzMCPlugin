package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.assembly.BotModule;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class CommandAndEventIntegrationTest {
    private ServerMock server;
    private OrzMC plugin;
    private CapturingSink sink;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
        sink = new CapturingSink();
        BotModule botModule = getBotModule(plugin);
        botModule.notifier().registerSink(sink);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testBotCommandDispatch() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(() -> server.dispatchCommand(player, "bot"));
        Component actual = player.nextComponentMessage();
        BotModule botModule = getBotModule(plugin);
        Component expected = botModule.botStatusService().buildStatusMessage();
        String actualText = PlainTextComponentSerializer.plainText().serialize(actual);
        String expectedText = PlainTextComponentSerializer.plainText().serialize(expected);
        Assertions.assertEquals(expectedText, actualText);
    }

    @Test
    public void testPlayerJoinEventDispatch() {
        PlayerMock player = server.addPlayer();
        Assertions.assertDoesNotThrow(
                () -> server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.text("hi"))));
        Assertions.assertTrue(
                sink.keys.stream().anyMatch(k -> k.equals("player_join")), "player_join event not captured");
        Assertions.assertTrue(
                sink.envelopes.stream().anyMatch(e -> e != null && e.message() != null), "missing event message");
    }

    @Test
    public void testAdminBotCommandCanExecuteConsoleCommand() {
        BotModule botModule = getBotModule(plugin);
        BotInboundHandler handler = botModule.botInboundHandler();
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();

        Assertions.assertDoesNotThrow(() -> handler.handleMessage("$e bot", true, got::set));
        server.getScheduler().performOneTick();

        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "missing bot command response");
        Assertions.assertEquals(MessageEnvelope.Format.CODE_BLOCK, envelope.format());
        Assertions.assertTrue(envelope.message().contains("QQBot:"), envelope.message());
    }

    private static BotModule getBotModule(OrzMC plugin) {
        return plugin.services().botModule();
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
