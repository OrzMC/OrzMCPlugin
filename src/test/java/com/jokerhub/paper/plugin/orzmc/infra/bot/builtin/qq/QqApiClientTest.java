package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QqApiClient HTTP 层单测（OkHttp MockWebServer）：token 换发请求体/错误分类、网关 URL 获取鉴权头/
 * 401 与业务码 11244/11242 → AUTH、非 2xx/缺 url → TRANSIENT。
 */
class QqApiClientTest {

    private MockWebServer server;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        base = server.url("/").toString().replaceAll("/$", "");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private QqApiClient api() {
        return new QqApiClient("app-1", "secret-1", base, base, silentLogger());
    }

    // =====================================================================
    // getAppAccessToken
    // =====================================================================

    @Test
    void fetchAccessToken_success_returnsTokenAndPostsCredentials() throws Exception {
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"access_token\":\"tok-abc\",\"expires_in\":7200}"));

        assertEquals("tok-abc", api().fetchAccessToken());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath() != null && req.getPath().endsWith("/app/getAppAccessToken"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"appId\":\"app-1\""), body);
        assertTrue(body.contains("\"clientSecret\":\"secret-1\""), body);
    }

    @Test
    void fetchAccessToken_businessCode_returnsNull() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":100016,\"message\":\"credentials invalid\"}"));

        assertNull(api().fetchAccessToken());
        assertEquals("POST", server.takeRequest().getMethod());
    }

    @Test
    void fetchAccessToken_httpError_returnsNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));

        assertNull(api().fetchAccessToken());
    }

    @Test
    void fetchAccessToken_missingAccessToken_returnsNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        assertNull(api().fetchAccessToken());
    }

    // =====================================================================
    // 网关地址 GET /gateway/bot
    // =====================================================================

    @Test
    void fetchGatewayUrl_success_sendsQqBotAuthHeader() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"url\":\"wss://gw.example/\"}"));

        QqGatewayUrlFetcher.Result result = api().fetch("tok-1");
        assertEquals(QqGatewayUrlFetcher.Status.SUCCESS, result.status());
        assertEquals("wss://gw.example/", result.url());

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath() != null && req.getPath().endsWith("/gateway/bot"));
        assertEquals("QQBot tok-1", req.getHeader("Authorization"));
    }

    @Test
    void fetchGatewayUrl_http401_isAuth() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"token invalid\"}"));

        assertEquals(QqGatewayUrlFetcher.Status.AUTH, api().fetch("tok-expired").status());
    }

    @Test
    void fetchGatewayUrl_businessCode11244_isAuth() throws Exception {
        // QQ 偶发 HTTP 200 + 业务码 11244（token not exist or expire）
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":11244,\"message\":\"token not exist or expire\"}"));

        assertEquals(QqGatewayUrlFetcher.Status.AUTH, api().fetch("tok-expired").status());
    }

    @Test
    void fetchGatewayUrl_http500_isTransient() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));

        assertEquals(QqGatewayUrlFetcher.Status.TRANSIENT, api().fetch("tok-1").status());
    }

    @Test
    void fetchGatewayUrl_missingUrlField_isTransient() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"code\":0}"));

        assertEquals(QqGatewayUrlFetcher.Status.TRANSIENT, api().fetch("tok-1").status());
    }

    @Test
    void constructor_rejectsBlankCredentials() {
        try {
            new QqApiClient("", "secret", silentLogger());
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("qq-api-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return raw;
    }
}
