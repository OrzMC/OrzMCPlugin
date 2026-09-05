package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.net.Proxy;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DiscordApiClient 单测（MockWebServer）：网关地址引导/认证头/发消息/DM 通道/角色数据源解析。 */
class DiscordApiClientTest {

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

    private DiscordApiClient client() {
        return new DiscordApiClient("tok-123", base, Proxy.NO_PROXY, silentLogger());
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("discord-api-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(Level.OFF);
        return raw;
    }

    @Test
    void fetchGatewayUrl_returnsUrl_andSendsAuthHeader() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"url\":\"https://gateway.discord.gg/?v=10&encoding=json\"}"));
        String url = client().fetchGatewayUrl();
        assertEquals("wss://gateway.discord.gg/?v=10&encoding=json", url);
        RecordedRequest req = server.takeRequest();
        assertEquals("/gateway/bot", req.getPath());
        assertEquals("Bot tok-123", req.getHeader("Authorization"));
    }

    @Test
    void fetchGatewayUrl_cachedWithinWindow() throws Exception {
        DiscordApiClient api = client();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"url\":\"https://gateway.discord.gg/\"}"));
        assertNotNull(api.fetchGatewayUrl());
        assertNotNull(api.fetchGatewayUrl(), "缓存窗口内二次调用复用，不发新请求");
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void fetchGatewayUrl_http401_returnsNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"401: Unauthorized\"}"));
        assertNull(client().fetchGatewayUrl());
    }

    @Test
    void sendChannelMessage_postsContent() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"m1\"}"));
        assertTrue(client().sendChannelMessage("111", "hello dc"));
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/channels/111/messages", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"content\":\"hello dc\""));
        assertEquals("Bot tok-123", req.getHeader("Authorization"));
    }

    @Test
    void sendChannelMessage_http4xx_returnsFalse() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"Missing Permissions\"}"));
        assertFalse(client().sendChannelMessage("111", "x"));
    }

    @Test
    void sendChannelMessage_blankText_returnsFalse() {
        assertFalse(client().sendChannelMessage("111", ""));
        assertEquals(0, server.getRequestCount(), "空文本不发请求");
    }

    @Test
    void ensureDmChannel_createsAndCaches() throws Exception {
        DiscordApiClient api = client();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"dm999\"}"));
        assertEquals("dm999", api.ensureDmChannel("user333"));
        RecordedRequest req = server.takeRequest();
        assertEquals("/users/@me/channels", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"recipient_id\":\"user333\""));
        // 缓存命中：二次调用不发请求
        assertEquals("dm999", api.ensureDmChannel("user333"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void ensureDmChannel_http4xx_returnsNull() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{}"));
        assertNull(client().ensureDmChannel("user333"));
    }

    @Test
    void guildOwnerAndMemberAndRoles_parsed() throws Exception {
        DiscordApiClient api = client();
        // owner
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"g1\",\"owner_id\":\"owner1\"}"));
        assertEquals("owner1", api.getGuildOwner("g1"));
        // member roles
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"user\":{\"id\":\"u1\"},\"roles\":[\"r1\",\"r2\"]}"));
        List<String> roles = api.getGuildMemberRoles("g1", "u1");
        assertEquals(List.of("r1", "r2"), roles);
        // guild roles permissions（十进制权限位串 → BigInteger）
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"id\":\"r1\",\"permissions\":\"8\"},{\"id\":\"r2\",\"permissions\":\"8388608\"}]"));
        Map<String, BigInteger> perms = api.getGuildRolesPermissions("g1");
        assertEquals(2, perms.size());
        assertTrue(perms.get("r1").testBit(3), "r1 具 ADMINISTRATOR(8)");
        assertTrue(perms.get("r2").testBit(23), "r2 具 8388608=1<<23");
    }

    @Test
    void roleQueries_http403_returnNull() {
        DiscordApiClient api = client();
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
        assertNull(api.getGuildOwner("g1"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
        assertNull(api.getGuildMemberRoles("g1", "u1"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
        assertNull(api.getGuildRolesPermissions("g1"));
    }
}
