package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrzBaseBotTest {

    private ServerAccess server;
    private ServerLogger logger;
    private ConfigService configService;
    private HealthRegistry healthRegistry;
    private FileConfiguration botConfig;
    private TestBot bot;

    static class TestBot extends OrzBaseBot {
        final List<String> sentPublic = new ArrayList<>();
        final List<String> sentPrivate = new ArrayList<>();
        final List<String> sentChannelKeys = new ArrayList<>();
        final List<String> sentChannelMessages = new ArrayList<>();

        TestBot(ServerAccess server, ServerLogger logger, ConfigService configService, HealthRegistry healthRegistry) {
            super(server, logger, configService, healthRegistry);
        }

        @Override
        public boolean isEnable() {
            return true;
        }

        @Override
        public void setup() {}

        @Override
        public void teardown() {}

        @Override
        protected void sendPublic(String message) {
            sentPublic.add(message);
        }

        @Override
        protected void sendPrivate(String message) {
            sentPrivate.add(message);
        }

        @Override
        protected void sendChannel(String channelKey, String message) {
            sentChannelKeys.add(channelKey);
            sentChannelMessages.add(message);
        }
    }

    @BeforeEach
    void setUp() {
        server = mock(ServerAccess.class);
        logger = mock(ServerLogger.class);
        configService = mock(ConfigService.class);
        healthRegistry = spy(new HealthRegistry());
        botConfig = mock(FileConfiguration.class);

        when(logger.logger()).thenReturn(mock(Logger.class));
        when(configService.getConfig("bot")).thenReturn(botConfig);

        bot = new TestBot(server, logger, configService, healthRegistry);
    }

    @Test
    void send_publicMessage_dispatchesToSendPublic() {
        MessageEnvelope envelope = MessageEnvelope.publicMessage("public text");
        bot.send(envelope);
        assertEquals(List.of("public text"), bot.sentPublic);
        assertTrue(bot.sentPrivate.isEmpty());
        assertTrue(bot.sentChannelKeys.isEmpty());
    }

    @Test
    void send_privateMessage_dispatchesToSendPrivate() {
        MessageEnvelope envelope = MessageEnvelope.privateMessage("private text");
        bot.send(envelope);
        assertEquals(List.of("private text"), bot.sentPrivate);
        assertTrue(bot.sentPublic.isEmpty());
    }

    @Test
    void send_channelMessage_withKey_dispatchesToSendChannel() {
        MessageEnvelope envelope = MessageEnvelope.channelMessage("channel-key", "channel text");
        bot.send(envelope);
        assertEquals(List.of("channel-key"), bot.sentChannelKeys);
        assertEquals(List.of("channel text"), bot.sentChannelMessages);
        assertTrue(bot.sentPublic.isEmpty());
    }

    @Test
    void send_channelMessage_withNullChannelKey_fallsBackToSendPublic() {
        MessageEnvelope envelope = new MessageEnvelope(
                MessageEnvelope.TargetType.CHANNEL, "fallback text", null, MessageEnvelope.Format.DEFAULT);
        bot.send(envelope);
        assertEquals(List.of("fallback text"), bot.sentPublic);
        assertTrue(bot.sentChannelKeys.isEmpty());
    }

    @Test
    void send_channelMessage_withEmptyChannelKey_fallsBackToSendPublic() {
        MessageEnvelope envelope = new MessageEnvelope(
                MessageEnvelope.TargetType.CHANNEL, "empty key text", "", MessageEnvelope.Format.DEFAULT);
        bot.send(envelope);
        assertEquals(List.of("empty key text"), bot.sentPublic);
        assertTrue(bot.sentChannelKeys.isEmpty());
    }

    @Test
    void send_publicMessageByDefault_dispatchesToSendPublic() {
        MessageEnvelope envelope = new MessageEnvelope(
                MessageEnvelope.TargetType.PUBLIC, "default public", null, MessageEnvelope.Format.DEFAULT);
        bot.send(envelope);
        assertEquals(List.of("default public"), bot.sentPublic);
    }

    @Test
    void send_nullEnvelope_doesNothing() {
        bot.send(null);
        assertTrue(bot.sentPublic.isEmpty());
        assertTrue(bot.sentPrivate.isEmpty());
        assertTrue(bot.sentChannelKeys.isEmpty());
    }

    @Test
    void constructor_setsBotConfig() {
        assertNotNull(bot.botConfig);
    }
}
