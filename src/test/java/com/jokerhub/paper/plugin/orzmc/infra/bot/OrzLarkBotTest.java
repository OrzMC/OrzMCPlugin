package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class OrzLarkBotTest {

    private ServerAccess server;
    private ServerLogger logger;
    private ConfigService configService;
    private MessageFormatter formatter;
    private ThrottledLogger throttledLogger;
    private HealthRegistry healthRegistry;
    private FileConfiguration botConfig;
    private OrzLarkBot bot;

    @BeforeEach
    void setUp() {
        server = mock(ServerAccess.class);
        logger = mock(ServerLogger.class);
        configService = mock(ConfigService.class);
        formatter = mock(MessageFormatter.class);
        throttledLogger = mock(ThrottledLogger.class);
        healthRegistry = spy(new HealthRegistry());
        botConfig = mock(FileConfiguration.class);

        when(logger.logger()).thenReturn(mock(Logger.class));
        when(configService.getConfig("bot")).thenReturn(botConfig);
        when(formatter.format(anyString(), any(MessageEnvelope.Format.class)))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0)));

        bot = new OrzLarkBot(server, logger, configService, formatter, throttledLogger, healthRegistry);
    }

    // ---------------------------------------------------------------
    // sendPublic
    // ---------------------------------------------------------------

    @Test
    void sendPublic_disabled_doesNothing() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(false);

        bot.sendPublic("test message");

        verify(healthRegistry, never()).setEnabled(eq("lark"), anyBoolean());
        verify(formatter, never()).format(anyString(), any());
    }

    @Test
    void sendPublic_enabled_sendsMessage() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        when(botConfig.getString("lark_bot_webhook")).thenReturn("https://webhook.example.com/lark");
        when(botConfig.getInt("http_max_retries")).thenReturn(3);
        when(botConfig.getLong("http_connect_timeout_seconds")).thenReturn(3L);
        when(botConfig.getLong("http_request_timeout_seconds")).thenReturn(3L);

        // Mock the static AsyncHttp.postJson to return a successful response
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(response);
            asyncHttp
                    .when(() -> AsyncHttp.postJson(
                            anyString(), anyString(), any(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            bot.sendPublic("hello from lark");

            // Synchronous verifications (happen in sendPublic before async callbacks)
            verify(healthRegistry).setEnabled("lark", true);
            verify(formatter).format("hello from lark", MessageEnvelope.Format.DEFAULT);
            // throttledLogger.info in thenAcceptAsync runs asynchronously — not verified here
        }
    }

    @Test
    void sendPublic_enabled_httpError_handlesAsync() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        when(botConfig.getString("lark_bot_webhook")).thenReturn("https://webhook.example.com/lark");
        when(botConfig.getInt("http_max_retries")).thenReturn(3);
        when(botConfig.getLong("http_connect_timeout_seconds")).thenReturn(3L);
        when(botConfig.getLong("http_request_timeout_seconds")).thenReturn(3L);

        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            CompletableFuture<HttpResponse<String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Network error"));

            asyncHttp
                    .when(() -> AsyncHttp.postJson(
                            anyString(), anyString(), any(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(failedFuture);

            bot.sendPublic("message that fails");

            // Synchronous behavior only: setEnabled happens in sendPublic before async callbacks
            verify(healthRegistry).setEnabled("lark", true);
            // exceptionally callback runs async (via ForkJoinPool) — not verified synchronously here
        }
    }

    @Test
    void sendPublic_exceptionDuringSend_caughtAndLogged() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        // Return null URL to cause NullPointerException when formatter.format() is called
        // Actually formatter.format returns List.of(msg), then asyncHttpRequest is called
        // asyncHttpRequest uses botConfig.getString which can throw
        when(botConfig.getString("lark_bot_webhook")).thenThrow(new RuntimeException("Config error"));

        bot.sendPublic("trigger exception");

        verify(logger.logger()).info(contains("Config error"));
    }

    // ---------------------------------------------------------------
    // sendPrivate (no-op)
    // ---------------------------------------------------------------

    @Test
    void sendPrivate_doesNothing() {
        bot.sendPrivate("private message");
        // No interaction with formatter, health registry, etc.
        verifyNoInteractions(formatter);
        verify(healthRegistry, never()).setEnabled(anyString(), anyBoolean());
    }

    // ---------------------------------------------------------------
    // sendChannel
    // ---------------------------------------------------------------

    @Test
    void sendChannel_disabled_doesNothing() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(false);

        bot.sendChannel("admin", "admin message");

        verify(formatter, never()).format(anyString(), any());
    }

    @Test
    void sendChannel_enabled_withUrl_sendsToChannel() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        when(botConfig.getString("channels.admin.lark")).thenReturn("https://webhook.example.com/admin");
        when(botConfig.getInt("http_max_retries")).thenReturn(3);
        when(botConfig.getLong("http_connect_timeout_seconds")).thenReturn(3L);
        when(botConfig.getLong("http_request_timeout_seconds")).thenReturn(3L);

        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(response);
            asyncHttp
                    .when(() -> AsyncHttp.postJson(
                            anyString(), anyString(), any(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            bot.sendChannel("admin", "admin channel message");

            verify(botConfig).getString("channels.admin.lark");
            verify(formatter).format("admin channel message", MessageEnvelope.Format.DEFAULT);
        }
    }

    @Test
    void sendChannel_withEmptyUrl_fallsBackToSendPublic() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        when(botConfig.getString("channels.admin.lark")).thenReturn("");
        when(botConfig.getString("lark_bot_webhook")).thenReturn("https://webhook.example.com/lark");
        when(botConfig.getInt("http_max_retries")).thenReturn(3);
        when(botConfig.getLong("http_connect_timeout_seconds")).thenReturn(3L);
        when(botConfig.getLong("http_request_timeout_seconds")).thenReturn(3L);

        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(response);
            asyncHttp
                    .when(() -> AsyncHttp.postJson(
                            anyString(), anyString(), any(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            bot.sendChannel("admin", "fallback to public");

            // Should fall back to the lark_bot_webhook URL, not channels.admin.lark
            verify(botConfig).getString("lark_bot_webhook");
        }
    }

    @Test
    void sendChannel_withNullUrl_fallsBackToSendPublic() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        when(botConfig.getString("channels.admin.lark")).thenReturn(null);
        when(botConfig.getString("lark_bot_webhook")).thenReturn("https://webhook.example.com/lark");
        when(botConfig.getInt("http_max_retries")).thenReturn(3);
        when(botConfig.getLong("http_connect_timeout_seconds")).thenReturn(3L);
        when(botConfig.getLong("http_request_timeout_seconds")).thenReturn(3L);

        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(response);
            asyncHttp
                    .when(() -> AsyncHttp.postJson(
                            anyString(), anyString(), any(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            bot.sendChannel("admin", "null url fallback");

            verify(botConfig).getString("lark_bot_webhook");
        }
    }

    // ---------------------------------------------------------------
    // lifecycle
    // ---------------------------------------------------------------

    @Test
    void isEnable_checksConfig() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        assertTrue(bot.isEnable());

        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(false);
        assertFalse(bot.isEnable());
    }

    @Test
    void setup_doesNothing() {
        bot.setup();
        // No-op, just verify no exception
    }

    @Test
    void teardown_doesNothing() {
        bot.teardown();
        // No-op, just verify no exception
    }

    // ---------------------------------------------------------------
    // send(MessageEnvelope) inherited from OrzBaseBot
    // ---------------------------------------------------------------

    @Test
    void send_envelope_public_dispatchesToSendPublic() {
        when(botConfig.getBoolean("enable_lark_bot")).thenReturn(true);
        when(botConfig.getString("lark_bot_webhook")).thenReturn("https://webhook.example.com/lark");
        when(botConfig.getInt("http_max_retries")).thenReturn(3);
        when(botConfig.getLong("http_connect_timeout_seconds")).thenReturn(3L);
        when(botConfig.getLong("http_request_timeout_seconds")).thenReturn(3L);

        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(response);
            asyncHttp
                    .when(() -> AsyncHttp.postJson(
                            anyString(), anyString(), any(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            bot.send(MessageEnvelope.publicMessage("hello via envelope"));

            verify(formatter).format("hello via envelope", MessageEnvelope.Format.DEFAULT);
        }
    }

    @Test
    void send_envelope_private_isNoOp() {
        bot.send(MessageEnvelope.privateMessage("private via envelope"));
        verifyNoInteractions(formatter);
    }
}
