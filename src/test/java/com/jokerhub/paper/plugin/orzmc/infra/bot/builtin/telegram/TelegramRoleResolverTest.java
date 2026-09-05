package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TelegramRoleResolver 单测（MockWebServer 模拟 getChatAdministrators）：creator/administrator 判定、单聊恒非管理、缓存命中免 API。 */
class TelegramRoleResolverTest {

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

    private TelegramRoleResolver resolver() {
        TelegramApiClient api = new TelegramApiClient("123:TOK", base, java.net.Proxy.NO_PROXY, silentLogger());
        return new TelegramRoleResolver(silentLogger(), api);
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("telegram-role-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(Level.OFF);
        return raw;
    }

    private static TelegramInboundMessage groupMsg(long senderId, long chatId) {
        return new TelegramInboundMessage("group", chatId, 1, "hi", senderId, false);
    }

    private boolean await(CompletableFuture<Boolean> f) throws Exception {
        return f.get(3, TimeUnit.SECONDS);
    }

    @Test
    void creatorIsAdmin() throws Exception {
        TelegramRoleResolver resolver = resolver();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":[{\"status\":\"creator\",\"user\":{\"id\":1}}]}"));
        assertTrue(await(resolver.isAdmin(groupMsg(1, -100))));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void administratorIsAdmin_memberIsNot() throws Exception {
        TelegramRoleResolver resolver = resolver();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":[{\"status\":\"creator\",\"user\":{\"id\":1}},"
                        + "{\"status\":\"administrator\",\"user\":{\"id\":2}},"
                        + "{\"status\":\"member\",\"user\":{\"id\":3}}]}"));
        // 两次独立 sender 查询 → 两个 API 响应
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":[{\"status\":\"creator\",\"user\":{\"id\":1}},"
                        + "{\"status\":\"administrator\",\"user\":{\"id\":2}},"
                        + "{\"status\":\"member\",\"user\":{\"id\":3}}]}"));
        assertTrue(await(resolver.isAdmin(groupMsg(2, -100))), "administrator → 管理");
        assertFalse(await(resolver.isAdmin(groupMsg(3, -100))), "member → 非管理");
        assertEquals(2, server.getRequestCount(), "不同 sender 各自查询一次");
    }

    @Test
    void cacheHit_avoidsSecondApiCall() throws Exception {
        TelegramRoleResolver resolver = resolver();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":[{\"status\":\"creator\",\"user\":{\"id\":1}}]}"));
        assertTrue(await(resolver.isAdmin(groupMsg(1, -100))));
        assertTrue(await(resolver.isAdmin(groupMsg(1, -100))), "同 sender 缓存命中免第二次 API");
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void queryFailure_isNonAdmin() throws Exception {
        TelegramRoleResolver resolver = resolver();
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));
        assertFalse(await(resolver.isAdmin(groupMsg(1, -100))), "查询失败按非管理处理");
    }

    @Test
    void privateChat_isNeverAdmin() throws Exception {
        TelegramRoleResolver resolver = resolver();
        TelegramInboundMessage dm = new TelegramInboundMessage("user", 300, 1, "hi", 300, false);
        assertFalse(await(resolver.isAdmin(dm)), "私聊无角色 → 非管理（跨平台一致）");
        assertEquals(0, server.getRequestCount(), "私聊不发 API 查询");
    }
}
