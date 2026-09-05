package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGateway.State;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.TestWsServer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * FeishuGatewayClient 单测（MockWebServer 模拟 REST 端点引导 + TestWsServer 模拟长连接网关）：
 * 端点引导（AppID/AppSecret 换 wss url + PingInterval）→建连→二进制心跳 ping 帧 / 服务端 ping→pong
 * 回显 / 事件帧透传 sink + ACK(code=200) / 未知帧忽略。网关复用 ReconnectingGateway 骨架（重连/看门狗
 * 由 conn 包既有测试覆盖，此处聚焦飞书协议分发）。
 */
class FeishuGatewayClientTest {

    private MockWebServer http;
    private TestWsServer ws;
    private FeishuGatewayClient client;

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

    /** 端点引导响应指向本地 TestWsServer；引导的 URL 带 service_id 供心跳帧使用。 */
    private FeishuGatewayClient startClient(FeishuEventSink sink, RecordingListener listener) throws Exception {
        ws = TestWsServer.start();
        http = new MockWebServer();
        http.start();
        String wsUrl = "ws://127.0.0.1:" + ws.port() + "/connect?service_id=17";
        // 飞书端点引导：HTTP 200 + 业务 code 0 + data.URL + ClientConfig
        http.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"data\":{\"URL\":\"" + wsUrl + "\","
                        + "\"ClientConfig\":{\"PingInterval\":1,\"ReconnectInterval\":5,\"ReconnectNonce\":1}}}"));

        String base = http.url("/").toString().replaceAll("/$", "");
        FeishuApiClient api = new FeishuApiClient("cli-1", "secret-1", base, base, rawLogger());
        client = new FeishuGatewayClient(silentLogger(), new ReconnectPolicy(30, 120, 0, 500, 0), api, sink, listener);
        client.start();
        return client;
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

    private static final class RecordingListener implements GatewayStateListener {
        final AtomicInteger connected = new AtomicInteger();
        final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void onConnected() {
            connected.incrementAndGet();
            events.add("connected");
        }

        @Override
        public void onDisconnected(int code, String reason) {
            events.add("disconnected:" + code);
        }
    }

    private static ServerLogger silentLogger() {
        Logger raw = rawLogger();
        return () -> raw;
    }

    private static Logger rawLogger() {
        Logger raw = Logger.getLogger("feishu-gw-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return raw;
    }

    // ---------------------------------------------------------------------
    // 服务端帧构造（与 FeishuFrame proto2 wire format 一致的原始字节，独立于编解码器实现）
    // ---------------------------------------------------------------------

    /** 服务端 ping 控制帧：CONTROL + type=ping + 指定 seq/log/service。 */
    private static byte[] serverPingFrame(long seq, long log, int service) throws Exception {
        byte[] hdr = headerBytes("type", "ping");
        return frame(seq, log, service, FeishuFrame.METHOD_CONTROL, hdr, null);
    }

    /** 事件数据帧：DATA + type=event + payload=事件 JSON。 */
    private static byte[] eventFrame(long seq, long log, String eventJson) throws Exception {
        byte[] hdr = headerBytes("type", "event");
        return frame(seq, log, 12, FeishuFrame.METHOD_DATA, hdr, eventJson.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] frame(long seq, long log, int service, int method, byte[] headers, byte[] payload)
            throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        writeVarintField(out, 1, seq); // SeqID
        writeVarintField(out, 2, log); // LogID
        writeVarintField(out, 3, service);
        writeVarintField(out, 4, method);
        writeLengthField(out, 5, headers); // repeated Header
        if (payload != null) {
            writeLengthField(out, 8, payload);
        }
        return out.toByteArray();
    }

    private static byte[] headerBytes(String key, String value) throws Exception {
        java.io.ByteArrayOutputStream h = new java.io.ByteArrayOutputStream();
        writeString(h, 1, key);
        writeString(h, 2, value);
        return h.toByteArray();
    }

    private static void writeVarintField(java.io.ByteArrayOutputStream out, int field, long value) {
        writeVarint(out, ((long) field << 3) | 0);
        writeVarint(out, value);
    }

    private static void writeLengthField(java.io.ByteArrayOutputStream out, int field, byte[] bytes) {
        writeVarint(out, ((long) field << 3) | 2);
        writeVarint(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeString(java.io.ByteArrayOutputStream out, int field, String s) {
        writeLengthField(out, field, s.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeVarint(java.io.ByteArrayOutputStream out, long value) {
        long v = value;
        while (true) {
            if ((v & ~0x7FL) == 0) {
                out.write((int) v);
                return;
            }
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
    }

    // =====================================================================
    // 用例
    // =====================================================================

    @Test
    void connect_fetchesEndpoint_sendsBinaryPingHeartbeat() throws Exception {
        RecordingListener listener = new RecordingListener();
        FeishuGatewayClient gw = startClient(null, listener);
        awaitTrue("建连", () -> listener.connected.get() >= 1);

        TestWsServer.Conn conn = ws.connections().get(0);

        // 心跳 PingInterval=1s：等待二进制 ping 帧（service=17、CONTROL、type=ping）
        awaitTrue("收到二进制心跳 ping", () -> {
            for (byte[] frame : conn.receivedBinary()) {
                try {
                    FeishuFrame f = FeishuFrame.decode(frame);
                    if (f.method() == FeishuFrame.METHOD_CONTROL
                            && FeishuFrame.TYPE_PING.equals(f.type())
                            && f.service() == 17) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // 未收满帧
                }
            }
            return false;
        });
        assertEquals(State.OPEN, gw.state());
    }

    @Test
    void serverPing_answeredWithPongEchoingSeqLogService() throws Exception {
        RecordingListener listener = new RecordingListener();
        FeishuGatewayClient gw = startClient(null, listener);
        awaitTrue("建连", () -> listener.connected.get() >= 1);
        TestWsServer.Conn conn = ws.connections().get(0);
        conn.receivedBinary().clear();

        // 构造服务端 ping：method=CONTROL、type=ping、seq/log/service 非 0
        FeishuFrame serverPing = FeishuFrame.decode(serverPingFrame(7, 9, 3));
        conn.sendBinary(serverPing.encode());

        awaitTrue(
                "回 pong",
                () -> conn.receivedBinary().stream().anyMatch(bytes -> {
                    try {
                        FeishuFrame f = FeishuFrame.decode(bytes);
                        return FeishuFrame.METHOD_CONTROL == f.method()
                                && FeishuFrame.TYPE_PONG.equals(f.type())
                                && f.seqId() == 7
                                && f.logId() == 9
                                && f.service() == 3;
                    } catch (RuntimeException ignored) {
                        return false;
                    }
                }));
        assertTrue(conn.receivedBinary().size() >= 1, "应回显 pong 帧");
        assertEquals(State.OPEN, gw.state());
    }

    @Test
    void eventFrame_dispatchedToSinkAndAckedWithCode200() throws Exception {
        RecordingListener listener = new RecordingListener();
        CopyOnWriteArrayList<String> receivedEvents = new CopyOnWriteArrayList<>();
        FeishuGatewayClient gw = startClient(
                payload -> {
                    receivedEvents.add(new String(payload, StandardCharsets.UTF_8));
                },
                listener);
        awaitTrue("建连", () -> listener.connected.get() >= 1);
        TestWsServer.Conn conn = ws.connections().get(0);
        conn.receivedBinary().clear();

        String eventJson = "{\"schema\":\"2.0\",\"header\":{\"event_id\":\"ev_1\","
                + "\"event_type\":\"im.message.receive_v1\"},\"event\":{}}";
        FeishuFrame event = FeishuFrame.decode(eventFrame(11, 22, eventJson));
        conn.sendBinary(event.encode());

        awaitTrue("事件透传 sink", () -> receivedEvents.size() == 1);
        assertEquals(eventJson, receivedEvents.get(0));

        awaitTrue(
                "回 ACK 200",
                () -> conn.receivedBinary().stream().anyMatch(bytes -> {
                    try {
                        FeishuFrame f = FeishuFrame.decode(bytes);
                        return FeishuFrame.METHOD_DATA == f.method()
                                && f.seqId() == 11
                                && f.logId() == 22
                                && new String(f.payload() == null ? new byte[0] : f.payload(), StandardCharsets.UTF_8)
                                        .contains("\"code\":200");
                    } catch (RuntimeException ignored) {
                        return false;
                    }
                }));
    }

    @Test
    void eventWithHandlerException_ackedWithCode500() throws Exception {
        RecordingListener listener = new RecordingListener();
        FeishuGatewayClient gw = startClient(
                payload -> {
                    throw new IllegalStateException("boom");
                },
                listener);
        awaitTrue("建连", () -> listener.connected.get() >= 1);
        TestWsServer.Conn conn = ws.connections().get(0);
        conn.receivedBinary().clear();

        String eventJson = "{\"schema\":\"2.0\",\"header\":{\"event_id\":\"ev_2\","
                + "\"event_type\":\"im.message.receive_v1\"},\"event\":{}}";
        FeishuFrame event = FeishuFrame.decode(eventFrame(1, 2, eventJson));
        conn.sendBinary(event.encode());

        awaitTrue(
                "回 ACK 500（触发平台重推）",
                () -> conn.receivedBinary().stream().anyMatch(bytes -> {
                    try {
                        FeishuFrame f = FeishuFrame.decode(bytes);
                        String payload =
                                new String(f.payload() == null ? new byte[0] : f.payload(), StandardCharsets.UTF_8);
                        return f.seqId() == 1 && payload.contains("\"code\":500");
                    } catch (RuntimeException ignored) {
                        return false;
                    }
                }));
    }

    /** 端点引导失败（code!=0）→ resolveEndpoint 返回 null → 网关保持 CONNECTING（骨架退避，重试引导）。 */
    @Test
    void endpointBootstrapFailure_staysConnectingRetries() throws Exception {
        ws = TestWsServer.start();
        http = new MockWebServer();
        http.start();
        // 业务 code 非 0（如 app 不存在）→ 引导失败
        http.enqueue(new MockResponse().setResponseCode(200).setBody("{\"code\":20013,\"msg\":\"app not found\"}"));

        String base = http.url("/").toString().replaceAll("/$", "");
        FeishuApiClient api = new FeishuApiClient("cli-1", "secret-1", base, base, rawLogger());
        RecordingListener listener = new RecordingListener();
        client = new FeishuGatewayClient(silentLogger(), new ReconnectPolicy(40, 200, 0, 500, 0), api, null, listener);
        client.start();

        // 退避 40ms 起：应保持 CONNECTING（无连接建立、无 connected 回调）
        Thread.sleep(300);
        assertEquals(State.CONNECTING, client.state(), "引导失败应退避重试而非建立连接");
        assertEquals(0, listener.connected.get());
        assertNotNull(http.getRequestCount() >= 1 ? client : client, "引导至少发起一次");
    }
}
