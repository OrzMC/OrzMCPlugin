package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGateway.State;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.TokenRefresher.RefreshOutcome;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ReconnectingGateway 生命周期单测：本地 WS 服务端（TestWsServer）先 accept 再断开，覆盖
 * 建连/断线重连/重试上限 fatal/鉴权刷新重连/心跳发送与静默看门狗/stop 清理。
 */
class ReconnectingGatewayTest {

    private TestWsServer server;
    private TestGateway gateway;

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            try {
                gateway.stop();
            } catch (RuntimeException ignored) {
                // 已停止
            }
        }
        if (server != null) {
            server.close();
        }
    }

    private TestGateway newGateway(ReconnectPolicy policy, TokenRefresher refresher, RecordingListener listener) {
        AtomicReference<TestWsServer> ref = new AtomicReference<>(server);
        gateway = new TestGateway(
                "qq",
                policy,
                refresher,
                listener,
                () -> "ws://127.0.0.1:" + ref.get().port() + "/");
        return gateway;
    }

    private static ReconnectPolicy fastPolicy() {
        return new ReconnectPolicy(30, 120, 0, 500, 0);
    }

    private static final long AWAIT_MS = 5000;

    private static void awaitTrue(String what, BooleanSupplier cond) throws Exception {
        long deadline = System.currentTimeMillis() + AWAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("超时等待: " + what);
    }

    // =====================================================================
    // 测试用例
    // =====================================================================

    @Test
    void connect_opensAndSendsIdentifyAndReceivesPayload() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        TestGateway gw = newGateway(fastPolicy(), null, listener);

        gw.start();
        gw.start(); // 幂等：再次 start 不应重复建连

        awaitTrue("首次建连", () -> gw.opens.get() == 1 && !server.connections().isEmpty());
        assertEquals(State.OPEN, gw.state());
        assertEquals(1, listener.connected.get());
        TestWsServer.Conn conn = server.connections().get(0);
        awaitTrue("服务端收到 identify 帧", () -> conn.receivedText().contains("identify:t0"));

        conn.sendText("{\"op\":10}");
        awaitTrue("收到服务端帧", () -> gw.payloads.contains("{\"op\":10}"));
    }

    @Test
    void serverDrop_triggersAutoReconnectWithNewConnection() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        TestGateway gw = newGateway(fastPolicy(), null, listener);

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);

        server.connections().get(0).closeSocket(); // 模拟网络断开（1006）

        awaitTrue("自动重连建立第二条连接", () -> server.connections().size() >= 2);
        assertEquals(1, listener.disconnected.get());
        awaitTrue(
                "首条连接发过 identify",
                () -> server.connections().get(0).receivedText().contains("identify:t0"));
        awaitTrue(
                "重连后再次 identify",
                () -> server.connections().get(1).receivedText().contains("identify:t0"));

        server.connections().get(1).sendText("after-reconnect");
        awaitTrue("重连后的连接可收发", () -> gw.payloads.contains("after-reconnect"));
    }

    @Test
    void refusedConnection_retriesUntilCap_thenFatal() throws Exception {
        // 占用后立即释放的端口 → 建连必然被拒
        int deadPort;
        try (ServerSocket tmp = new ServerSocket(0)) {
            deadPort = tmp.getLocalPort();
        }
        ReconnectPolicy capped = new ReconnectPolicy(20, 80, 0, 0, 3);
        RecordingListener listener = new RecordingListener();
        gateway = new TestGateway("qq", capped, null, listener, () -> "ws://127.0.0.1:" + deadPort + "/");

        gateway.start();

        awaitTrue("连续失败达上限后 fatal", () -> listener.fatal.get() == 1);
        assertEquals(State.FATAL, gateway.state());
        assertEquals(0, gateway.opens.get(), "从未成功建连");
        assertTrue(gateway.lastProblem().contains("上限"), "fatal 原因应含上限说明: " + gateway.lastProblem());
        Thread.sleep(300);
        assertEquals(1, listener.fatal.get(), "fatal 后不应继续重试");
        assertEquals(State.FATAL, gateway.state());
    }

    @Test
    void authClose_refreshesTokenAndReconnectsImmediately() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        AtomicInteger refreshCalls = new AtomicInteger();
        gateway = newGateway(
                fastPolicy(),
                () -> {
                    refreshCalls.incrementAndGet();
                    gateway.token.set("t1");
                    return RefreshOutcome.REFRESHED;
                },
                listener);
        gateway.authCloseCode = 4004;

        gateway.start();
        awaitTrue("首次建连", () -> gateway.opens.get() == 1);

        server.connections().get(0).sendClose(4004); // 服务端鉴权失败关闭

        awaitTrue(
                "鉴权刷新后立即重连",
                () -> gateway.opens.get() == 2 && server.connections().size() >= 2);
        assertEquals(1, refreshCalls.get());
        awaitTrue("重连携带刷新后的令牌", () -> server.connections().get(1).receivedText().contains("identify:t1"));
        assertEquals(State.OPEN, gateway.state());
    }

    @Test
    void authClose_whenRefreshDead_fatalAndStops() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        AtomicInteger refreshCalls = new AtomicInteger();
        gateway = newGateway(
                fastPolicy(),
                () -> {
                    refreshCalls.incrementAndGet();
                    return RefreshOutcome.DEAD;
                },
                listener);
        gateway.authCloseCode = 4004;

        gateway.start();
        awaitTrue("首次建连", () -> gateway.opens.get() == 1);

        server.connections().get(0).sendClose(4004);

        awaitTrue("刷新 DEAD → fatal", () -> listener.fatal.get() == 1);
        assertEquals(State.FATAL, gateway.state());
        Thread.sleep(300);
        assertEquals(1, gateway.opens.get(), "fatal 后不应再重连");
    }

    @Test
    void heartbeat_sentAtConfiguredInterval_noFalseReconnectWhenAcked() throws Exception {
        server = TestWsServer.start(true); // 回声：收到任何帧回 "ack"，保持活跃
        RecordingListener listener = new RecordingListener();
        AtomicInteger seq = new AtomicInteger();
        TestGateway gw = newGateway(fastPolicy(), null, listener);
        gw.heartbeatIntervalMs = 50L;
        gw.heartbeatPayload = () -> "hb:" + seq.incrementAndGet();

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);
        awaitTrue("按周期发送心跳", () -> {
            long hb = server.connections().get(0).receivedText().stream()
                    .filter(f -> f.startsWith("hb:"))
                    .count();
            return hb >= 3;
        });

        Thread.sleep(400); // 活跃连接不应被静默看门狗误杀
        assertEquals(1, gw.opens.get(), "心跳有回执时不应强制重连");
        assertTrue(gw.payloads.contains("ack"), "应收到服务端心跳回执: " + gw.payloads);
    }

    @Test
    void heartbeatSilence_forcesCloseAndReconnect() throws Exception {
        server = TestWsServer.start(); // 不回执 → 入站静默
        RecordingListener listener = new RecordingListener();
        AtomicInteger seq = new AtomicInteger();
        TestGateway gw = newGateway(fastPolicy(), null, listener);
        gw.heartbeatIntervalMs = 50L;
        gw.heartbeatPayload = () -> "hb:" + seq.incrementAndGet();

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);
        awaitTrue("静默超时触发强制重连", () -> gw.opens.get() >= 2);
        assertTrue(listener.disconnected.get() >= 1, "强制断开应上报 disconnected");
    }

    @Test
    void stop_isIdempotent_andPreventsFurtherReconnect() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        TestGateway gw = newGateway(fastPolicy(), null, listener);

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);

        gw.stop();
        gw.stop(); // 幂等
        assertEquals(State.STOPPED, gw.state());

        TestWsServer.Conn conn = server.connections().get(0);
        conn.closeSocket();
        Thread.sleep(300);
        assertEquals(1, gw.opens.get(), "stop 后不应再重连");

        assertThrows(IllegalStateException.class, gw::start, "已 stop 实例不可重启");
    }

    @Test
    void resolveEndpointNull_thenRecoversOnBackoff() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        AtomicInteger resolveCalls = new AtomicInteger();
        gateway = new TestGateway("qq", fastPolicy(), null, listener, () -> {
            // 第一次取地址失败（如网关 URL API 暂不可用），退避后恢复
            if (resolveCalls.getAndIncrement() == 0) {
                return null;
            }
            return "ws://127.0.0.1:" + server.port() + "/";
        });

        gateway.start();

        awaitTrue("resolve 失败退避后恢复建连", () -> gateway.opens.get() == 1);
        assertEquals(State.OPEN, gateway.state());
        assertTrue(resolveCalls.get() >= 2);
    }

    @Test
    void policy_validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new ReconnectPolicy(0, 100, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReconnectPolicy(100, 50, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReconnectPolicy(100, 100, 150, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReconnectPolicy(100, 100, 0, 0, -1));
    }

    // =====================================================================
    // 测试替身
    // =====================================================================

    static final class TestGateway extends ReconnectingGateway {
        final Supplier<String> endpoint;
        final AtomicReference<String> token = new AtomicReference<>("t0");
        final List<String> payloads = new CopyOnWriteArrayList<>();
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger fatalCount = new AtomicInteger();
        volatile int authCloseCode = -999; // 该关闭码触发 onAuthFailure（模拟 QQ 鉴权关闭）；Java-WebSocket 异常关闭会用 -1，勿用 -1 作哨兵
        volatile Long heartbeatIntervalMs;
        volatile Supplier<String> heartbeatPayload;

        TestGateway(
                String name,
                ReconnectPolicy policy,
                TokenRefresher refresher,
                GatewayStateListener listener,
                Supplier<String> endpoint) {
            super(name, silentLogger(), policy, refresher, listener);
            this.endpoint = endpoint;
        }

        @Override
        protected String resolveEndpoint() {
            return endpoint.get();
        }

        @Override
        protected void onGatewayOpen() {
            opens.incrementAndGet();
            send("identify:" + token.get());
            if (heartbeatIntervalMs != null && heartbeatPayload != null) {
                configureHeartbeat(heartbeatIntervalMs, heartbeatPayload);
            }
        }

        @Override
        protected void onGatewayPayload(String payload) {
            payloads.add(payload);
        }

        @Override
        protected void onGatewayClosed(int code, String reason, boolean remote) {
            closes.incrementAndGet();
            if (code == authCloseCode) {
                onAuthFailure();
            }
        }

        @Override
        protected void onGatewayFatal(String message, Throwable cause) {
            fatalCount.incrementAndGet();
        }
    }

    static final class RecordingListener implements GatewayStateListener {
        final AtomicInteger connected = new AtomicInteger();
        final AtomicInteger disconnected = new AtomicInteger();
        final AtomicInteger fatal = new AtomicInteger();
        final AtomicBoolean fatalMessage = new AtomicBoolean();

        @Override
        public void onConnected() {
            connected.incrementAndGet();
        }

        @Override
        public void onDisconnected(int code, String reason) {
            disconnected.incrementAndGet();
        }

        @Override
        public void onFatal(String message, Throwable cause) {
            fatal.incrementAndGet();
            fatalMessage.set(message != null && message.contains("上限"));
        }
    }

    private static ServerLogger silentLogger() {
        Logger raw = Logger.getLogger("reconnecting-gateway-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return () -> raw;
    }
}
