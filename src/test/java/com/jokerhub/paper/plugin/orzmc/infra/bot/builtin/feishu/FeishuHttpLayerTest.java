package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import java.util.List;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FeishuApiClient / FeishuSender HTTP 层单测（OkHttp MockWebServer）：tenant token 换发、WS 端点引导
 * （AppID/AppSecret 直换 + ClientConfig 解析）、群角色查询（owner/manager 判定）、下行发送（content 为
 * JSON 字符串、token 失效重换重试一次）。
 */
class FeishuHttpLayerTest {

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

    private FeishuApiClient api() {
        return new FeishuApiClient("cli-1", "secret-1", base, base, silentLogger());
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("feishu-http-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return raw;
    }

    // =====================================================================
    // tenant_access_token
    // =====================================================================

    @Test
    void fetchTenantToken_success_postsCredentials() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"tenant_access_token\":\"t-abc\",\"expire\":7200}"));

        assertEquals("t-abc", api().fetchTenantToken());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(
                req.getPath() != null && req.getPath().contains("/auth/v3/tenant_access_token/internal"),
                req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"app_id\":\"cli-1\""), body);
        assertTrue(body.contains("\"app_secret\":\"secret-1\""), body);
    }

    @Test
    void fetchTenantToken_businessCode_returnsNull() throws Exception {
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"code\":10003,\"msg\":\"invalid app secret\"}"));

        assertNull(api().fetchTenantToken());
    }

    @Test
    void fetchTenantToken_httpError_returnsNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));
        assertNull(api().fetchTenantToken());
    }

    @Test
    void fetchTenantToken_missingToken_returnsNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"code\":0}"));
        assertNull(api().fetchTenantToken());
    }

    // =====================================================================
    // WS 端点引导（/callback/ws/endpoint）
    // =====================================================================

    @Test
    void fetchWsEndpoint_success_parsesUrlAndClientConfig() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"URL\":\"wss://gw.feishu.cn/connect\","
                        + "\"ClientConfig\":{\"PingInterval\":30,\"ReconnectInterval\":60,\"ReconnectNonce\":10}}}"));

        FeishuApiClient.WsEndpoint ep = api().fetchWsEndpoint();
        assertNotNull(ep);
        assertEquals("wss://gw.feishu.cn/connect", ep.url());
        assertEquals(30L, ep.pingIntervalSecs());
        assertEquals(60L, ep.reconnectIntervalSecs());
        assertEquals(10L, ep.reconnectNonceSecs());

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath() != null && req.getPath().contains("/callback/ws/endpoint"), req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"AppID\":\"cli-1\""), body);
        assertTrue(body.contains("\"AppSecret\":\"secret-1\""), body);
    }

    @Test
    void fetchWsEndpoint_defaultsPingIntervalWhenNoClientConfig() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"URL\":\"wss://gw.feishu.cn/connect\"}}"));

        FeishuApiClient.WsEndpoint ep = api().fetchWsEndpoint();
        assertNotNull(ep);
        assertEquals(120L, ep.pingIntervalSecs(), "服务端未下发 ClientConfig → 默认 120s（官方 SDK 默认）");
    }

    @Test
    void fetchWsEndpoint_businessCode_returnsNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"code\":20013,\"msg\":\"app not found\"}"));
        assertNull(api().fetchWsEndpoint());
    }

    // =====================================================================
    // 群角色查询（GET /im/v1/chats/{id}）
    // =====================================================================

    @Test
    void fetchChatRoles_success_bearerAuthAndRoleJudgement() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"owner_id\":\"ou_owner\","
                        + "\"user_manager_id_list\":[\"ou_mgr1\",\"ou_mgr2\"]}}"));

        FeishuApiClient.ChatRoles roles = api().fetchChatRoles("oc_chat", "t-1");
        assertNotNull(roles);
        assertEquals("ou_owner", roles.ownerId());
        assertEquals(List.of("ou_mgr1", "ou_mgr2"), roles.managerIds());
        assertTrue(roles.isOwnerOrManager("ou_owner"));
        assertTrue(roles.isOwnerOrManager("ou_mgr2"));
        assertFalse(roles.isOwnerOrManager("ou_member"));

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath() != null && req.getPath().contains("/im/v1/chats/oc_chat"), req.getPath());
        assertEquals("Bearer t-1", req.getHeader("Authorization"));
    }

    @Test
    void fetchChatRoles_businessCode_returnsNull() throws Exception {
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"code\":99991663,\"msg\":\"token invalid\"}"));
        assertNull(api().fetchChatRoles("oc_chat", "t-stale"));
    }

    @Test
    void isTokenRejected_recognizesFeishuCodes() {
        assertTrue(FeishuApiClient.isTokenRejected(401, ""));
        assertTrue(FeishuApiClient.isTokenRejected(200, "{\"code\":99991663}"));
        assertTrue(FeishuApiClient.isTokenRejected(200, "{\"code\":20005}"));
        assertFalse(FeishuApiClient.isTokenRejected(200, "{\"code\":230002}"));
    }

    // =====================================================================
    // 下行发送（POST /im/v1/messages）
    // =====================================================================

    private static final class StubTokens implements TokenProvider {
        String cached = "t-current";
        int refreshed;

        @Override
        public String current() {
            return cached;
        }

        @Override
        public String fresh() {
            return cached;
        }

        @Override
        public String onAuthFailure() {
            refreshed++;
            cached = "t-fresh";
            return cached;
        }
    }

    @Test
    void sendMessage_success_contentIsJsonString() throws Exception {
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"code\":0,\"data\":{\"message_id\":\"om_x\"}}"));

        StubTokens tokens = new StubTokens();
        FeishuSender sender = new FeishuSender(silentLogger(), tokens, base);
        assertTrue(sender.sendMessage("oc_chat", "你好").join());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath() != null && req.getPath().contains("/im/v1/messages"), req.getPath());
        assertTrue(req.getPath() != null && req.getPath().contains("receive_id_type=chat_id"), req.getPath());
        assertEquals("Bearer t-current", req.getHeader("Authorization"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"receive_id\":\"oc_chat\""), body);
        assertTrue(body.contains("\"msg_type\":\"text\""), body);
        // content 必须是 JSON 字符串（转义后的内层 {"text":"你好"}），而非嵌套对象
        assertTrue(body.contains("\"content\":\"{\\\"text\\\":\\\"你好\\\"}\""), body);
        assertEquals(0, tokens.refreshed, "成功发送不应触发 token 重换");
    }

    @Test
    void sendMessage_tokenRejected_refreshesOnceAndRetries() throws Exception {
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"code\":99991663,\"msg\":\"token invalid\"}"));
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"code\":0,\"data\":{\"message_id\":\"om_y\"}}"));

        StubTokens tokens = new StubTokens();
        FeishuSender sender = new FeishuSender(silentLogger(), tokens, base);
        assertTrue(sender.sendMessage("oc_chat", "重试").join());

        assertEquals(1, tokens.refreshed, "token 失效应重换一次");
        RecordedRequest first = server.takeRequest();
        RecordedRequest second = server.takeRequest();
        assertEquals("Bearer t-current", first.getHeader("Authorization"));
        assertEquals("Bearer t-fresh", second.getHeader("Authorization"));
    }

    @Test
    void sendMessage_httpError_returnsFalseNoRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));

        StubTokens tokens = new StubTokens();
        FeishuSender sender = new FeishuSender(silentLogger(), tokens, base);
        assertFalse(sender.sendMessage("oc_chat", "失败").join());
        assertEquals(0, tokens.refreshed, "非 token 类失败不重试（D7）");
    }
}
