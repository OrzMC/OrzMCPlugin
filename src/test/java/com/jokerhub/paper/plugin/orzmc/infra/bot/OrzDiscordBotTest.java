package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrzDiscordBotTest {

    private ServerAccess server;
    private ServerLogger logger;
    private ServerScheduler scheduler;
    private ConfigService configService;
    private BotInboundHandler inboundHandler;
    private MessageFormatter formatter;
    private ThrottledLogger throttledLogger;
    private HealthRegistry healthRegistry;
    private FileConfiguration botConfig;
    private OrzDiscordBot bot;

    @BeforeEach
    void setUp() throws Exception {
        server = mock(ServerAccess.class);
        logger = mock(ServerLogger.class);
        scheduler = mock(ServerScheduler.class);
        configService = mock(ConfigService.class);
        inboundHandler = mock(BotInboundHandler.class);
        formatter = mock(MessageFormatter.class);
        throttledLogger = mock(ThrottledLogger.class);
        healthRegistry = spy(new HealthRegistry());
        botConfig = mock(FileConfiguration.class);

        when(logger.logger()).thenReturn(mock(Logger.class));
        when(configService.getConfig("bot")).thenReturn(botConfig);
        when(formatter.format(anyString(), any(MessageEnvelope.Format.class))).thenAnswer(invocation -> {
            String msg = invocation.getArgument(0);
            return List.of(msg);
        });

        bot = new OrzDiscordBot(
                server, logger, scheduler, configService, inboundHandler, formatter, throttledLogger, healthRegistry);
    }

    // Helper to set private fields via reflection
    private void setField(String name, Object value) throws Exception {
        Field f = OrzDiscordBot.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(bot, value);
    }

    private void setApiReady(boolean ready) throws Exception {
        Field f = OrzDiscordBot.class.getDeclaredField("isApiReady");
        f.setAccessible(true);
        f.set(bot, ready);
    }

    // Helper: create TextChannel mock whose sendMessage returns a non-null action (avoids NPE on .queue())
    private TextChannel mockTextChannel() {
        TextChannel channel = mock(TextChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessage(any(CharSequence.class))).thenReturn(action);
        return channel;
    }

    // ---------------------------------------------------------------
    // isEnable
    // ---------------------------------------------------------------

    @Test
    void isEnable_checksConfig() {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        assertTrue(bot.isEnable());

        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(false);
        assertFalse(bot.isEnable());
    }

    // ---------------------------------------------------------------
    // setup (disabled bot)
    // ---------------------------------------------------------------

    @Test
    void setup_disabledBot_doesNothing() {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(false);

        bot.setup();

        // Should log disabled message and return
        verify(throttledLogger).info(eq("discord"), contains("Disabled"));
        verify(healthRegistry, never()).setEnabled(eq("discord"), anyBoolean());
    }

    // ---------------------------------------------------------------
    // teardown
    // ---------------------------------------------------------------

    @Test
    void teardown_withNullApi_doesNothing() throws Exception {
        setField("api", null);
        setApiReady(true);

        // Should not throw
        bot.teardown();
    }

    @Test
    void teardown_setsApiReadyFalse() throws Exception {
        setApiReady(true);

        bot.teardown();

        // isApiReady should be false after teardown
        Field f = OrzDiscordBot.class.getDeclaredField("isApiReady");
        f.setAccessible(true);
        assertFalse(f.getBoolean(bot));
    }

    // ---------------------------------------------------------------
    // send(MessageEnvelope)
    // ---------------------------------------------------------------

    @Test
    void send_nullEnvelope_doesNothing() {
        bot.send(null);
        verify(formatter, never()).format(anyString(), any());
    }

    @Test
    void send_publicMessage_sendsToDefaultChannel() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        TextChannel channel = mockTextChannel();
        JDA api = mock(JDA.class);
        when(api.getTextChannelById(anyString())).thenReturn(channel);
        setField("api", api);
        when(botConfig.getString("discord_player_text_channel_id")).thenReturn("12345");

        bot.send(MessageEnvelope.publicMessage("public test"));

        verify(channel).sendMessage("public test");
    }

    @Test
    void send_publicMessage_notReady_queuesMessage() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(false);

        bot.send(MessageEnvelope.publicMessage("queue me"));

        // Message should be queued, not sent
        // Verify by sending another message that triggers the ready flow
        // Actually we can check via reflection: toBeSendMessageWhenApiReady
        Field queueField = OrzDiscordBot.class.getDeclaredField("toBeSendMessageWhenApiReady");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> queue = (List<String>) queueField.get(bot);
        assertFalse(queue.isEmpty());
        assertTrue(queue.get(0).contains("queue me"));
    }

    @Test
    void send_privateMessage_isNoOp() {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);

        // OrzDiscordBot.sendPrivate() is a no-op
        bot.send(MessageEnvelope.privateMessage("private test"));

        // sendPrivate is called but is no-op — only formatter is invoked during send()
        verify(formatter, times(1)).format(anyString(), any());
    }

    @Test
    void send_channelMessage_withKey_sendsToChannel() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        TextChannel channel = mockTextChannel();
        JDA api = mock(JDA.class);
        when(api.getTextChannelById("discord-abc")).thenReturn(channel);
        setField("api", api);
        when(botConfig.getString("channels.admin.discord")).thenReturn("discord-abc");

        bot.send(MessageEnvelope.channelMessage("admin", "admin msg"));

        verify(channel).sendMessage("admin msg");
    }

    @Test
    void send_channelMessage_withKeyChannelNotFound_fallsBackToDefault() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        TextChannel defaultChannel = mockTextChannel();
        JDA api = mock(JDA.class);
        when(api.getTextChannelById("channel-key-discord")).thenReturn(null);
        when(api.getTextChannelById("default-id")).thenReturn(defaultChannel);
        setField("api", api);
        when(botConfig.getString("channels.admin.discord")).thenReturn("channel-key-discord");
        when(botConfig.getString("discord_player_text_channel_id")).thenReturn("default-id");

        bot.send(MessageEnvelope.channelMessage("admin", "fallback msg"));

        // Should fall back to default channel
        verify(defaultChannel).sendMessage("fallback msg");
    }

    @Test
    void send_channelMessage_withNullKey_fallsBackToDefault() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        TextChannel defaultChannel = mockTextChannel();
        JDA api = mock(JDA.class);
        when(api.getTextChannelById("default-id")).thenReturn(defaultChannel);
        setField("api", api);
        when(botConfig.getString("discord_player_text_channel_id")).thenReturn("default-id");

        bot.send(MessageEnvelope.channelMessage(null, "null key msg"));

        verify(defaultChannel).sendMessage("null key msg");
    }

    @Test
    void send_channelMessage_notReady_queuesMessage() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(false);

        bot.send(MessageEnvelope.channelMessage("admin", "queue channel msg"));

        Field queueField = OrzDiscordBot.class.getDeclaredField("toBeSendMessageWhenApiReady");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> queue = (List<String>) queueField.get(bot);
        assertFalse(queue.isEmpty());
    }

    @Test
    void send_disabledBot_doesNothing() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(false);
        // Even if api is ready, being disabled should prevent sending
        setApiReady(true);
        TextChannel channel = mock(TextChannel.class);
        JDA api = mock(JDA.class);
        when(api.getTextChannelById(anyString())).thenReturn(channel);
        setField("api", api);

        bot.send(MessageEnvelope.publicMessage("disabled test"));

        // isEnable() returns false for disabled bot, so no message sent
        verify(channel, never()).sendMessage(anyString());
    }

    // ---------------------------------------------------------------
    // sendPublic (called from OrzBaseBot.send flow)
    // ---------------------------------------------------------------

    @Test
    void sendPublic_notReady_queuesMessage() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(false);

        bot.sendPublic("not ready msg");

        Field queueField = OrzDiscordBot.class.getDeclaredField("toBeSendMessageWhenApiReady");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> queue = (List<String>) queueField.get(bot);
        assertFalse(queue.isEmpty());
    }

    @Test
    void sendPublic_ready_sendsMessage() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        TextChannel channel = mock(TextChannel.class);
        JDA api = mock(JDA.class);
        when(api.getTextChannelById("channel-id")).thenReturn(channel);
        setField("api", api);
        when(botConfig.getString("discord_player_text_channel_id")).thenReturn("channel-id");

        bot.sendPublic("ready msg");

        verify(channel).sendMessage("ready msg");
    }

    @Test
    void sendPublic_channelNotFound_logsWarning() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        JDA api = mock(JDA.class);
        when(api.getTextChannelById(anyString())).thenReturn(null);
        setField("api", api);
        when(botConfig.getString("discord_player_text_channel_id")).thenReturn("nonexistent");

        bot.sendPublic("no channel msg");

        // Should log a warning
        verify(logger.logger()).warning(contains("your discord bot not in this text channel"));
    }

    // ---------------------------------------------------------------
    // sendPrivate (no-op)
    // ---------------------------------------------------------------

    @Test
    void sendPrivate_doesNothing() {
        bot.sendPrivate("private msg");
        // No interaction with formatter or JDA
        verifyNoInteractions(formatter);
    }

    // ---------------------------------------------------------------
    // sendChannel
    // ---------------------------------------------------------------

    @Test
    void sendChannel_notReady_queuesMessage() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(false);

        bot.sendChannel("admin", "channel not ready");

        Field queueField = OrzDiscordBot.class.getDeclaredField("toBeSendMessageWhenApiReady");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> queue = (List<String>) queueField.get(bot);
        assertFalse(queue.isEmpty());
    }

    @Test
    void sendChannel_resolvesKeyAndSends() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        TextChannel channel = mock(TextChannel.class);
        JDA api = mock(JDA.class);
        when(api.getTextChannelById("admin-discord-id")).thenReturn(channel);
        setField("api", api);
        when(botConfig.getString("channels.admin.discord")).thenReturn("admin-discord-id");

        bot.sendChannel("admin", "channel msg");

        verify(channel).sendMessage("channel msg");
    }

    @Test
    void sendChannel_keyResolvesToNull_fallsBackToDefault() throws Exception {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(true);
        setApiReady(true);
        TextChannel defaultChannel = mock(TextChannel.class);
        JDA api = mock(JDA.class);
        when(api.getTextChannelById("default-id")).thenReturn(defaultChannel);
        setField("api", api);
        when(botConfig.getString("channels.admin.discord")).thenReturn(null);
        when(botConfig.getString("discord_player_text_channel_id")).thenReturn("default-id");

        bot.sendChannel("admin", "key null fallback");

        verify(defaultChannel).sendMessage("key null fallback");
    }

    @Test
    void sendChannel_disabled_doesNothing() {
        when(botConfig.getBoolean("enable_discord_bot")).thenReturn(false);

        bot.sendChannel("admin", "disabled");

        verify(formatter, never()).format(anyString(), any());
    }
}
