package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.ws.MessageHandler;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketClientFactory;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketEventListener;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class OrzQQBotWsInjectionTest {
    private static class FakeWs implements WsClient {
        WebSocketEventListener listener;
        boolean connected;

        @Override
        public void connect() {
            connected = true;
            if (listener != null) listener.onOpen();
        }

        @Override
        public void disconnect() {
            connected = false;
            if (listener != null) listener.onClose(1000, "bye", false);
        }

        @Override
        public void send(String message) {}
    }

    private static class FakeFactory implements WebSocketClientFactory {
        FakeWs ws = new FakeWs();

        @Override
        public WsClient create(
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
                Map<String, String> httpHeaders,
                String heartbeatPayload,
                WebSocketEventListener listener,
                MessageHandler handler) {
            ws.listener = listener;
            return ws;
        }
    }

    private ServerAccess server;
    private ServerLogger logger;
    private java.util.logging.Logger rawLogger;
    private BotInboundHandler inbound;
    private ThrottledLogger throttled;
    private YamlConfiguration cfg;
    private ConfigService configService;
    private HealthRegistry healthRegistry;

    @BeforeEach
    void setUp() {
        rawLogger = java.util.logging.Logger.getLogger("test");
        rawLogger.setUseParentHandlers(false);
        rawLogger.setLevel(java.util.logging.Level.OFF);
        logger = () -> rawLogger;
        server = () -> null;
        inbound = (msg, isAdmin, sender) -> {};
        cfg = new YamlConfiguration();
        cfg.set("enable_qq_bot", true);
        cfg.set("qq_bot_ws_server", "ws://localhost:12345");
        cfg.set("ws_max_retries", 2);
        cfg.set("ws_base_retry_ms", 100);
        cfg.set("ws_max_delay_ms", 1000);
        cfg.set("ws_jitter_percent", 10);
        cfg.set("ws_stable_reset_ms", 200);
        cfg.set("ws_message_log_enabled", false);
        cfg.set("ws_message_log_throttle_ms", 60000);
        configService = Mockito.mock(ConfigService.class);
        Mockito.when(configService.getConfig("bot")).thenReturn(cfg);
        throttled = new ThrottledLogger(configService, rawLogger);
        healthRegistry = new HealthRegistry();
    }

    @Test
    void setupUsesFactoryAndUpdatesHealthOnOpenClose() {
        FakeFactory factory = new FakeFactory();
        OrzQQBot bot =
                new OrzQQBot(server, logger, configService, inbound, new PlainMessageFormatter(), throttled, factory,
                        healthRegistry);
        bot.setup();
        assertTrue(healthRegistry.getRaw("qq").wsConnected);
        bot.teardown();
        assertFalse(healthRegistry.getRaw("qq").wsConnected);
    }
}
