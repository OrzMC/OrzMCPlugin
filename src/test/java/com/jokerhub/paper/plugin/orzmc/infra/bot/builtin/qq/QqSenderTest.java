package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QqSender HTTP 层单测（OkHttp MockWebServer）：群/私聊 URL、请求体（msg_type=0、msg_id 被动回复）、
 * QQBot 鉴权头、401 → 令牌重换重试一次、非 2xx 尽力一次不重试。
 */
class QqSenderTest {

    private MockWebServer server;
    private String base;
    private FakeTokens tokens;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        base = server.url("/").toString().replaceAll("/$", "");
        tokens = new FakeTokens("tok-0");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private QqSender sender() {
        return new QqSender(silentLogger(), tokens, base);
    }

    private static boolean awaitSend(java.util.concurrent.CompletableFuture<Boolean> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    // =====================================================================
    // 用例
    // =====================================================================

    @Test
    void sendGroupMessage_postsExpectedUrlBodyAndAuth() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"mid-1\"}"));

        assertTrue(awaitSend(sender().sendGroupMessage("GROUP-1", "你好", null)));

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath() != null && req.getPath().endsWith("/v2/groups/GROUP-1/messages"));
        assertEquals("QQBot tok-0", req.getHeader("Authorization"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"content\":\"你好\""), body);
        assertTrue(body.contains("\"msg_type\":0"), body);
        assertFalse(body.contains("msg_id"), "无回复 id 时不应带 msg_id: " + body);
    }

    @Test
    void passiveReply_includesMsgId() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"mid-2\"}"));

        assertTrue(awaitSend(sender().sendGroupMessage("GROUP-1", "回复", "src-msg-9")));

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"msg_id\":\"src-msg-9\""), body);
    }

    @Test
    void sendDirectMessage_targetsUserEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"mid-3\"}"));

        assertTrue(awaitSend(sender().sendDirectMessage("USER-7", "私聊", null)));

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath() != null && req.getPath().endsWith("/v2/users/USER-7/messages"));
    }

    @Test
    void tokenRejected_refreshesOnceAndRetriesOnce() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"code\":11244}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"mid-4\"}"));

        assertTrue(awaitSend(sender().sendGroupMessage("GROUP-1", "重试", null)));
        assertEquals(1, tokens.authFailures.get(), "401 应强制重换一次");

        RecordedRequest first = server.takeRequest();
        assertEquals("QQBot tok-0", first.getHeader("Authorization"));
        RecordedRequest second = server.takeRequest();
        assertEquals("QQBot fresh-1", second.getHeader("Authorization"), "重试应携带重换后的 token");
    }

    @Test
    void tokenRejected_twice_returnsFalseAfterSingleRefresh() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));

        assertFalse(awaitSend(sender().sendGroupMessage("GROUP-1", "失败", null)));
        assertEquals(1, tokens.authFailures.get(), "只应重换一次，不再无限重试");
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void serverError_failsWithoutRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));

        assertFalse(awaitSend(sender().sendGroupMessage("GROUP-1", "错误", null)));
        assertEquals(0, tokens.authFailures.get());
        assertEquals(1, server.getRequestCount(), "尽力一次不重试（D7）");
    }

    @Test
    void blankText_rejected() {
        assertThrows(IllegalArgumentException.class, () -> sender().sendGroupMessage("G", "  ", null));
    }

    @Test
    void connectPhaseFailure_retriesOnceThenReportsPhase() throws Exception {
        java.util.List<String> logs = new java.util.ArrayList<>();
        int deadPort = freePort();
        QqSender sender = new QqSender(capturingLogger(logs), tokens, "http://127.0.0.1:" + deadPort);

        assertFalse(awaitSend(sender.sendGroupMessage("GROUP-1", "连不上", null)));

        assertEquals(1, logs.stream().filter(m -> m.contains("重试一次投递")).count(), "连接阶段应恰好兑底重试一次: " + logs);
        assertTrue(logs.stream().anyMatch(m -> m.contains("连接阶段异常，已重试一次")), "最终告警应标明阶段: " + logs);
        assertEquals(0, tokens.authFailures.get(), "连接失败不应触发令牌重换");
    }

    @Test
    void connectPhaseClassification_acceptsOnlyProvablyUnsentFailures() {
        // 连接阶段（请求未发出，重试安全）
        assertTrue(QqSender.isConnectPhaseFailure(
                new java.net.http.HttpConnectTimeoutException("HTTP connect timed out")));
        assertTrue(QqSender.isConnectPhaseFailure(new java.net.ConnectException("Connection refused")));
        assertTrue(QqSender.isConnectPhaseFailure(new java.net.NoRouteToHostException("No route to host")));
        assertTrue(QqSender.isConnectPhaseFailure(new java.net.UnknownHostException("api.bot.qq.com")));
        assertTrue(QqSender.isConnectPhaseFailure(new javax.net.ssl.SSLHandshakeException("handshake_failure")));
        // 请求阶段（可能已投递，重试会产生重复通知）或非网络异常
        assertFalse(QqSender.isConnectPhaseFailure(new java.net.http.HttpTimeoutException("request timed out")));
        assertFalse(QqSender.isConnectPhaseFailure(new java.io.IOException("broken pipe")));
        assertFalse(QqSender.isConnectPhaseFailure(new java.util.concurrent.TimeoutException()));
    }

    // =====================================================================
    // 替身
    // =====================================================================

    static final class FakeTokens implements TokenProvider {
        final AtomicInteger authFailures = new AtomicInteger();
        volatile String token;

        FakeTokens(String initial) {
            this.token = initial;
        }

        @Override
        public String current() {
            return token;
        }

        @Override
        public String fresh() {
            return token;
        }

        @Override
        public String onAuthFailure() {
            token = "fresh-" + authFailures.incrementAndGet();
            return token;
        }
    }

    private static int freePort() throws java.io.IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 采集 INFO 及以上日志文本（验证兑底重试与阶段化告警）。 */
    private static Logger capturingLogger(java.util.List<String> sink) {
        Logger raw = Logger.getLogger("qq-sender-capture");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.ALL);
        for (java.util.logging.Handler handler : raw.getHandlers()) {
            raw.removeHandler(handler);
        }
        raw.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.INFO.intValue()) {
                    sink.add(String.valueOf(record.getMessage()));
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return raw;
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("qq-sender-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return raw;
    }
}
