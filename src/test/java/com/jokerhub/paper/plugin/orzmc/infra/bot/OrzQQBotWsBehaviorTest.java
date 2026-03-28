package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketClientFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class OrzQQBotWsBehaviorTest {
    static class CapturingFactory implements WebSocketClientFactory {
        com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketEventListener listener;
        com.jokerhub.paper.plugin.orzmc.infra.ws.MessageHandler handler;
        final FakeWs ws = new FakeWs(this);

        @Override
        public com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient create(
                ServerLogger server,
                String url,
                ThrottledLogger throttledLogger,
                int maxRetries,
                long baseRetryInterval,
                long maxRetryInterval,
                int jitterPercent,
                long stableResetMs,
                boolean logMessageEnabled,
                long logMessageThrottleMs,
                java.util.Map<String, String> httpHeaders,
                String heartbeatPayload,
                com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketEventListener listener,
                com.jokerhub.paper.plugin.orzmc.infra.ws.MessageHandler handler) {
            this.listener = listener;
            this.handler = handler;
            return ws;
        }
    }

    static class FakeWs implements com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient {
        private final CapturingFactory factory;
        boolean connected;

        FakeWs(CapturingFactory factory) {
            this.factory = factory;
        }

        @Override
        public void connect() {
            connected = true;
            if (factory.listener != null) factory.listener.onOpen();
        }

        @Override
        public void disconnect() {
            connected = false;
            if (factory.listener != null) factory.listener.onClose(1000, "bye", false);
        }

        @Override
        public void send(String message) {}

        void fireError(Exception e) {
            if (factory.listener != null) factory.listener.onError(e);
        }

        void fireMessage(String msg) {
            if (factory.handler != null) factory.handler.handle(msg);
        }
    }

    private ServerAccess server;
    private ServerLogger logger;
    private java.util.logging.Logger rawLogger;
    private ThrottledLogger throttled;
    private YamlConfiguration cfg;
    private ConfigService configService;

    @BeforeEach
    void init() {
        rawLogger = java.util.logging.Logger.getLogger("test");
        rawLogger.setUseParentHandlers(false);
        rawLogger.setLevel(java.util.logging.Level.OFF);
        logger = () -> rawLogger;
        server = () -> null;
        cfg = new YamlConfiguration();
        cfg.set("enable_qq_bot", true);
        cfg.set("qq_bot_ws_server", "ws://localhost:12345");
        cfg.set("qq_group_id", "123");
        cfg.set("ws_max_retries", 1);
        cfg.set("ws_base_retry_ms", 100);
        cfg.set("ws_max_delay_ms", 1000);
        cfg.set("ws_jitter_percent", 10);
        cfg.set("ws_stable_reset_ms", 200);
        cfg.set("ws_message_log_enabled", false);
        cfg.set("ws_message_log_throttle_ms", 60000);
        configService = Mockito.mock(ConfigService.class);
        Mockito.when(configService.getConfig("bot")).thenReturn(cfg);
        throttled = new ThrottledLogger(configService, rawLogger);
    }

    @Test
    void reportsHealthOnReconnectExhausted() {
        CapturingFactory factory = new CapturingFactory();
        OrzQQBot bot = new OrzQQBot(
                server, logger, configService, (m, a, s) -> {}, new PlainMessageFormatter(), throttled, factory);
        bot.setup();
        factory.ws.fireError(new RuntimeException("WS reconnect exhausted"));
        assertFalse(HealthRegistry.get("qq").wsConnected);
        assertTrue(String.valueOf(HealthRegistry.get("qq").lastError).contains("WS reconnect exhausted"));
    }

    @Test
    void dispatchesIncomingAdminMessage() {
        AtomicReference<String> gotMsg = new AtomicReference<>();
        AtomicBoolean gotPriv = new AtomicBoolean(false);
        BotInboundHandler inbound = (message, isAdmin, sender) -> {
            gotMsg.set(message);
            gotPriv.set(isAdmin);
        };
        CapturingFactory factory = new CapturingFactory();
        OrzQQBot bot =
                new OrzQQBot(server, logger, configService, inbound, new PlainMessageFormatter(), throttled, factory);
        bot.setup();
        String json = "{\"group_id\":\"123\",\"raw_message\":\"$hi\",\"sender\":{\"role\":\"admin\"}}";
        factory.ws.fireMessage(json);
        assertEquals("$hi", gotMsg.get());
        assertTrue(gotPriv.get());
    }
}
