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
 * 401 与业务码 11244 → AUTH（11242 属可重试系统错，不归 AUTH，见 2026-09 官方错误码表）、非 2xx/缺 url → TRANSIENT。
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
    void fetchGatewayUrl_currentTokenInvalidShape_isAuth() throws Exception {
        // 2026-09 实探测的实际响应体（假 token 直连三接口均为此形）
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"message\":\"AccessToken无效或过期\",\"code\":11244,\"err_code\":40011027}"));

        assertEquals(QqGatewayUrlFetcher.Status.AUTH, api().fetch("tok-expired").status());
    }

    @Test
    void fetchGatewayUrl_businessCode11242_isNotAuth() throws Exception {
        // 官方公共错误码表（2026）：11242 = “校验 token 失败，系统错误，一般重试一次会好”——
        // 属可重试瞬态错，不是凭据失效；若误判为 AUTH 会白白强制换 token。
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"code\":11242,\"message\":\"check token failed\"}"));

        assertEquals(QqGatewayUrlFetcher.Status.TRANSIENT, api().fetch("tok-1").status());
    }

    @Test
    void defaultAuthBase_alignsWithOfficialDoc() {
        // 官方《获取访问凭证》只列 https://api.bot.qq.com/app/getAppAccessToken
        // （旧别名 bots.qq.com 实测仍可用但未文档化，不再作为默认值）
        assertEquals("https://api.bot.qq.com", QqApiClient.DEFAULT_AUTH_BASE);
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
