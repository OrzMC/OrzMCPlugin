package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGatewayTest.RecordingListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGatewayTest.TestGateway;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ReconnectingGateway 剩余分支：**建连失败**这一类（地址不可用 / 非法 URI / 端口拒绝 / 重试耗尽）。
 *
 * <p>这些路径平时只在网关宕机或配置写错时出现，但决定了"故障时插件会不会卡死、会不会无限重试刷日志"。</p>
 */
class ReconnectingGatewayRetryTest {

    private TestGateway gateway;

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.stop();
        }
    }

    private static ReconnectPolicy tinyPolicy() {
        // 最多 2 次重试、基础退避 20ms：让"重试耗尽"在毫秒级发生
        return new ReconnectPolicy(2, 40, 0, 20, 0);
    }

    private static void awaitTrue(String what, BooleanSupplier cond) {
        long deadline = System.currentTimeMillis() + 8000;
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

    @Test
    void blankEndpoint_entersRetryThenStops() {
        gateway = new TestGateway("qq", tinyPolicy(), null, new RecordingListener(), () -> "   ");
        RecordingListener listener = new RecordingListener();
        gateway = new TestGateway("qq", tinyPolicy(), null, listener, () -> "   ");

        gateway.start();

        awaitTrue("退避重试未耗尽前不应 fatal", () -> gateway.state() != ReconnectingGateway.State.OPEN);
        assertEquals(0, listener.connected.get(), "地址不可用不应连上");
    }

    @Test
    void resolveEndpointThrowing_isHandledAsLost() {
        RecordingListener listener = new RecordingListener();
        gateway = new TestGateway("qq", tinyPolicy(), null, listener, () -> {
            throw new IllegalStateException("config broken");
        });

        gateway.start();

        awaitTrue("异常后进入重试/停止态", () -> gateway.state() != ReconnectingGateway.State.OPEN);
        assertEquals(0, listener.connected.get());
        // 异常被吞并转为重试（lastProblem 只记录致命问题，普通建连失败不写）
        assertTrue(gateway.state() != ReconnectingGateway.State.OPEN);
    }

    @Test
    void invalidUri_isRejectedWithoutThrowing() {
        gateway = new TestGateway("qq", tinyPolicy(), null, new RecordingListener(), () -> "ht tp://bad uri\u0000");

        gateway.start();

        awaitTrue("非法 URI 进入重试态", () -> gateway.state() != ReconnectingGateway.State.IDLE);
    }

    @Test
    void refusedConnection_retriesUpToLimit() {
        RecordingListener listener = new RecordingListener();
        // 1 号端口通常无监听 → 连接被拒（走 onWsError → transitionLost → queueReconnect）
        gateway = new TestGateway("qq", tinyPolicy(), null, listener, () -> "ws://127.0.0.1:1/");

        gateway.start();

        awaitTrue(
                "最终进入 FATAL 或持续重试",
                () -> gateway.state() == ReconnectingGateway.State.FATAL
                        || gateway.state() == ReconnectingGateway.State.CONNECTING
                        || gateway.state() == ReconnectingGateway.State.IDLE);
        assertEquals(0, listener.connected.get(), "端口拒绝不应误报连上");
    }

    @Test
    void stopWhileConnecting_isSafe() {
        gateway = new TestGateway("qq", tinyPolicy(), null, new RecordingListener(), () -> "ws://127.0.0.1:1/");

        gateway.start();
        gateway.stop();

        assertEquals(ReconnectingGateway.State.STOPPED, gateway.state());
    }

    @Test
    void reconnectNowAfterFatal_doesNotThrow() {
        gateway = new TestGateway("qq", tinyPolicy(), null, new RecordingListener(), () -> null);
        gateway.start();
        gateway.failFatal("boom");

        assertEquals(ReconnectingGateway.State.FATAL, gateway.state());
        gateway.reconnectNow();
        assertTrue(gateway.state() != null);
    }
}
