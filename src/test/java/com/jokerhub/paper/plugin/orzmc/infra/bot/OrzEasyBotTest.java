package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketClientFactory;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketEventListener;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrzEasyBotTest {
    private BotInboundHandler inboundHandler;
    private ThrottledLogger throttledLogger;
    private OrzEasyBot bot;

    @BeforeEach
    void setUp() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("platforms.qq.enabled", true);
        config.set("platforms.qq.admin_group", "qq:admin-chat");
        config.set("platforms.qq.player_group", "qq:player-chat");
        config.set("platforms.qq.admin_dm", "qq:admin-dm");

        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("easybot")).thenReturn(config);
        ServerLogger serverLogger = mock(ServerLogger.class);
        when(serverLogger.logger()).thenReturn(Logger.getLogger("OrzEasyBotTest"));
        inboundHandler = mock(BotInboundHandler.class);
        throttledLogger = mock(ThrottledLogger.class);

        bot = new OrzEasyBot(
                serverLogger,
                configService,
                inboundHandler,
                new PlainMessageFormatter(),
                throttledLogger,
                new HealthRegistry(),
                mock(WebSocketClientFactory.class));
    }

    @Test
    void processInboundEvent_allowsConfiguredPlatformConversation() {
        bot.processInboundEvent(event("qq", "player-chat", "$h", "Member"));

        verify(inboundHandler).handleMessage(eq("$h"), eq(false), any(), any());
    }

    @Test
    void processInboundEvent_allowsConfiguredAdminConversationAndRole() {
        bot.processInboundEvent(event("qq", "qq:admin-chat", "$b", "Admin"));

        verify(inboundHandler).handleMessage(eq("$b"), eq(true), any(), any());
    }

    @Test
    void processInboundEvent_rejectsUnconfiguredConversation() {
        bot.processInboundEvent(event("qq", "unknown-chat", "$h", "Owner"));

        verify(inboundHandler, never()).handleMessage(any(), eq(true), any(), any());
        verify(throttledLogger)
                .warning(eq("easybot-inbound-target"), eq("EasyBot 忽略未授权会话消息: platform=qq, target=qq:unknown-chat"));
    }

    @Test
    void processInboundEvent_normalizesPlatformCase() {
        bot.processInboundEvent(event("QQ", "player-chat", "$h", "Member"));

        verify(inboundHandler).handleMessage(eq("$h"), eq(false), any(), any());
    }

    @Test
    void processInboundEvent_rejectsOversizedPayload() {
        bot.processInboundEvent("x".repeat(64 * 1024 + 1));

        verifyNoInteractions(inboundHandler);
    }

    @Test
    void setupWebSocketClient_reloadsWhenConnectionConfigChanges() throws Exception {
        YamlConfiguration config = gatewayConfig();
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("easybot")).thenReturn(config);
        ServerLogger serverLogger = logger("OrzEasyBotReloadTest");
        WsClient first = mock(WsClient.class);
        WsClient second = mock(WsClient.class);
        List<WsClient> clients = List.of(first, second);
        AtomicReference<WebSocketEventListener> listenerRef = new AtomicReference<>();
        AtomicReference<Integer> creates = new AtomicReference<>(0);
        WebSocketClientFactory factory = factoryReturning(clients, listenerRef, creates);
        OrzEasyBot reloadBot = new OrzEasyBot(
                serverLogger,
                configService,
                inboundHandler,
                new PlainMessageFormatter(),
                throttledLogger,
                new HealthRegistry(),
                factory);

        reloadBot.setupWebSocketClient();
        reloadBot.setupWebSocketClient();
        assertEquals(1, creates.get());

        config.set("api_key", "changed-secret");
        reloadBot.reloadConfig();

        assertEquals(2, creates.get());
        verify(first).disconnect();
        verify(second).connect();
    }

    @Test
    void send_routesPublicAndPrivateAndAccepts202() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        List<String> bodies = new ArrayList<>();
        CountDownLatch requests = new CountDownLatch(2);
        server.createContext("/api/v1/messages/batch-send", exchange -> {
            try (InputStream input = exchange.getRequestBody()) {
                bodies.add(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = "accepted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            requests.countDown();
        });
        server.start();
        try {
            YamlConfiguration config = gatewayConfig();
            config.set("api_server", "http://127.0.0.1:" + server.getAddress().getPort());
            ConfigService configService = mock(ConfigService.class);
            when(configService.getConfig("easybot")).thenReturn(config);
            HealthRegistry health = new HealthRegistry();
            OrzEasyBot outboundBot = new OrzEasyBot(
                    logger("OrzEasyBotHttpTest"),
                    configService,
                    inboundHandler,
                    new PlainMessageFormatter(),
                    throttledLogger,
                    health,
                    mock(WebSocketClientFactory.class));

            outboundBot.send(MessageEnvelope.publicMessage("public"));
            outboundBot.send(MessageEnvelope.privateMessage("private"));

            assertTrue(requests.await(5, TimeUnit.SECONDS));
            assertTrue(bodies.stream()
                    .anyMatch(body -> body.contains("\"targets\"")
                            && body.contains("qq:player-chat")
                            && body.contains("public")));
            assertTrue(bodies.stream()
                    .anyMatch(body ->
                            body.contains("\"targets\"") && body.contains("qq:admin-dm") && body.contains("private")));
            for (int i = 0; i < 50 && !health.getRaw("easybot").httpChecked; i++) {
                Thread.sleep(20);
            }
            assertTrue(health.getRaw("easybot").httpChecked);
            assertTrue(health.getRaw("easybot").httpOk);
            assertTrue(health.getRaw("easybot").apiReady);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void send_batch200_recordsFailedTargetsAndKeepsHealthHealthy() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        CountDownLatch requests = new CountDownLatch(1);
        server.createContext("/api/v1/messages/batch-send", exchange -> {
            try (InputStream input = exchange.getRequestBody()) {
                input.readAllBytes();
            }
            byte[] response = """
                    {"total":2,"results":{"qq:player-chat":{"status":"sent","messageId":"m1"},\
                    "telegram:fail-chat":{"status":"failed","error":"chat not found"}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            requests.countDown();
        });
        server.start();
        try {
            YamlConfiguration config = gatewayConfig();
            config.set("api_server", "http://127.0.0.1:" + server.getAddress().getPort());
            ConfigService configService = mock(ConfigService.class);
            when(configService.getConfig("easybot")).thenReturn(config);
            HealthRegistry health = new HealthRegistry();
            OrzEasyBot outboundBot = new OrzEasyBot(
                    logger("OrzEasyBotPartialFailTest"),
                    configService,
                    inboundHandler,
                    new PlainMessageFormatter(),
                    throttledLogger,
                    health,
                    mock(WebSocketClientFactory.class));

            outboundBot.send(MessageEnvelope.publicMessage("hello"));

            assertTrue(requests.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 50 && !health.getRaw("easybot").httpChecked; i++) {
                Thread.sleep(20);
            }
            assertTrue(health.getRaw("easybot").httpChecked);
            // 失败目标被单独记录（带错误原因），已成功目标不出现
            verify(throttledLogger)
                    .warning(eq("easybot-batch-partial"), contains("telegram:fail-chat -> failed: chat not found"));
            // 部分目标失败：httpOk 保持绿（网关健康），投递字段结构化记录 1/2 + 失败目标列表
            assertTrue(health.getRaw("easybot").httpOk);
            assertTrue(health.getRaw("easybot").apiReady);
            assertEquals(1, health.getRaw("easybot").deliveryFailed);
            assertEquals(2, health.getRaw("easybot").deliveryTotal);
            assertEquals(List.of("telegram:fail-chat"), health.getRaw("easybot").deliveryTargets);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void send_batch200_healthStoresStructuredDeliveryFields_onlyDetailInLog() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        CountDownLatch requests = new CountDownLatch(1);
        server.createContext("/api/v1/messages/batch-send", exchange -> {
            try (InputStream input = exchange.getRequestBody()) {
                input.readAllBytes();
            }
            byte[] response = """
                    {"total":3,"results":{"qq:player-chat":{"status":"sent","messageId":"m1"},\
                    "telegram:player-chat":{"status":"failed","error":"down"},\
                    "discord:player-chat":{"status":"failed","error":"down"}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            requests.countDown();
        });
        server.start();
        try {
            YamlConfiguration config = gatewayConfig();
            config.set("api_server", "http://127.0.0.1:" + server.getAddress().getPort());
            config.set("platforms.telegram.enabled", true);
            config.set("platforms.telegram.player_group", "telegram:player-chat");
            config.set("platforms.discord.enabled", true);
            config.set("platforms.discord.player_group", "discord:player-chat");
            ConfigService configService = mock(ConfigService.class);
            when(configService.getConfig("easybot")).thenReturn(config);
            HealthRegistry health = new HealthRegistry();
            OrzEasyBot outboundBot = new OrzEasyBot(
                    logger("OrzEasyBotConciseWarningTest"),
                    configService,
                    inboundHandler,
                    new PlainMessageFormatter(),
                    throttledLogger,
                    health,
                    mock(WebSocketClientFactory.class));

            outboundBot.send(MessageEnvelope.publicMessage("hello"));

            assertTrue(requests.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 50 && health.getRaw("easybot").deliveryFailed == 0; i++) {
                Thread.sleep(20);
            }
            // 健康状态只存结构化字段：失败数 / 总数 / 失败目标列表
            assertEquals(2, health.getRaw("easybot").deliveryFailed);
            assertEquals(3, health.getRaw("easybot").deliveryTotal);
            assertEquals(
                    List.of("telegram:player-chat", "discord:player-chat"), health.getRaw("easybot").deliveryTargets);
            // 完整明细（含所有失败目标及原因）进 throttled logger
            verify(throttledLogger)
                    .warning(eq("easybot-batch-partial"), contains("discord:player-chat -> failed: down"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void send_batch200_withoutTotal_treatedAsPartialNotAllFailed() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        CountDownLatch requests = new CountDownLatch(1);
        server.createContext("/api/v1/messages/batch-send", exchange -> {
            try (InputStream input = exchange.getRequestBody()) {
                input.readAllBytes();
            }
            // 无 total 字段，results 只含失败目标（成功目标被省略）
            byte[] response = ("{\"results\":{\"qq:player-chat\":{\"status\":\"failed\",\"error\":\"down\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            requests.countDown();
        });
        server.start();
        try {
            YamlConfiguration config = gatewayConfig();
            config.set("api_server", "http://127.0.0.1:" + server.getAddress().getPort());
            ConfigService configService = mock(ConfigService.class);
            when(configService.getConfig("easybot")).thenReturn(config);
            HealthRegistry health = new HealthRegistry();
            OrzEasyBot outboundBot = new OrzEasyBot(
                    logger("OrzEasyBotNoTotalTest"),
                    configService,
                    inboundHandler,
                    new PlainMessageFormatter(),
                    throttledLogger,
                    health,
                    mock(WebSocketClientFactory.class));

            outboundBot.send(MessageEnvelope.publicMessage("hello"));

            assertTrue(requests.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 50 && health.getRaw("easybot").deliveryFailed == 0; i++) {
                Thread.sleep(20);
            }
            // 未知 total：不判定为全部失败，记 warning（部分失败），deliveryTotal=0
            verify(throttledLogger).warning(eq("easybot-batch-partial"), contains("qq:player-chat -> failed: down"));
            assertEquals(1, health.getRaw("easybot").deliveryFailed);
            assertEquals(0, health.getRaw("easybot").deliveryTotal);
            assertEquals(List.of("qq:player-chat"), health.getRaw("easybot").deliveryTargets);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void send_batch200_allTargetsFailed_structuredDeliveryFields() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        CountDownLatch requests = new CountDownLatch(1);
        server.createContext("/api/v1/messages/batch-send", exchange -> {
            try (InputStream input = exchange.getRequestBody()) {
                input.readAllBytes();
            }
            byte[] response = """
                    {"total":2,"results":{"qq:player-chat":{"status":"failed","error":"chat closed"},\
                    "telegram:fail-chat":{"status":"failed","error":"not reachable"}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            requests.countDown();
        });
        server.start();
        try {
            YamlConfiguration config = gatewayConfig();
            config.set("api_server", "http://127.0.0.1:" + server.getAddress().getPort());
            ConfigService configService = mock(ConfigService.class);
            when(configService.getConfig("easybot")).thenReturn(config);
            HealthRegistry health = new HealthRegistry();
            OrzEasyBot outboundBot = new OrzEasyBot(
                    logger("OrzEasyBotAllFailTest"),
                    configService,
                    inboundHandler,
                    new PlainMessageFormatter(),
                    throttledLogger,
                    health,
                    mock(WebSocketClientFactory.class));

            outboundBot.send(MessageEnvelope.publicMessage("hello"));

            assertTrue(requests.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 50 && !health.getRaw("easybot").httpChecked; i++) {
                Thread.sleep(20);
            }
            assertTrue(health.getRaw("easybot").httpChecked);
            // 全部目标失败 → 记 error 日志（完整明细）
            verify(throttledLogger).error(eq("easybot-batch-fail"), contains("qq:player-chat -> failed: chat closed"));
            // 全部失败：httpOk 保持绿（网关本身健康），投递字段 2/2（由渲染层标红），无 lastError
            assertTrue(health.getRaw("easybot").httpOk);
            assertTrue(health.getRaw("easybot").apiReady);
            assertEquals(2, health.getRaw("easybot").deliveryFailed);
            assertEquals(2, health.getRaw("easybot").deliveryTotal);
            assertEquals(2, health.getRaw("easybot").deliveryTargets.size());
            assertNull(health.getRaw("easybot").lastError);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void send_batch200_allSuccessClearsPreviousDeliveryState() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        CountDownLatch requests = new CountDownLatch(2);
        AtomicInteger count = new AtomicInteger();
        server.createContext("/api/v1/messages/batch-send", exchange -> {
            try (InputStream input = exchange.getRequestBody()) {
                input.readAllBytes();
            }
            String body = count.incrementAndGet() == 1
                    ? "{\"total\":2,\"results\":{\"qq:player-chat\":{\"status\":\"sent\",\"messageId\":\"m1\"},"
                            + "\"telegram:player-chat\":{\"status\":\"failed\",\"error\":\"down\"}}}"
                    : "{\"total\":2,\"results\":{\"qq:player-chat\":{\"status\":\"sent\",\"messageId\":\"m1\"},"
                            + "\"telegram:player-chat\":{\"status\":\"sent\",\"messageId\":\"m2\"}}}";
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            requests.countDown();
        });
        server.start();
        try {
            YamlConfiguration config = gatewayConfig();
            config.set("api_server", "http://127.0.0.1:" + server.getAddress().getPort());
            config.set("platforms.telegram.enabled", true);
            config.set("platforms.telegram.player_group", "telegram:player-chat");
            ConfigService configService = mock(ConfigService.class);
            when(configService.getConfig("easybot")).thenReturn(config);
            HealthRegistry health = new HealthRegistry();
            OrzEasyBot outboundBot = new OrzEasyBot(
                    logger("OrzEasyBotClearWarningTest"),
                    configService,
                    inboundHandler,
                    new PlainMessageFormatter(),
                    throttledLogger,
                    health,
                    mock(WebSocketClientFactory.class));

            // 第一次：部分失败 → 先等投递失败字段出现（避免与第二次全部成功的清除竞态）
            outboundBot.send(MessageEnvelope.publicMessage("hello"));
            for (int i = 0; i < 100 && health.getRaw("easybot").deliveryFailed == 0; i++) {
                Thread.sleep(20);
            }
            assertEquals(1, health.getRaw("easybot").deliveryFailed);
            assertEquals(2, health.getRaw("easybot").deliveryTotal);

            // 第二次：全部成功 → 再发，等投递失败字段被清除
            outboundBot.send(MessageEnvelope.publicMessage("hello"));
            for (int i = 0; i < 100 && health.getRaw("easybot").deliveryFailed != 0; i++) {
                Thread.sleep(20);
            }
            assertEquals(0, health.getRaw("easybot").deliveryFailed);
            assertEquals(0, health.getRaw("easybot").deliveryTotal);
            assertTrue(health.getRaw("easybot").httpOk);
            assertTrue(health.getRaw("easybot").apiReady);
            assertTrue(requests.await(5, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webSocketBecomesHealthyOnlyAfterAuthentication() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("platforms.qq.enabled", true);
        config.set("platforms.qq.admin_group", "qq:admin-chat");
        config.set("ws_server", "ws://127.0.0.1:8080");
        config.set("api_key", "secret");
        ConfigService configService = mock(ConfigService.class);
        when(configService.getConfig("easybot")).thenReturn(config);
        ServerLogger serverLogger = mock(ServerLogger.class);
        when(serverLogger.logger()).thenReturn(Logger.getLogger("OrzEasyBotAuthTest"));
        HealthRegistry health = new HealthRegistry();
        WsClient wsClient = mock(WsClient.class);
        AtomicReference<WebSocketEventListener> listenerRef = new AtomicReference<>();
        WebSocketClientFactory factory =
                (server,
                        url,
                        logs,
                        retries,
                        baseRetry,
                        maxRetry,
                        jitter,
                        stableReset,
                        logMessages,
                        logThrottle,
                        headers,
                        heartbeat,
                        listener,
                        handler) -> {
                    listenerRef.set(listener);
                    return wsClient;
                };
        OrzEasyBot authBot = new OrzEasyBot(
                serverLogger,
                configService,
                inboundHandler,
                new PlainMessageFormatter(),
                throttledLogger,
                health,
                factory);

        authBot.setupWebSocketClient();
        listenerRef.get().onOpen();
        assertFalse(health.getRaw("easybot").wsConnected);

        authBot.processInboundEvent("{\"type\":\"auth_ok\"}");
        assertTrue(health.getRaw("easybot").wsConnected);

        authBot.processInboundEvent("{\"type\":\"auth_failed\",\"message\":\"bad token\"}");
        assertFalse(health.getRaw("easybot").wsConnected);
        verify(wsClient).disconnect();
    }

    private static String event(String platform, String chatId, String text, String role) {
        return """
                {
                  "type": "event",
                  "event": "message.inbound",
                  "data": {
                    "platform": "%s",
                    "chat_id": "%s",
                    "text": "%s",
                    "sender": {"role": "%s"}
                  }
                }
                """.formatted(platform, chatId, text, role);
    }

    private static YamlConfiguration gatewayConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("api_server", "http://127.0.0.1:8080");
        config.set("ws_server", "ws://127.0.0.1:8080");
        config.set("api_key", "secret");
        config.set("http_connect_timeout_seconds", 1);
        config.set("http_request_timeout_seconds", 1);
        config.set("http_max_retries", 0);
        config.set("platforms.qq.enabled", true);
        config.set("platforms.qq.admin_group", "qq:admin-chat");
        config.set("platforms.qq.player_group", "qq:player-chat");
        config.set("platforms.qq.admin_dm", "qq:admin-dm");
        return config;
    }

    private static ServerLogger logger(String name) {
        ServerLogger serverLogger = mock(ServerLogger.class);
        when(serverLogger.logger()).thenReturn(Logger.getLogger(name));
        return serverLogger;
    }

    private static WebSocketClientFactory factoryReturning(
            List<WsClient> clients,
            AtomicReference<WebSocketEventListener> listenerRef,
            AtomicReference<Integer> creates) {
        return (server,
                url,
                logs,
                retries,
                baseRetry,
                maxRetry,
                jitter,
                stableReset,
                logMessages,
                logThrottle,
                headers,
                heartbeat,
                listener,
                handler) -> {
            int index = creates.getAndUpdate(value -> value + 1);
            listenerRef.set(listener);
            return clients.get(index);
        };
    }
}
