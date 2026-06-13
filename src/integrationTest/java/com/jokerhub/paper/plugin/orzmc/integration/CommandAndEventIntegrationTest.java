package com.jokerhub.paper.plugin.orzmc.integration;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.features.bot.BotStatusService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import java.lang.reflect.Field;
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
        Notifier notifier = (Notifier) getServiceField(plugin, "notifier");
        notifier.registerSink(sink);
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
        BotStatusService statusService = (BotStatusService) getServiceField(plugin, "botStatusService");
        Component expected = statusService.buildStatusMessage();
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
        BotInboundHandler handler = (BotInboundHandler) getServiceField(plugin, "botInboundHandler");
        AtomicReference<MessageEnvelope> got = new AtomicReference<>();

        Assertions.assertDoesNotThrow(() -> handler.handleMessage("$e bot", true, got::set));
        server.getScheduler().performOneTick();

        MessageEnvelope envelope = got.get();
        Assertions.assertNotNull(envelope, "missing bot command response");
        Assertions.assertEquals(MessageEnvelope.Format.CODE_BLOCK, envelope.format());
        Assertions.assertTrue(envelope.message().contains("QQBot:"), envelope.message());
    }

    private static Object getField(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException(new NoSuchFieldException(name));
    }

    /** Access a field on the OrzServices graph via the plugin reference. */
    private static Object getServiceField(OrzMC plugin, String fieldName) {
        Object services = getField(plugin, "services");
        return getField(services, fieldName);
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
