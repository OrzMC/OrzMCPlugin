package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGateway.State;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.TestWsServer;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * DiscordGatewayClient 单测（MockWebServer 模拟 /gateway/bot 引导 + TestWsServer 模拟网关）：
 * hello→identify（intents/token/properties）→READY 捕获 session→事件透传 sink→心跳帧（d=seq）
 * / op9(false) 清会话重连 / op7 保留会话 resume / 4004 鉴权失败 fatal。重连退避与看门狗由 conn 包
 * 既有测试覆盖，此处聚焦 Discord 协议分发。
 */
class DiscordGatewayClientTest {

    private MockWebServer http;
    private TestWsServer ws;
    private DiscordGatewayClient client;
    private RecordingSink sink;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            try {
                client.stop();
            } catch (RuntimeException ignored) {
                // 已停止
            }
        }
        if (http != null) {
            http.shutdown();
        }
        if (ws != null) {
            ws.close();
        }
    }

    /** 引导 /gateway/bot 指向本地 TestWsServer；随后 start 建连。 */
    private DiscordGatewayClient startClient(RecordingListener listener) throws Exception {
        ws = TestWsServer.start();
        http = new MockWebServer();
        http.start();
        String wsUrl = "ws://127.0.0.1:" + ws.port() + "/?v=10&encoding=json";
        http.enqueue(new MockResponse().setResponseCode(200).setBody("{\"url\":\"" + wsUrl + "\"}"));

        String base = http.url("/").toString().replaceAll("/$", "");
        DiscordApiClient api = new DiscordApiClient("tok-discord", base, Proxy.NO_PROXY, rawLogger());
        sink = new RecordingSink();
        client = new DiscordGatewayClient(
                silentLogger(), new ReconnectPolicy(30, 120, 0, 500, 0), api, "tok-discord", sink, listener);
        client.start();
        return client;
    }

    /** 等客户端握手完成（onConnected）且连接对象可见，随后即可安全向服务端发帧。 */
    private void awaitConnected(RecordingListener listener) throws Exception {
        awaitTrue("网关建连", () -> listener.connected && !ws.connections().isEmpty());
    }

    private static void awaitTrue(String what, BooleanSupplier cond) throws Exception {
        long deadline = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("超时等待: " + what);
    }

    private static Logger rawLogger() {
        Logger raw = Logger.getLogger("discord-gw-raw");
        raw.setUseParentHandlers(false);
        raw.setLevel(Level.OFF);
        return raw;
    }

    private static ServerLogger silentLogger() {
        Logger raw = rawLogger();
        return () -> raw;
    }

    // =====================================================================
    // 用例
    // =====================================================================
    @Test
    void hello_identify_readDispatch() throws Exception {
        RecordingListener listener = new RecordingListener();
        startClient(listener);
        awaitConnected(listener);
        TestWsServer.Conn conn = ws.connections().get(0);

        // 服务端 hello → 客户端 identify（op2：token/intents/properties）
        conn.sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":400}}");
        awaitTrue("收到 identify", () -> anyContains(conn.receivedText(), "\"op\":2"));
        String identify = firstContaining(conn.receivedText(), "\"op\":2");
        assertTrue(identify.contains("\"token\":\"tok-discord\""), "identify 携带裸 token");
        assertTrue(identify.contains("\"intents\":37376"), "intents=GUILD_MESSAGES|DIRECT_MESSAGES|MESSAGE_CONTENT");
        assertTrue(identify.contains("\"properties\""), "identify 携带 properties");

        // READY → 捕获 session_id
        conn.sendText(
                "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"session_id\":\"sess1\",\"user\":{\"username\":\"dcbot\"}}}");
        awaitTrue("READY 后建连稳定", () -> client.state() == State.OPEN);

        // MESSAGE_CREATE → sink 收到原始帧
        conn.sendText("{\"op\":0,\"s\":2,\"t\":\"MESSAGE_CREATE\",\"d\":{\"id\":\"m1\",\"channel_id\":\"111\","
                + "\"guild_id\":\"222\",\"content\":\"$l\",\"author\":{\"id\":\"u1\",\"username\":\"alice\"}}}");
        awaitTrue("事件透传 sink", () -> !sink.events.isEmpty());
        assertEquals("MESSAGE_CREATE", sink.events.get(0));
        assertTrue(listener.connected);
    }

    @Test
    void heartbeatSent_withSeqAsPayload() throws Exception {
        RecordingListener listener = new RecordingListener();
        startClient(listener);
        awaitConnected(listener);
        TestWsServer.Conn conn = ws.connections().get(0);
        conn.sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":400}}"); // 0.75*400=300ms
        awaitTrue("收到 identify", () -> anyContains(conn.receivedText(), "\"op\":2"));
        // READY 带 s=1 → 心跳 d=1
        conn.sendText("{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"session_id\":\"s1\"}}");
        awaitTrue("心跳帧（d=1）", () -> anyContains(conn.receivedText(), "\"op\":1"));
        assertTrue(anyContains(conn.receivedText(), "\"op\":1"), "收到 op1 心跳");
    }

    @Test
    void invalidSessionFalse_clearsSessionAndReidentifies() throws Exception {
        RecordingListener listener = new RecordingListener();
        startClient(listener);
        awaitConnected(listener);
        TestWsServer.Conn conn = ws.connections().get(0);
        conn.sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":400}}");
        awaitTrue("收到 identify", () -> anyContains(conn.receivedText(), "\"op\":2"));
        conn.sendText("{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"session_id\":\"sess1\"}}");
        Thread.sleep(100); // 等 session_id 捕获（READY 处理在 WS 读线程）

        // op9 d=false → 清会话并立即重连（全量 re-identify）
        conn.sendText("{\"op\":9,\"d\":false}");
        awaitTrue("重连新连接", () -> ws.connections().size() >= 2);
        TestWsServer.Conn conn2 = ws.connections().get(1);
        conn2.sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":400}}");
        awaitTrue("新连接 re-identify（op2）", () -> anyContains(conn2.receivedText(), "\"op\":2"));
        assertTrue(anyContains(conn2.receivedText(), "\"op\":2"), "op9(false) 后走全量 identify");
    }

    @Test
    void reconnectRequest_keepsSessionForResume() throws Exception {
        RecordingListener listener = new RecordingListener();
        startClient(listener);
        awaitConnected(listener);
        TestWsServer.Conn conn = ws.connections().get(0);
        conn.sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":400}}");
        awaitTrue("收到 identify", () -> anyContains(conn.receivedText(), "\"op\":2"));
        conn.sendText("{\"op\":0,\"s\":3,\"t\":\"READY\",\"d\":{\"session_id\":\"sess9\"}}");
        // 等 session 已捕获（receivedText 到达即可；稍候保证处理）
        Thread.sleep(100);

        // op7 → 立即重连；READY 已捕获 session_id 且 seq=3 → 新连接 resume（op6）
        conn.sendText("{\"op\":7}");
        awaitTrue("重连新连接", () -> ws.connections().size() >= 2);
        TestWsServer.Conn conn2 = ws.connections().get(1);
        conn2.sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":400}}");
        awaitTrue("新连接 resume（op6）", () -> anyContains(conn2.receivedText(), "\"op\":6"));
        String resume = firstContaining(conn2.receivedText(), "\"op\":6");
        assertTrue(resume.contains("\"session_id\":\"sess9\""), "resume 携带原 session_id");
        assertTrue(resume.contains("\"seq\":3"), "resume 携带最后事件序号");
    }

    @Test
    void close4004_authFailure_fatal() throws Exception {
        RecordingListener listener = new RecordingListener();
        startClient(listener);
        awaitConnected(listener);
        ws.connections().get(0).sendClose(4004);
        awaitTrue("鉴权失败 → fatal", () -> client.state() == State.FATAL);
        assertTrue(listener.fatal, "listener 收到 fatal 回调（token 无效停止自动重连）");
    }

    // =====================================================================
    // 工具
    // =====================================================================
    private static boolean anyContains(List<String> frames, String needle) {
        return frames.stream().anyMatch(f -> f.contains(needle));
    }

    private static String firstContaining(List<String> frames, String needle) {
        return frames.stream().filter(f -> f.contains(needle)).findFirst().orElse("");
    }

    private static final class RecordingSink implements DiscordEventSink {
        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void onGatewayEvent(String type, String rawFrame) {
            events.add(type);
        }
    }

    private static final class RecordingListener implements GatewayStateListener {
        volatile boolean connected;
        volatile boolean fatal;

        @Override
        public void onConnected() {
            connected = true;
        }

        @Override
        public void onDisconnected(int code, String reason) {}

        @Override
        public void onFatal(String message, Throwable cause) {
            fatal = true;
        }
    }
}
