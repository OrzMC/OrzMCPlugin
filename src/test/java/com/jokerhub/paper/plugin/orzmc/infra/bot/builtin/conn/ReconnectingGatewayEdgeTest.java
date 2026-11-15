package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGatewayTest.RecordingListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGatewayTest.TestGateway;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ReconnectingGateway 边界分支补测：发送降级、心跳开关、构造校验、重连入口的"非正常时机"调用。
 *
 * <p>这些分支平时只在异常/竞态下走到（未连接时发送、心跳被关、stop 后仍被调用），
 * 却是"运行期不抛异常"的关键保证——用覆盖率热点清单挑出来后补齐。</p>
 */
class ReconnectingGatewayEdgeTest {

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

    private static ReconnectPolicy fastPolicy() {
        return new ReconnectPolicy(30, 120, 0, 500, 0);
    }

    private static ServerLogger silentLogger() {
        java.util.logging.Logger raw = java.util.logging.Logger.getLogger("reconnecting-gateway-edge-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return () -> raw;
    }

    private void awaitTrue(String what, java.util.function.BooleanSupplier cond) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("超时等待: " + what);
    }

    /** 直接构造（绕过 TestGateway）以覆盖构造校验分支。 */
    @Test
    void constructor_rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new TestGateway(" ", fastPolicy(), null, null, () -> null));
        assertThrows(
                IllegalArgumentException.class, () -> new ReconnectingGateway("qq", null, fastPolicy(), null, null) {
                    @Override
                    protected String resolveEndpoint() {
                        return null;
                    }

                    @Override
                    protected void onGatewayOpen() {}

                    @Override
                    protected void onGatewayPayload(String payload) {}
                });
        assertThrows(
                IllegalArgumentException.class, () -> new ReconnectingGateway("qq", silentLogger(), null, null, null) {
                    @Override
                    protected String resolveEndpoint() {
                        return null;
                    }

                    @Override
                    protected void onGatewayOpen() {}

                    @Override
                    protected void onGatewayPayload(String payload) {}
                });
    }

    @Test
    void isOpen_falseBeforeConnect_trueAfterOpen_falseAfterStop() throws Exception {
        server = TestWsServer.start();
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> wsUrl());
        assertFalse(gateway.isOpen(), "未启动时不应认为已连接");

        gateway.start();
        awaitTrue("建连", () -> gateway.opens.get() == 1);
        assertTrue(gateway.isOpen(), "连接建立后 isOpen 应为 true");

        gateway.stop();
        assertFalse(gateway.isOpen(), "stop 后 isOpen 应为 false");
    }

    @Test
    void sendAndSendBytes_whenDisconnected_areDroppedWithoutThrowing() {
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> null);

        assertDoesNotThrow(() -> gateway.send("{\"op\":1}"));
        assertDoesNotThrow(() -> gateway.sendBytes(new byte[] {1, 2, 3}));
        // 超长 payload 走 clip(...) 的截断分支
        assertDoesNotThrow(() -> gateway.send("x".repeat(500)));
    }

    @Test
    void configureHeartbeat_nonPositiveInterval_disablesHeartbeat() throws Exception {
        server = TestWsServer.start();
        AtomicInteger sent = new AtomicInteger();
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> wsUrl());

        gateway.start();
        awaitTrue("建连", () -> gateway.opens.get() == 1);
        gateway.configureHeartbeat(0, () -> {
            sent.incrementAndGet();
            return "hb";
        });
        gateway.configureHeartbeat(50, null); // null 工厂同样视为关闭

        Thread.sleep(300);
        assertEquals(0, sent.get(), "关闭心跳后不应再发送");
    }

    @Test
    void configureHeartbeatBytes_nonPositiveInterval_disablesHeartbeat() throws Exception {
        server = TestWsServer.start();
        AtomicInteger sent = new AtomicInteger();
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> wsUrl());

        gateway.start();
        awaitTrue("建连", () -> gateway.opens.get() == 1);
        gateway.configureHeartbeatBytes(0, () -> {
            sent.incrementAndGet();
            return new byte[] {1};
        });
        gateway.configureHeartbeatBytes(50, null);

        Thread.sleep(300);
        assertEquals(0, sent.get(), "关闭二进制心跳后不应再发送");
    }

    @Test
    void configureHeartbeat_afterStop_isIgnored() throws Exception {
        server = TestWsServer.start();
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> wsUrl());
        gateway.start();
        awaitTrue("建连", () -> gateway.opens.get() == 1);
        gateway.stop();

        assertDoesNotThrow(() -> gateway.configureHeartbeat(20, () -> "hb"));
        assertDoesNotThrow(() -> gateway.configureHeartbeatBytes(20, () -> new byte[] {1}));
    }

    @Test
    void reconnectNow_withoutConnection_doesNotThrow() {
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> null);

        assertDoesNotThrow(gateway::reconnectNow);
        assertNotNull(gateway.state());
    }

    @Test
    void failFatal_thenLifecycleCalls_areSafe() {
        RecordingListener listener = new RecordingListener();
        gateway = new TestGateway("qq", fastPolicy(), null, listener, () -> null);
        gateway.start();
        gateway.failFatal("测试致命错误");

        assertEquals(ReconnectingGateway.State.FATAL, gateway.state());
        assertEquals(1, listener.fatal.get());
        assertDoesNotThrow(() -> gateway.send("x"));
        assertDoesNotThrow(() -> gateway.configureHeartbeat(20, () -> "hb"));
        // FATAL 后可再次 start（文档承诺可安全重启）
        assertDoesNotThrow(gateway::start);
    }

    @Test
    void isOpen_whenUnderlyingClosed_returnsFalse() throws Exception {
        server = TestWsServer.start();
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> wsUrl());
        gateway.start();
        awaitTrue("建连", () -> gateway.opens.get() == 1);
        server.connections().get(0).closeSocket();
        awaitTrue("连接被标记断开", () -> gateway.state() != ReconnectingGateway.State.OPEN);
        assertFalse(gateway.isOpen(), "底层连接关闭后 isOpen 应为 false");
    }

    private String wsUrl() {
        return "ws://127.0.0.1:" + server.port() + "/";
    }

    @Test
    void send_whenConnected_deliversFrame() throws Exception {
        server = TestWsServer.start();
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> wsUrl());
        gateway.start();
        awaitTrue("建连", () -> gateway.opens.get() == 1);

        gateway.send("hello-frame");
        awaitTrue("服务端收到帧", () -> server.connections().get(0).receivedText().contains("hello-frame"));
        assertTrue(gateway.isOpen());
    }

    @Test
    void sendBytes_whenConnected_deliversFrame() throws Exception {
        server = TestWsServer.start();
        gateway = new TestGateway("qq", fastPolicy(), null, new RecordingListener(), () -> wsUrl());
        gateway.start();
        awaitTrue("建连", () -> gateway.opens.get() == 1);

        gateway.sendBytes(new byte[] {9, 8, 7});
        awaitTrue("服务端收到二进制帧", () -> !server.connections().get(0).receivedText().isEmpty());
    }
}
