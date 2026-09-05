package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DiscordRoleResolver 单测（MockWebServer 模拟 guild owner / member roles / guild roles）：
 * 群主/管理角色权限位判定、查询失败 fail-closed、DM 恒非管理、缓存命中免 API。
 */
class DiscordRoleResolverTest {

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

    private DiscordRoleResolver resolver() {
        DiscordApiClient api = new DiscordApiClient("tok", base, Proxy.NO_PROXY, silentLogger());
        return new DiscordRoleResolver(silentLogger(), api);
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("discord-role-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(Level.OFF);
        return raw;
    }

    private static DiscordInboundMessage groupMsg(String senderId, String guildId) {
        return new DiscordInboundMessage("group", "111", guildId, "m1", "$help", senderId, "alice", false);
    }

    private boolean await(CompletableFuture<Boolean> f) throws Exception {
        return f.get(3, TimeUnit.SECONDS);
    }

    @Test
    void guildOwner_isAdmin() throws Exception {
        DiscordRoleResolver resolver = resolver();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        assertTrue(await(resolver.isAdmin(groupMsg("owner1", "g1"))));
    }

    @Test
    void memberWithAdministratorRole_isAdmin() throws Exception {
        DiscordRoleResolver resolver = resolver();
        // owner（非发送者）
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        // member roles → [r1]
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"roles\":[\"r1\"]}"));
        // guild roles → r1 具 ADMINISTRATOR(8)
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[{\"id\":\"r1\",\"permissions\":\"8\"}]"));
        assertTrue(await(resolver.isAdmin(groupMsg("userA", "g1"))));
    }

    @Test
    void memberWithManageGuildRole_isAdmin() throws Exception {
        DiscordRoleResolver resolver = resolver();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"roles\":[\"r2\"]}"));
        // r2 具 MANAGE_GUILD(32)
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[{\"id\":\"r2\",\"permissions\":\"32\"}]"));
        assertTrue(await(resolver.isAdmin(groupMsg("userB", "g1"))));
    }

    @Test
    void memberWithoutAdminRole_isNotAdmin() throws Exception {
        DiscordRoleResolver resolver = resolver();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"roles\":[\"r3\"]}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[{\"id\":\"r3\",\"permissions\":\"0\"}]"));
        assertFalse(await(resolver.isAdmin(groupMsg("userC", "g1"))));
    }

    @Test
    void memberWithNoRoles_isNotAdmin() throws Exception {
        DiscordRoleResolver resolver = resolver();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"roles\":[]}"));
        assertFalse(await(resolver.isAdmin(groupMsg("userD", "g1"))));
    }

    @Test
    void roleQueryFailure_failsClosed() throws Exception {
        DiscordRoleResolver resolver = resolver();
        // owner / member / roles 三段查询均 403
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
        assertFalse(await(resolver.isAdmin(groupMsg("userE", "g1"))), "查询失败按非管理处理（fail-closed）");
    }

    @Test
    void dm_alwaysNotAdmin() throws Exception {
        DiscordRoleResolver resolver = resolver();
        DiscordInboundMessage dm = new DiscordInboundMessage("user", "777", null, "m1", "$h", "333", "alice", false);
        assertFalse(await(resolver.isAdmin(dm)));
        assertEquals(0, server.getRequestCount(), "DM 判定不发任何 API 请求");
    }

    @Test
    void resultCached_withinTtlNoExtraRequests() throws Exception {
        DiscordRoleResolver resolver = resolver();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        assertTrue(await(resolver.isAdmin(groupMsg("owner1", "g1"))));
        // owner/成员角色/角色权限按 guild 缓存：同 key 二次判定命中结果缓存，不发新请求
        assertTrue(await(resolver.isAdmin(groupMsg("owner1", "g1"))));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void differentUsers_doNotShareResultCache() throws Exception {
        DiscordRoleResolver resolver = resolver();
        // owner 判定 user1
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        assertTrue(await(resolver.isAdmin(groupMsg("owner1", "g1"))));
        // user2 非 owner → 需要成员角色查询
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"roles\":[]}"));
        assertFalse(await(resolver.isAdmin(groupMsg("user2", "g1"))));
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void asyncNeverThrows_onMalformedServer() throws Exception {
        DiscordRoleResolver resolver = resolver();
        // owner 坏 JSON → null → 继续 member（坏 JSON → null）→ 继续 roles（坏 JSON → null）→ false
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{bad json"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{bad json"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{bad json"));
        try {
            assertFalse(await(resolver.isAdmin(groupMsg("userX", "g1"))));
        } catch (Exception e) {
            fail("角色判定异常应被内部兜底为 false，不抛出: " + e);
        }
    }
}
