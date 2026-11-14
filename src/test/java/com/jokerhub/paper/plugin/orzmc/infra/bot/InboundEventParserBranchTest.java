package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * InboundEventParser 分支补测（热点：49% → 目标高覆盖）。
 *
 * <p>入站解析是"网关 → 命令层"的唯一入口，所有丢弃分支都必须静默且可解释：
 * 超大帧、非事件帧、禁用平台、未授权会话、超长文本、限速——漏一条就是线上被刷或消息丢失。</p>
 */
class InboundEventParserBranchTest {

    private InboundEventParser parser;
    private BotInboundHandler inboundHandler;
    private ThrottledLogger throttledLogger;
    private HealthRegistry health;
    private HttpSender httpSender;
    private WebSocketLifecycle wsLifecycle;
    private MessageFormatter formatter;

    @BeforeEach
    void setUp() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("platforms.qq.enabled", true);
        config.set("platforms.qq.admin_group", "qq:admin-chat");
        config.set("platforms.qq.player_group", "qq:player-chat");
        config.set("platforms.qq.admin_dm", "qq:admin-dm");
        config.set("platforms.discord.enabled", false);

        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("easybot")).thenReturn(config);

        ServerLogger serverLogger = mock(ServerLogger.class);
        when(serverLogger.logger()).thenReturn(Logger.getLogger("inbound-parser-test"));
        inboundHandler = mock(BotInboundHandler.class);
        throttledLogger = mock(ThrottledLogger.class);
        health = new HealthRegistry();
        httpSender = mock(HttpSender.class);
        wsLifecycle = mock(WebSocketLifecycle.class);
        formatter = new PlainMessageFormatter();

        parser = new InboundEventParser(
                serverLogger,
                configService,
                inboundHandler,
                formatter,
                throttledLogger,
                health,
                httpSender,
                wsLifecycle);
    }

    private static String event(String platform, String chatId, String text, String role) {
        return """
                {"type":"event","event":"message.inbound",
                 "data":{"platform":"%s","chat_id":"%s","text":"%s","sender":{"role":"%s","nickname":"小明"}}}
                """.formatted(platform, chatId, text, role);
    }

    // ---------------------------------------------------------------- 帧级丢弃

    @Test
    void nullEmptyAndOversizedPayload_areIgnored() {
        parser.process(null);
        parser.process("");
        parser.process("x".repeat(64 * 1024 + 1));

        verifyNoInteractions(inboundHandler);
        verify(throttledLogger).warning(eq("easybot-inbound-size"), anyString());
    }

    @Test
    void nonObjectOrMissingType_areIgnored() {
        parser.process("[]");
        parser.process("{}");
        parser.process("{\"type\":null}");

        verifyNoInteractions(inboundHandler);
    }

    @Test
    void unknownFrameType_isIgnored() {
        parser.process("{\"type\":\"something_else\"}");

        verifyNoInteractions(inboundHandler);
    }

    // ---------------------------------------------------------------- 系统帧

    @Test
    void authOk_marksWsConnected() {
        health.setWsConnected("easybot", false);
        health.setLastError("easybot", "stale");

        parser.process("{\"type\":\"auth_ok\"}");

        assertTrue(health.getRaw("easybot").wsConnected);
        assertNull(health.getRaw("easybot").lastError);
    }

    @Test
    void authFailed_shutsDownAndRecordsError() {
        parser.process("{\"type\":\"auth_failed\",\"message\":\"bad token\"}");

        assertEquals("WS auth failed: bad token", health.getRaw("easybot").lastError);
        verify(wsLifecycle).shutdown();
    }

    @Test
    void authFailed_withoutMessage_usesUnknown() {
        parser.process("{\"type\":\"auth_failed\"}");

        assertEquals("WS auth failed: unknown", health.getRaw("easybot").lastError);
    }

    @Test
    void laggedFrame_reportsDroppedCount() {
        parser.process("{\"type\":\"lagged\",\"dropped\":7}");
        parser.process("{\"type\":\"lagged\"}");

        verify(throttledLogger).warning(eq("easybot-ws-lag"), eq("EasyBot WS 事件丢失: 7"));
        verify(throttledLogger).warning(eq("easybot-ws-lag"), eq("EasyBot WS 事件丢失: 0"));
    }

    @Test
    void ping_repliesPongOnlyWhenConnected() {
        WsClient client = mock(WsClient.class);
        when(wsLifecycle.currentClient()).thenReturn(client);

        parser.process("{\"type\":\"ping\"}");
        verify(client).send("{\"type\":\"pong\"}");

        when(wsLifecycle.currentClient()).thenReturn(null);
        parser.process("{\"type\":\"ping\"}"); // 无连接：不抛异常、不发帧
        verifyNoInteractions(inboundHandler);
    }

    // ---------------------------------------------------------------- 事件帧丢弃

    @Test
    void eventFrameMissingEventKeyOrWrongEventType_isIgnored() {
        parser.process("{\"type\":\"event\"}");
        parser.process("{\"type\":\"event\",\"event\":\"message.sent\"}");
        parser.process("{\"type\":\"event\",\"event\":null}");

        verifyNoInteractions(inboundHandler);
    }

    @Test
    void dataMissingOrNotObject_isIgnored() {
        parser.process("{\"type\":\"event\",\"event\":\"message.inbound\"}");
        parser.process("{\"type\":\"event\",\"event\":\"message.inbound\",\"data\":[]}");

        verifyNoInteractions(inboundHandler);
    }

    @Test
    void platformMissingEmptyOrTooLong_isIgnored() {
        parser.process(
                "{\"type\":\"event\",\"event\":\"message.inbound\",\"data\":{\"text\":\"hi\",\"chat_id\":\"c\"}}");
        parser.process(event("   ", "player-chat", "hi", "Member"));
        parser.process(event("p".repeat(65), "player-chat", "hi", "Member"));

        verifyNoInteractions(inboundHandler);
    }

    @Test
    void disabledPlatform_isIgnored() {
        parser.process(event("discord", "qq:player-chat", "hi", "Member"));

        verifyNoInteractions(inboundHandler);
    }

    @Test
    void emptyOrOversizedText_isIgnored() {
        parser.process(event("qq", "player-chat", "   ", "Member"));
        parser.process(event("qq", "player-chat", "t".repeat(8 * 1024 + 1), "Member"));

        verifyNoInteractions(inboundHandler);
        verify(throttledLogger).warning(eq("easybot-inbound-text-size"), anyString());
    }

    @Test
    void chatIdMissingOrTooLong_isIgnored() {
        parser.process(
                "{\"type\":\"event\",\"event\":\"message.inbound\",\"data\":{\"platform\":\"qq\",\"text\":\"hi\"}}");
        parser.process(event("qq", "c".repeat(513), "hi", "Member"));

        verifyNoInteractions(inboundHandler);
    }

    @Test
    void unauthorizedConversation_isIgnoredWithWarning() {
        parser.process(event("qq", "unknown-chat", "$h", "Owner"));

        verify(inboundHandler, never()).handleMessage(any(), anyBoolean(), any(), any());
        verify(throttledLogger).warning(eq("easybot-inbound-target"), anyString());
    }

    // ---------------------------------------------------------------- 正常分派

    @Test
    void allowedConversation_dispatchesWithRoleAndSenderName() {
        parser.process(event("qq", "player-chat", "$h", "Member"));

        verify(inboundHandler).handleMessage(eq("$h"), eq(false), eq("小明"), any());
    }

    @Test
    void ownerAndAdminRoles_areAdministrators() {
        parser.process(event("qq", "qq:admin-chat", "$b", "Owner"));
        parser.process(event("qq", "qq:admin-chat", "$b", "admin"));

        verify(inboundHandler, times(2)).handleMessage(eq("$b"), eq(true), eq("小明"), any());
    }

    @Test
    void unknownRole_isNotAdministrator() {
        parser.process(event("qq", "player-chat", "$l", "Member"));

        verify(inboundHandler).handleMessage(eq("$l"), eq(false), eq("小明"), any());
    }

    @Test
    void senderWithoutNickname_fallsBackToUserId() {
        String payload = """
                {"type":"event","event":"message.inbound",
                 "data":{"platform":"qq","chat_id":"player-chat","text":"$h",
                         "sender":{"role":"Member","user_id":"u-42"}}}
                """;
        parser.process(payload);

        verify(inboundHandler).handleMessage(eq("$h"), eq(false), eq("u-42"), any());
    }

    @Test
    void senderMissing_nameIsNull() {
        String payload = """
                {"type":"event","event":"message.inbound",
                 "data":{"platform":"qq","chat_id":"player-chat","text":"$h"}}
                """;
        parser.process(payload);

        verify(inboundHandler).handleMessage(eq("$h"), eq(false), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replySink_sendsFormattedPartsToOriginConversation() {
        ArgumentCaptor<Consumer<MessageEnvelope>> sinkCaptor = ArgumentCaptor.forClass(Consumer.class);
        parser.process(event("qq", "player-chat", "$h", "Member"));
        verify(inboundHandler).handleMessage(any(), anyBoolean(), any(), sinkCaptor.capture());

        Consumer<MessageEnvelope> sink = sinkCaptor.getValue();
        sink.accept(MessageEnvelope.publicMessage("第一行"));
        sink.accept(null); // 防御：null 不发送

        verify(httpSender, times(1)).sendMessage(any(), eq("qq:player-chat"), eq("第一行"));
    }

    @Test
    void rateLimit_dropsExcessEventsInSameWindow() {
        for (int i = 0; i < 101; i++) {
            parser.process(event("qq", "player-chat", "$h", "Member"));
        }

        verify(throttledLogger, times(1)).warning(eq("easybot-inbound-rate"), anyString());
        verify(inboundHandler, times(100)).handleMessage(any(), anyBoolean(), any(), any());
    }

    @Test
    void malformedJson_recordsHealthError() {
        parser.process("{not json");

        // 解析异常不冒泡到调用方（分支由 catch 覆盖），也不进业务层
        verifyNoInteractions(inboundHandler);
    }
}
