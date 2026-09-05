package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TelegramApiClient HTTP 层单测（OkHttp MockWebServer）：getUpdates / sendMessage / 角色 / getMe。 */
class TelegramApiClientTest {

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

    private TelegramApiClient api() {
        return new TelegramApiClient("123456:TEST-TOKEN", base, java.net.Proxy.NO_PROXY, silentLogger());
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("telegram-http-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(Level.OFF);
        return raw;
    }

    // =====================================================================
    // getUpdates
    // =====================================================================
    @Test
    void getUpdates_success_returnsUpdates() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":[{\"update_id\":1001,\"message\":{\"message_id\":1,"
                        + "\"from\":{\"id\":11,\"is_bot\":false,\"first_name\":\"a\"},"
                        + "\"chat\":{\"id\":-100,\"type\":\"group\",\"title\":\"g\"},"
                        + "\"text\":\"hi\"}}]}"));
        TelegramApiClient.GetUpdatesResult result = api().getUpdates(1001, 30);
        assertTrue(result.ok());
        assertNull(result.error());
        assertEquals(1, result.updates().size());
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath() != null && req.getPath().contains("/getUpdates"), req.getPath());
        assertTrue(req.getPath().contains("timeout=30"), req.getPath());
        assertTrue(req.getPath().contains("offset=1001"), req.getPath());
    }

    @Test
    void getUpdates_apiError_exposesError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}"));
        TelegramApiClient.GetUpdatesResult result = api().getUpdates(0, 30);
        assertFalse(result.ok());
        assertNotNull(result.error());
    }

    @Test
    void getUpdates_httpError_returnsNotOk() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));
        TelegramApiClient.GetUpdatesResult result = api().getUpdates(0, 30);
        assertFalse(result.ok());
    }

    // =====================================================================
    // sendMessage
    // =====================================================================
    @Test
    void sendMessage_success_sendsText() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"result\":{}}"));
        assertTrue(api().sendMessage(-100123, "hello 世界"));
        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertTrue(path != null && path.contains("/sendMessage"), path);
        assertTrue(path.contains("chat_id=-100123"), path);
        assertTrue(path.contains("text="), path);
        // 文本被 URL 编码（世界 非 ASCII）
        assertTrue(path.contains("%E4%B8%96%E7%95%8C"), path);
    }

    @Test
    void sendMessage_botKicked_returnsFalse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was kicked\"}"));
        assertFalse(api().sendMessage(-100, "x"));
    }

    @Test
    void sendMessage_httpError_returnsFalse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("slow down"));
        assertFalse(api().sendMessage(-100, "x"));
    }

    // =====================================================================
    // getChatAdministrators
    // =====================================================================
    @Test
    void getChatAdministrators_returnsCreatorAndAdmin() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":[{\"status\":\"creator\",\"user\":{\"id\":1}},"
                        + "{\"status\":\"administrator\",\"user\":{\"id\":2}},"
                        + "{\"status\":\"member\",\"user\":{\"id\":3}}]}"));
        List<Long> admins = api().getChatAdministrators(-100);
        assertNotNull(admins);
        assertEquals(List.of(1L, 2L), admins); // creator + administrator；member 排除
    }

    @Test
    void getChatAdministrators_httpError_returnsNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("oops"));
        assertNull(api().getChatAdministrators(-100));
    }

    // =====================================================================
    // getMe
    // =====================================================================
    @Test
    void getMe_success_returnsUsername() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":{\"id\":123,\"is_bot\":true,\"username\":\"orz_bot\"}}"));
        assertEquals("orz_bot", api().getMe());
    }

    @Test
    void getMe_unauthorized_returnsNull() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}"));
        assertNull(api().getMe());
    }
}
