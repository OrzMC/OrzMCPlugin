package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuApiClient.ChatRoles;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
 * FeishuRoleResolver 单测（MockWebServer 模拟 chats API）：群主/管理员/普通成员判定、单聊恒非管理、
 * 缓存命中免 API、并发单飞、token 失效重换一次。
 */
class FeishuRoleResolverTest {

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

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("feishu-role-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return raw;
    }

    private static final class StubTokens
            implements com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider {
        String cached = "t-1";
        AtomicInteger refreshCount = new AtomicInteger();

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
            refreshCount.incrementAndGet();
            cached = "t-2";
            return cached;
        }
    }

    private FeishuRoleResolver resolver(StubTokens tokens) {
        FeishuApiClient api = new FeishuApiClient("cli-1", "s-1", base, base, silentLogger());
        return new FeishuRoleResolver(silentLogger(), api, tokens);
    }

    private FeishuInboundMessage groupMsg(String senderId) {
        return new FeishuInboundMessage("group", "oc_chat", "om_1", "hi", senderId, "user");
    }

    private boolean await(CompletableFuture<Boolean> f) throws Exception {
        return f.get(3, TimeUnit.SECONDS);
    }

    @Test
    void ownerAndManagerAreAdmin_memberIsNot() throws Exception {
        StubTokens tokens = new StubTokens();
        FeishuRoleResolver resolver = resolver(tokens);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"owner_id\":\"ou_owner\","
                        + "\"user_manager_id_list\":[\"ou_mgr\"]}}"));

        assertTrue(await(resolver.isAdmin(groupMsg("ou_owner"))), "群主是管理");
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void memberIsNotAdmin() throws Exception {
        StubTokens tokens = new StubTokens();
        FeishuRoleResolver resolver = resolver(tokens);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"owner_id\":\"ou_owner\","
                        + "\"user_manager_id_list\":[\"ou_mgr\"]}}"));
        assertFalse(await(resolver.isAdmin(groupMsg("ou_member"))));
    }

    @Test
    void cacheHit_skipsApi() throws Exception {
        StubTokens tokens = new StubTokens();
        FeishuRoleResolver resolver = resolver(tokens);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"owner_id\":\"ou_owner\",\"user_manager_id_list\":[]}}"));

        assertTrue(await(resolver.isAdmin(groupMsg("ou_owner"))));
        assertTrue(await(resolver.isAdmin(groupMsg("ou_owner"))), "缓存命中，第二次免 API");
        assertEquals(1, server.getRequestCount(), "两次查询只发一次 API（缓存）");
    }

    @Test
    void concurrentSameKey_singleFlight() throws Exception {
        StubTokens tokens = new StubTokens();
        FeishuRoleResolver resolver = resolver(tokens);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"owner_id\":\"ou_owner\",\"user_manager_id_list\":[]}}"));

        CompletableFuture<Boolean> f1 = resolver.isAdmin(groupMsg("ou_owner"));
        CompletableFuture<Boolean> f2 = resolver.isAdmin(groupMsg("ou_owner"));
        assertTrue(await(f1));
        assertTrue(await(f2));
        assertEquals(1, server.getRequestCount(), "并发同 key 只发一次 API（单飞）");
    }

    @Test
    void p2pMessage_alwaysNotAdmin() throws Exception {
        StubTokens tokens = new StubTokens();
        FeishuRoleResolver resolver = resolver(tokens);
        FeishuInboundMessage dm = new FeishuInboundMessage("user", "oc_dm", "om_2", "hi", "ou_any", "user");
        assertFalse(await(resolver.isAdmin(dm)));
        assertEquals(0, server.getRequestCount(), "单聊不发 API");
    }

    @Test
    void apiError_treatedAsNotAdmin_andRetriesOnceOnTokenRefresh() throws Exception {
        StubTokens tokens = new StubTokens();
        FeishuRoleResolver resolver = resolver(tokens);
        // 第一次：token 失效（99991663）；重换 t-2 后第二次成功
        server.enqueue(
                new MockResponse().setResponseCode(200).setBody("{\"code\":99991663,\"msg\":\"token invalid\"}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"owner_id\":\"ou_owner\",\"user_manager_id_list\":[]}}"));

        assertTrue(await(resolver.isAdmin(groupMsg("ou_owner"))), "token 失效重换后应判定成功");
        assertEquals(1, tokens.refreshCount.get(), "失效触发一次重换");
        assertEquals(2, server.getRequestCount());

        RecordedRequest first = server.takeRequest();
        RecordedRequest second = server.takeRequest();
        assertEquals("Bearer t-1", first.getHeader("Authorization"));
        assertEquals("Bearer t-2", second.getHeader("Authorization"));
    }

    @Test
    void chatRoles_judgementDirect() {
        ChatRoles roles = new ChatRoles("ou_owner", List.of("ou_mgr"));
        assertTrue(roles.isOwnerOrManager("ou_owner"));
        assertTrue(roles.isOwnerOrManager("ou_mgr"));
        assertFalse(roles.isOwnerOrManager("ou_member"));
        assertFalse(roles.isOwnerOrManager(null));
        assertFalse(roles.isOwnerOrManager(""));
    }
}
