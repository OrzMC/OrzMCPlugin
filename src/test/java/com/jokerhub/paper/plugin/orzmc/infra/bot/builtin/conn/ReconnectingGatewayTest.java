package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void start_doesNotBlockCallerWhileResolvingEndpoint() throws Exception {
        // 回归（R12）：start() 常由服务器线程调用（/bot、/orzmc config reload），而 resolveEndpoint 可能做
        // 阻塞 HTTP（如 QQ /gateway/bot）——首次建连必须异步，不得让调用线程等网络。
        server = TestWsServer.start();
        AtomicReference<TestWsServer> ref = new AtomicReference<>(server);
        java.util.concurrent.CountDownLatch resolving = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        TestGateway gw = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> {
            resolving.countDown();
            try {
                release.await(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ws://127.0.0.1:" + ref.get().port() + "/";
        });
        gateway = gw;

        long t0 = System.nanoTime();
        gw.start();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 500, "start() 不得在调用线程等 resolveEndpoint（实测 " + elapsedMs + "ms）");
        assertTrue(resolving.await(2, java.util.concurrent.TimeUnit.SECONDS), "建连应已开始在后台调度线程执行");

        release.countDown();
        awaitTrue("释放后在后台完成建连", () -> gw.opens.get() == 1);
        assertEquals(State.OPEN, gw.state());
    }

    @Test
    void stop_doesNotBlockCaller() throws Exception {
        // 回归：stop() 不得 awaitTermination 阻塞调用线程（/orzmc config reload 逐平台停旧建新会叠加）。
        server = TestWsServer.start();
        TestGateway gw = newGateway(fastPolicy(), null, new RecordingListener());
        gw.start();
        awaitTrue("建连", () -> gw.opens.get() == 1);

        long t0 = System.nanoTime();
        gw.stop();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 500, "stop() 不得阻塞调用线程（实测 " + elapsedMs + "ms）");
        assertEquals(State.STOPPED, gw.state());
    }

    @Test
    void serverDrop_triggersAutoReconnectWithNewConnection() throws Exception {
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        TestGateway gw = newGateway(fastPolicy(), null, listener);

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);
        // 等 identify 落地后再断线：否则「断线比首帧 flush 更快」时首条连接可能没记录到 identify，
        // 断言必然超时（2026-11 全量测试并行/负载高时复现过一次假失败）
        awaitTrue(
                "首条连接发过 identify",
                () -> server.connections().get(0).receivedText().contains("identify:t0"));

        server.connections().get(0).closeSocket(); // 模拟网络断开（1006）

        awaitTrue("自动重连建立第二条连接", () -> server.connections().size() >= 2);
        assertEquals(1, listener.disconnected.get());
        awaitTrue(
                "首条连接发过 identify（断线后快照仍可读）",
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

    // =====================================================================
    // issue #616：握手黑洞不得把网关挂死（connect timeout）
    // =====================================================================

    @Test
    void connectTimeout_stalledHandshake_retriesInsteadOfHangingForever() throws Exception {
        // 黑洞服务端：TCP 能 accept，但永远不回握手响应（模拟 TLS/握手被中间设备丢包的环境）。
        // Java-WebSocket 的 2 参构造 connect timeout = 0 = 无限等待 → 旧实现会永久卡在建连调度线程上
        // （2026-11-22 本机实测：4 分钟无任何日志、state 停在 CONNECTING）。
        try (ServerSocket blackhole = new ServerSocket(0)) {
            Thread acceptor = new Thread(
                    () -> {
                        while (!blackhole.isClosed()) {
                            try {
                                blackhole.accept();
                            } catch (java.io.IOException ignored) {
                                return;
                            }
                        }
                    },
                    "test-blackhole-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();

            AtomicInteger attempts = new AtomicInteger();
            RecordingListener listener = new RecordingListener();
            gateway = new TestGateway("qq", new ReconnectPolicy(20, 60000, 0, 0, 3), null, listener, () -> {
                attempts.incrementAndGet();
                return "ws://127.0.0.1:" + blackhole.getLocalPort() + "/";
            });
            gateway.connectTimeoutOverrideMs = 250;

            gateway.start();
            awaitTrue("握手停滞后按超时重试（而不是悬挂）", () -> attempts.get() >= 2);
            assertEquals(0, gateway.opens.get(), "从未真正建连成功");
        }
    }

    @Test
    void connectTimeout_defaultsTo15s() throws Exception {
        TestGateway gw = newGateway(fastPolicy(), null, new RecordingListener());
        assertEquals(
                ReconnectingGateway.DEFAULT_CONNECT_TIMEOUT_MS,
                gw.connectTimeoutMs(),
                "默认建连超时必须 > 0（0 在 Java-WebSocket 里 = 无限等待，会把网关挂死）");
        assertTrue(gw.connectTimeoutMs() > 0, "绝不允许 0（= 无限等待）");
    }

    @Test
    void connectWatchdog_doesNotFireAfterSuccessfulOpen() throws Exception {
        // 看门狗只在“仍未 OPEN”时判失败：正常建连后即使超时任务到期也不得打重连
        server = TestWsServer.start();
        RecordingListener listener = new RecordingListener();
        TestGateway gw = newGateway(fastPolicy(), null, listener);
        gw.connectTimeoutOverrideMs = 200;

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);
        Thread.sleep(600); // 跨过看门狗期限
        assertEquals(1, gw.opens.get(), "已建连成功时看门狗不得触发重连");
        assertEquals(0, listener.disconnected.get(), "不应产生断开");
    }

    @Test
    void heartbeat_sentAtConfiguredInterval_noFalseReconnectWhenAcked() throws Exception {
        server = TestWsServer.start(true); // 回声：收到任何帧回 "ack"，保持活跃
        RecordingListener listener = new RecordingListener();
        AtomicInteger seq = new AtomicInteger();
        TestGateway gw = newGateway(fastPolicy(), null, listener);
        // 静默看门狗窗口 = interval × SILENT_CYCLES(3)。原用 50ms（窗口仅 150ms）：CI/本机负载抖动
        // 一次 >150ms 的调度停顿就会被误判「心跳静默」并强制重连（2026-11 实观测到 opens=2 假失败）。
        // 200ms → 600ms 窗口，给调度抖动留 3 倍余量。
        gw.heartbeatIntervalMs = 200L;
        gw.heartbeatPayload = () -> "hb:" + seq.incrementAndGet();

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);
        awaitTrue("按周期发送心跳", () -> heartbeatFrameCount() >= 3);

        // 跨过 3 个心跳周期持续观察：回执正常时不应出现任何重连（轮询优于单次 sleep 后断言）
        long observeUntil = System.currentTimeMillis() + 3 * gw.heartbeatIntervalMs;
        while (System.currentTimeMillis() < observeUntil) {
            assertEquals(
                    1,
                    gw.opens.get(),
                    () -> "心跳有回执时不应强制重连（closes=" + gw.closes.get() + ", payloads=" + gw.payloads + "）");
            Thread.sleep(25);
        }
        assertTrue(gw.payloads.contains("ack"), "应收到服务端心跳回执: " + gw.payloads);
    }

    private long heartbeatFrameCount() {
        return server.connections().get(0).receivedText().stream()
                .filter(f -> f.startsWith("hb:"))
                .count();
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

    // =====================================================================
    // issue #610：空闲期误判静默（判活口径 / 窗口可配）
    // =====================================================================

    @Test
    void wsPing_countsAsInboundLiveness_idleConnectionSurvives() throws Exception {
        // 回归（#610）：空闲期平台侧若只发 WS 控制帧 ping，旧实现只统计文本/二进制帧 → 误判「静默死亡」。
        server = TestWsServer.start(); // 不回显文本：唯一入站活性就是控制帧
        RecordingListener listener = new RecordingListener();
        TestGateway gw = newGateway(fastPolicy(), null, listener);
        gw.heartbeatIntervalMs = 100L; // 默认窗口 = 3 × 100 = 300ms
        gw.heartbeatPayload = () -> "hb";

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);
        TestWsServer.Conn conn = server.connections().get(0);

        // 观察 ~4 个默认窗口：每 50ms 发一个控制帧 ping，全程无文本/二进制入站
        long observeUntil = System.currentTimeMillis() + 1200;
        while (System.currentTimeMillis() < observeUntil) {
            conn.sendPing();
            assertEquals(1, gw.opens.get(), () -> "WS ping 应计为入站活性，不得触发静默重连（closes=" + gw.closes.get() + "）");
            Thread.sleep(50);
        }
        assertTrue(gw.inboundFrameCount() > 0, "控制帧应计入入站活性计数");
    }

    @Test
    void silenceTimeoutOverride_extendsIdleTolerance() throws Exception {
        // 回归（#610）：静默窗口可被 im.yml 覆盖（heartbeat_timeout_seconds）——回帧慢/链路会回收长连接的环境
        // 可放大窗口，不得再被默认 3 周期误杀。
        server = TestWsServer.start(); // 不回执
        TestGateway gw = newGateway(fastPolicy(), null, new RecordingListener());
        gw.heartbeatIntervalMs = 50L; // 默认窗口 = 150ms
        gw.heartbeatPayload = () -> "hb";
        gw.setSilenceTimeoutMs(3000); // 显式放大到 3000ms

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);

        // 观察 900ms（= 默认窗口的 6 倍）：覆盖生效时应零重连
        long observeUntil = System.currentTimeMillis() + 900;
        while (System.currentTimeMillis() < observeUntil) {
            assertEquals(1, gw.opens.get(), "窗口覆盖为 3000ms 时 900ms 内不应重连（closes=" + gw.closes.get() + "）");
            Thread.sleep(25);
        }
    }

    @Test
    void silenceWindow_defaultsToThreeCycles_andHonoursOverride() throws Exception {
        TestGateway gw = newGateway(fastPolicy(), null, new RecordingListener());
        assertEquals(0L, gw.silenceWindowMs(), "未配置心跳时看门狗窗口为 0（不生效）");

        gw.configureHeartbeatForTest(1000L);
        assertEquals(3000L, gw.silenceWindowMs(), "默认窗口 = 3 × 心跳周期");

        gw.setSilenceTimeoutMs(7000L);
        assertEquals(7000L, gw.silenceWindowMs(), "显式覆盖优先于周期推导");

        gw.setSilenceTimeoutMs(-1L);
        assertEquals(3000L, gw.silenceWindowMs(), "非正值应回落默认窗口（防配置写错直接关掉看门狗逻辑）");
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
    // issue #616：入站断流诊断（远端地址 / 计数快照 / 同址零入站升级）
    // =====================================================================

    @Test
    void diagnostics_exposesRemoteAddressAndCounters() throws Exception {
        server = TestWsServer.start();
        TestGateway gw = newGateway(fastPolicy(), null, new RecordingListener());

        gw.start();
        awaitTrue("首次建连", () -> gw.opens.get() == 1);
        server.connections().get(0).sendText("{\"op\":10}");
        awaitTrue("收到服务端帧", () -> gw.payloads.contains("{\"op\":10}"));

        GatewayDiagnostics diag = gw.diagnostics();
        assertEquals("OPEN", diag.state());
        assertEquals("127.0.0.1:" + server.port(), diag.remoteAddress(), "诊断必须给出对端地址：同址反复零入站才指向出网路径");
        assertTrue(diag.inboundFrames() >= 1, "入站帧数应计入文本帧: " + diag);
        assertTrue(diag.silentMs() >= 0, "静默时长不应为负: " + diag);
        assertEquals(0, diag.sameAddressSilenceStreak(), "健康连接不应有同址零入站计数: " + diag);
        assertEquals(0, diag.silenceLosses(), "健康连接不应有静默重连: " + diag);
        assertEquals(0, diag.reconnectAttempts(), "健康连接不应有重连尝试: " + diag);
        assertTrue(diag.heartbeatSends() >= 0, "心跳计数应为非负: " + diag);
        assertNull(diag.lastProblem(), "健康连接不应有问题描述: " + diag);
    }

    @Test
    void silenceOnSameAddress_escalatesStreakAndLosses() throws Exception {
        server = TestWsServer.start(); // 只 accept 不回帧 → 每轮都「连上但零入站」
        AtomicInteger seq = new AtomicInteger();
        TestGateway gw = newGateway(fastPolicy(), null, new RecordingListener());
        gw.heartbeatIntervalMs = 50L;
        gw.heartbeatPayload = () -> "hb:" + seq.incrementAndGet();

        gw.start();
        // 跑到第 3 次：同时覆盖「同址零入站」提示分支（SAME_ADDRESS_SILENCE_HINT）与退避抬高
        awaitTrue("同一地址连续零入站静默被累计到提示阈值", () -> gw.diagnostics().sameAddressSilenceStreak() >= 3);

        GatewayDiagnostics diag = gw.diagnostics();
        assertTrue(diag.silenceLosses() >= 2, () -> "静默重连次数应累计（退避据此抬高）: " + diag);
        assertTrue(diag.reconnectAttempts() >= 1, () -> "应记录连续重连尝试次数: " + diag);
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
        /** 测试可覆盖建连超时（黑洞握手测试用，避免等真实的 15s）；-1 = 不覆盖，0 = 显式关掉看门狗。 */
        volatile int connectTimeoutOverrideMs = -1;

        @Override
        protected int connectTimeoutMs() {
            return connectTimeoutOverrideMs >= 0 ? connectTimeoutOverrideMs : super.connectTimeoutMs();
        }

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

        /** 不建连也能配置心跳（验证静默窗口推导/覆盖）。 */
        void configureHeartbeatForTest(long intervalMs) {
            configureHeartbeat(intervalMs, () -> "hb");
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
