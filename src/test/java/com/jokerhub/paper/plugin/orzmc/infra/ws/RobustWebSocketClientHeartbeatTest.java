package com.jokerhub.paper.plugin.orzmc.infra.ws;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class RobustWebSocketClientHeartbeatTest {
    static class StubClient extends WebSocketClient {
        boolean open = true;
        boolean closed = false;

        public StubClient() throws URISyntaxException {
            super(new URI("ws://localhost"), Map.of());
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {}

        @Override
        public void onMessage(String message) {}

        @Override
        public void onClose(int code, String reason, boolean remote) {}

        @Override
        public void onError(Exception ex) {}

        @Override
        public boolean isOpen() {
            return open && !closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static class Testable extends RobustWebSocketClient {
        public Testable(
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
                WebSocketEventListener listener)
                throws URISyntaxException {
            super(
                    server,
                    url,
                    throttledLogger,
                    maxRetries,
                    baseRetryInterval,
                    maxRetryInterval,
                    jitterPercent,
                    stableResetMs,
                    logMessageEnabled,
                    logMessageThrottleMs,
                    httpHeaders,
                    heartbeatPayload,
                    listener);
        }
    }

    @Test
    void closesOnConsecutiveMissedAcks() throws Exception {
        java.util.logging.Logger rawLogger = java.util.logging.Logger.getLogger("test");
        rawLogger.setUseParentHandlers(false);
        rawLogger.setLevel(java.util.logging.Level.OFF);
        ServerLogger logger = () -> rawLogger;
        ConfigService configService = Mockito.mock(ConfigService.class);
        Mockito.when(configService.getConfig("bot")).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        Testable client = new Testable(
                logger,
                "ws://localhost:65534",
                new ThrottledLogger(configService, rawLogger),
                1,
                100,
                1000,
                10,
                200,
                false,
                60000,
                Map.of(),
                "{\"action\":\"get_status\"}",
                null);
        StubClient stub = new StubClient();
        Field f = RobustWebSocketClient.class.getDeclaredField("client");
        f.setAccessible(true);
        f.set(client, stub);
        Field msgTs = RobustWebSocketClient.class.getDeclaredField("lastMessageTs");
        msgTs.setAccessible(true);
        msgTs.setLong(client, 0L);
        Field sentTs = RobustWebSocketClient.class.getDeclaredField("lastHeartbeatSentTs");
        sentTs.setAccessible(true);
        sentTs.setLong(client, 1000L);
        Field missed = RobustWebSocketClient.class.getDeclaredField("missedHeartbeatAcks");
        missed.setAccessible(true);
        missed.setInt(client, 1);
        client.doHeartbeatTick();
        assertTrue(stub.closed);
    }

    @Test
    void resetsMissedAcksOnMessageActivity() throws Exception {
        java.util.logging.Logger rawLogger = java.util.logging.Logger.getLogger("test");
        rawLogger.setUseParentHandlers(false);
        rawLogger.setLevel(java.util.logging.Level.OFF);
        ServerLogger logger = () -> rawLogger;
        ConfigService configService = Mockito.mock(ConfigService.class);
        Mockito.when(configService.getConfig("bot")).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        Testable client = new Testable(
                logger,
                "ws://localhost:65534",
                new ThrottledLogger(configService, rawLogger),
                1,
                100,
                1000,
                10,
                200,
                false,
                60000,
                Map.of(),
                "{\"action\":\"get_status\"}",
                null);
        StubClient stub = new StubClient();
        Field f = RobustWebSocketClient.class.getDeclaredField("client");
        f.setAccessible(true);
        f.set(client, stub);
        Field missed = RobustWebSocketClient.class.getDeclaredField("missedHeartbeatAcks");
        missed.setAccessible(true);
        missed.setInt(client, 1);
        Field sentTs = RobustWebSocketClient.class.getDeclaredField("lastHeartbeatSentTs");
        sentTs.setAccessible(true);
        sentTs.setLong(client, 1000L);
        Field msgTs = RobustWebSocketClient.class.getDeclaredField("lastMessageTs");
        msgTs.setAccessible(true);
        msgTs.setLong(client, 1500L);
        client.doHeartbeatTick();
        assertFalse(stub.closed);
        assertEquals(0, missed.getInt(client));
    }
}
