package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGateway.State;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.TestWsServer;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * QqGatewayClient 单测（本地 WS 服务端 mock QQ 网关）：hello→identify 帧内容 / 心跳周期与 seq /
 * op0 事件透传（READY/RESUMED 内部消费）/ 4004 鉴权关闭→令牌刷新重连 / op7 resume / op9 全量 re-identify。
 */
class QqGatewayClientTest {

    private static final String HELLO = "{\"op\":10,\"d\":{\"heartbeat_interval\":1000000}}";
    private static final int INTENTS = 1 << 25;

    private TestWsServer server;
    private QqGatewayClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.stop();
            } catch (RuntimeException ignored) {
                // 已停止
            }
        }
        if (server != null) {
            server.close();
        }
    }

    private QqGatewayClient startClient(TokenProvider tokens, QqEventSink sink, RecordingListener listener)
            throws Exception {
        server = TestWsServer.start();
        client = new QqGatewayClient(
                silentLogger(),
                new ReconnectPolicy(30, 120, 0, 500, 0),
                tokens,
                token -> QqGatewayUrlFetcher.Result.success("ws://127.0.0.1:" + server.port() + "/"),
                sink,
                listener);
        client.start();
        return client;
    }

    private static void awaitTrue(String what, BooleanSupplier cond) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("超时等待: " + what);
    }

    /** 连接就绪（onWsOpen）后向首条连接下发 hello 并等待 identify 帧。 */
    private TestWsServer.Conn helloThenIdentify(RecordingListener listener) throws Exception {
        awaitTrue("建连", () -> listener.connected.get() >= 1);
        TestWsServer.Conn conn = server.connections().get(0);
        conn.sendText(HELLO);
        awaitTrue("收到 identify", () -> conn.receivedText().stream().anyMatch(f -> f.contains("\"op\":2")));
        return conn;
    }

    private static List<String> framesWith(TestWsServer.Conn conn, String needle) {
        List<String> out = new ArrayList<>();
        conn.receivedText().forEach(f -> {
            if (f.contains(needle)) {
                out.add(f);
            }
        });
        return out;
    }

    // =====================================================================
    // 测试用例
    // =====================================================================

    @Test
    void helloThenIdentify_carriesTokenAndIntents() throws Exception {
        RecordingListener listener = new RecordingListener();
        FakeTokens tokens = new FakeTokens("tok-0");
        client = startClient(tokens, null, listener);

        TestWsServer.Conn conn = helloThenIdentify(listener);

        List<String> identifies = framesWith(conn, "\"op\":2");
        assertEquals(1, identifies.size(), "仅应在 hello 后发一次 identify: " + conn.receivedText());
        String identify = identifies.get(0);
        assertTrue(identify.contains("\"token\":\"QQBot tok-0\""), identify);
        assertTrue(identify.contains("\"intents\":" + INTENTS), identify);
        assertTrue(identify.contains("\"shard\":[0,1]"), identify);
        assertEquals(State.OPEN, client.state());
    }

    @Test
    void heartbeat_sentAtSafetyInterval_withLatestSeq() throws Exception {
        server = TestWsServer.start(true); // 回声 ack 防静默看门狗
        RecordingListener listener = new RecordingListener();
        client = new QqGatewayClient(
                silentLogger(),
                new ReconnectPolicy(30, 120, 0, 500, 0),
                new FakeTokens("tok-0"),
                token -> QqGatewayUrlFetcher.Result.success("ws://127.0.0.1:" + server.port() + "/"),
                null,
                listener);
        client.start();

        awaitTrue("建连", () -> listener.connected.get() >= 1);
        TestWsServer.Conn conn = server.connections().get(0);
        conn.sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":400}}"); // 0.75 → 每 300ms 一次心跳
        awaitTrue("identify", () -> framesWith(conn, "\"op\":2").size() == 1);
        awaitTrue("按周期发送心跳", () -> framesWith(conn, "\"op\":1").size() >= 2);

        // 心跳 d 携带最新事件序号（s 字段推进）
        conn.sendText(
                "{\"op\":0,\"t\":\"READY\",\"s\":1,\"d\":{\"version\":1,\"session_id\":\"sid-h\",\"user\":{},\"shard\":[0,1]}}");
        conn.sendText("{\"op\":0,\"t\":\"GROUP_AT_MESSAGE_CREATE\",\"s\":5,\"d\":{}}");
        awaitTrue("心跳带最新 seq", () -> framesWith(conn, "\"op\":1,\"d\":5").size() >= 1);
    }

    @Test
    void dispatch_forwardsMessageEvents_only() throws Exception {
        RecordingListener listener = new RecordingListener();
        RecordingSink sink = new RecordingSink();
        client = startClient(new FakeTokens("tok-0"), sink, listener);
        TestWsServer.Conn conn = helloThenIdentify(listener);

        String ready =
                "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"version\":1,\"session_id\":\"sid-d\",\"user\":{},\"shard\":[0,1]}}";
        String groupAt =
                "{\"op\":0,\"s\":2,\"t\":\"GROUP_AT_MESSAGE_CREATE\",\"d\":{\"group_openid\":\"G1\",\"content\":\"hi\"}}";
        String c2c = "{\"op\":0,\"s\":3,\"t\":\"C2C_MESSAGE_CREATE\",\"d\":{\"content\":\"dm\"}}";
        conn.sendText(ready);
        conn.sendText(groupAt);
        conn.sendText(c2c);
        conn.sendText("{\"op\":11}"); // 心跳回执：不回调

        awaitTrue("事件回调 2 条", () -> sink.events.size() == 2);
        assertEquals(
                List.of("GROUP_AT_MESSAGE_CREATE", "C2C_MESSAGE_CREATE"),
                sink.events.stream().map(e -> e.type).toList());
        assertTrue(sink.events.get(0).raw.contains("\"group_openid\":\"G1\""), "透传原始帧: " + sink.events.get(0).raw);
        assertTrue(sink.events.stream().noneMatch(e -> e.raw.contains("READY")), "READY 不应透传");
    }

    @Test
    void close4004_refreshesToken_andImmediateReconnectWithFreshToken() throws Exception {
        RecordingListener listener = new RecordingListener();
        FakeTokens tokens = new FakeTokens("tok-0");
        client = startClient(tokens, null, listener);
        TestWsServer.Conn conn0 = helloThenIdentify(listener);
        assertEquals(1, listener.connected.get());

        conn0.sendClose(4004); // QQ 鉴权失败关闭

        awaitTrue("令牌刷新后立即重连成功", () -> listener.connected.get() >= 2);
        assertEquals(1, tokens.authFailures.get(), "鉴权关闭应触发一次令牌重换");
        awaitTrue("重连建连", () -> server.connections().size() >= 2);

        TestWsServer.Conn conn1 = server.connections().get(1);
        conn1.sendText(HELLO);
        awaitTrue(
                "重连 identify 携带新令牌",
                () -> framesWith(conn1, "\"op\":2").stream().anyMatch(f -> f.contains("\"token\":\"QQBot fresh-1\"")));
        assertEquals(State.OPEN, client.state());
        assertEquals(0, listener.fatal.get());
    }

    @Test
    void close4004_whenRefreshFails_retriesLaterWithoutFatal() throws Exception {
        RecordingListener listener = new RecordingListener();
        DeadTokens tokens = new DeadTokens("tok-0");
        client = startClient(tokens, null, listener);
        TestWsServer.Conn conn0 = helloThenIdentify(listener);

        conn0.sendClose(4004);

        // 刷新失败 → RETRY_LATER：退避后重连成功（不 fatal、不风暴）
        awaitTrue("退避后重连成功", () -> listener.connected.get() >= 2);
        assertEquals(1, tokens.authFailures.get());
        assertEquals(0, listener.fatal.get());
        assertEquals(State.OPEN, client.state());
    }

    @Test
    void op7_reconnectResumesSession() throws Exception {
        RecordingListener listener = new RecordingListener();
        RecordingSink sink = new RecordingSink();
        client = startClient(new FakeTokens("tok-0"), sink, listener);
        TestWsServer.Conn conn0 = helloThenIdentify(listener);

        conn0.sendText(
                "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"version\":1,\"session_id\":\"sid-R\",\"user\":{},\"shard\":[0,1]}}");
        conn0.sendText("{\"op\":0,\"s\":2,\"t\":\"GROUP_AT_MESSAGE_CREATE\",\"d\":{}}");
        awaitTrue("事件已透传", () -> sink.events.size() == 1);

        conn0.sendText("{\"op\":7}"); // 会话失效，要求重连

        awaitTrue("重连建连", () -> server.connections().size() >= 2 && listener.connected.get() >= 2);
        TestWsServer.Conn conn1 = server.connections().get(1);
        conn1.sendText(HELLO);
        awaitTrue(
                "重连后走 resume",
                () -> framesWith(conn1, "\"op\":6").stream()
                        .anyMatch(f -> f.contains("\"session_id\":\"sid-R\"")
                                && f.contains("\"seq\":2")
                                && f.contains("\"token\":\"QQBot tok-0\"")));
        assertFalse(
                conn1.receivedText().stream().anyMatch(f -> f.contains("\"op\":2")),
                "resume 成功前不应发 identify: " + conn1.receivedText());
    }

    @Test
    void op9_invalidSession_clearsAndFullyReidentifies() throws Exception {
        RecordingListener listener = new RecordingListener();
        client = startClient(new FakeTokens("tok-0"), null, listener);
        TestWsServer.Conn conn0 = helloThenIdentify(listener);

        conn0.sendText(
                "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"version\":1,\"session_id\":\"sid-X\",\"user\":{},\"shard\":[0,1]}}");
        conn0.sendText("{\"op\":0,\"s\":4,\"t\":\"C2C_MESSAGE_CREATE\",\"d\":{}}");

        conn0.sendText("{\"op\":9}"); // 无效会话：清除后重连

        awaitTrue("重连建连", () -> server.connections().size() >= 2);
        TestWsServer.Conn conn1 = server.connections().get(1);
        conn1.sendText(HELLO);
        awaitTrue(
                "重连后全量 identify（无 resume）", () -> framesWith(conn1, "\"op\":2").size() == 1);
        assertFalse(
                conn1.receivedText().stream().anyMatch(f -> f.contains("\"op\":6")),
                "会话已清除，不应再 resume: " + conn1.receivedText());
    }

    @Test
    void op9_withinDebounceWindow_skipsImmediateReconnect() throws Exception {
        RecordingListener listener = new RecordingListener();
        client = startClient(new FakeTokens("tok-0"), null, listener);
        TestWsServer.Conn conn0 = helloThenIdentify(listener);
        conn0.sendText(
                "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"version\":1,\"session_id\":\"sid-D\",\"user\":{},\"shard\":[0,1]}}");

        conn0.sendText("{\"op\":9}"); // 第一次：清 session + 立即重连
        awaitTrue("首次 op9 触发重连", () -> listener.connected.get() >= 2);
        int connsAfterFirst = server.connections().size();

        TestWsServer.Conn conn1 = server.connections().get(1);
        conn1.sendText(HELLO);
        awaitTrue("重连后 identify", () -> framesWith(conn1, "\"op\":2").size() == 1);
        conn1.sendText("{\"op\":9}"); // 15s 防抖窗口内的第二次：不应立即重连
        Thread.sleep(700);
        assertEquals(connsAfterFirst, server.connections().size(), "防抖窗口内 op9 不应触发立即重连");
    }

    @Test
    void gatewayUrl_cachedWithinWindow_reducesFetches() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        AtomicInteger fetches = new AtomicInteger();
        client = new QqGatewayClient(
                silentLogger(),
                new ReconnectPolicy(30, 120, 0, 500, 0),
                new FakeTokens("tok-0"),
                token -> {
                    fetches.incrementAndGet();
                    return QqGatewayUrlFetcher.Result.success("ws://127.0.0.1:" + server.port() + "/");
                },
                null,
                listener);
        client.start();
        awaitTrue("首次建连", () -> listener.connected.get() >= 1);
        assertEquals(1, fetches.get());

        server.connections().get(0).closeSocket(); // 断线重连：缓存窗口内不应重复请求 /gateway/bot
        awaitTrue("自动重连", () -> listener.connected.get() >= 2);
        assertEquals(1, fetches.get(), "60s 缓存窗口内重连复用网关 URL");
    }

    // =====================================================================
    // 测试替身
    // =====================================================================

    /** 可换发令牌：onAuthFailure 返回新令牌并计数。 */
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

    /** 换发永远失败（返回 null）：映射 RETRY_LATER 防风暴。 */
    static final class DeadTokens implements TokenProvider {
        final AtomicInteger authFailures = new AtomicInteger();
        final String token;

        DeadTokens(String token) {
            this.token = token;
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
            authFailures.incrementAndGet();
            return null;
        }
    }

    static final class RecordingSink implements QqEventSink {
        final List<Event> events = new CopyOnWriteArrayList<>();

        record Event(String type, String raw) {}

        @Override
        public void onGatewayEvent(String type, String rawFrame) {
            events.add(new Event(type, rawFrame));
        }
    }

    static final class RecordingListener implements GatewayStateListener {
        final AtomicInteger connected = new AtomicInteger();
        final AtomicInteger fatal = new AtomicInteger();

        @Override
        public void onConnected() {
            connected.incrementAndGet();
        }

        @Override
        public void onFatal(String message, Throwable cause) {
            fatal.incrementAndGet();
        }
    }

    private static ServerLogger silentLogger() {
        Logger raw = Logger.getLogger("qq-gateway-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return () -> raw;
    }
}
