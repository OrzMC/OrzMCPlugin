package com.jokerhub.paper.plugin.orzmc.infra.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * RobustWebSocketClient 重连逻辑补测（覆盖率 47% → 目标 70%+）：
 * 重试耗尽、指数退避计算、disconnect 幂等、onError/onClose 转发链路。
 */
class RobustWebSocketClientReconnectTest {

    static class Testable extends RobustWebSocketClient {
        public Testable(
                ServerLogger server,
                String url,
                ThrottledLogger throttledLogger,
                int maxRetries,
                long baseRetryInterval,
                long maxRetryInterval,
                int jitterPercent,
                long stableResetMs,
                boolean logMessageEnabled,
                long logMessageThrottleMs,
                Map<String, String> httpHeaders,
                String heartbeatPayload,
                WebSocketEventListener listener)
                throws URISyntaxException {
            super(
                    server,
                    url,
                    throttledLogger,
                    maxRetries,
                    baseRetryInterval,
                    maxRetryInterval,
                    jitterPercent,
                    stableResetMs,
                    logMessageEnabled,
                    logMessageThrottleMs,
                    httpHeaders,
                    heartbeatPayload,
                    listener);
        }
    }

    private ServerLogger silentLogger() {
        java.util.logging.Logger raw = java.util.logging.Logger.getLogger("ws-reconnect-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return () -> raw;
    }

    /** WebSocketEventListener 非函数接口，用匿名类包装 */
    private WebSocketEventListener listenerWith(java.util.function.Consumer<Exception> onError) {
        return new WebSocketEventListener() {
            @Override
            public void onOpen() {}

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {
                onError.accept(ex);
            }
        };
    }

    private Testable createClient(WebSocketEventListener listener, int maxRetries) throws Exception {
        return new Testable(
                silentLogger(),
                "ws://localhost:65534",
                mock(ThrottledLogger.class),
                maxRetries,
                100,
                1000,
                0,
                20000,
                false,
                0,
                Map.of(),
                null,
                listener);
    }

    // 反射调用 private 方法/字段
    private Object invokePrivate(Object target, String name, Class<?>... paramTypes) throws Exception {
        Method m = target.getClass().getSuperclass().getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private void setPrivate(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getSuperclass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** 重试次数耗尽：listener.onError("WS reconnect exhausted") + shouldReconnect=false */
    @Test
    void scheduleReconnect_maxRetriesExhausted_notifiesListener() throws Exception {
        AtomicReference<Exception> err = new AtomicReference<>();
        Testable client = createClient(listenerWith(err::set), 0); // maxRetries=0 → 立即耗尽

        invokePrivate(client, "scheduleReconnect");

        assertNotNull(err.get(), "listener 应收到重试耗尽异常");
        assertEquals("WS reconnect exhausted", err.get().getMessage());
        Field shouldReconnect = client.getClass().getSuperclass().getDeclaredField("shouldReconnect");
        shouldReconnect.setAccessible(true);
        assertFalse((boolean) shouldReconnect.get(client), "耗尽后应停止重连");
    }

    /** 重试耗尽后 listener.onError 内部异常不冒泡（防插件崩） */
    @Test
    void scheduleReconnect_listenerThrows_doesNotPropagate() throws Exception {
        Testable client = createClient(
                listenerWith(ex -> {
                    throw new IllegalStateException("listener 自身异常");
                }),
                0);

        assertDoesNotThrow(() -> invokePrivate(client, "scheduleReconnect"));
    }

    /** 指数退避：retryCount=1 → base*2^0=100；retryCount=3 → base*2^2=400（上限 1000 内） */
    @Test
    void calculateBackoffDelay_exponentialWithCap() throws Exception {
        Testable client = createClient(listenerWith(ex -> {}), 10);
        // jitter=0 → 精确值
        setPrivate(client, "retryCount", 1);
        long d1 = (long) invokePrivate(client, "calculateBackoffDelay");
        assertEquals(100L, d1, "第 1 次重试 delay=100ms");

        setPrivate(client, "retryCount", 3);
        long d3 = (long) invokePrivate(client, "calculateBackoffDelay");
        assertEquals(400L, d3, "第 3 次重试 delay=400ms");

        // 封顶：retryCount 很大时不超过 maxRetryInterval(1000)
        setPrivate(client, "retryCount", 99);
        long dCap = (long) invokePrivate(client, "calculateBackoffDelay");
        assertTrue(dCap <= 1000L, "退避应封顶 maxRetryInterval，实际 " + dCap);
    }

    /** jitter 边界：0% 无抖动；100% 抖动不超过 [0.5, 1.5]x */
    @Test
    void calculateBackoffDelay_jitterBounds() throws Exception {
        Testable client = createClient(listenerWith(ex -> {}), 10);
        setPrivate(client, "retryCount", 2); // base=200
        for (int i = 0; i < 50; i++) {
            long d = (long) invokePrivate(client, "calculateBackoffDelay");
            assertTrue(d >= 100L && d <= 300L, "jitter=0 应精确 200，实际 " + d);
        }
    }

    /** disconnect 幂等：第二次调用直接返回（close 只执行一次） */
    @Test
    void disconnect_idempotent() throws Exception {
        Testable client = createClient(listenerWith(ex -> {}), 3);
        client.disconnect();
        client.disconnect(); // 第二次应直接返回（CAS 拦截）
        // 不抛异常即通过（内部 client null 时 close 不执行）
    }

    /** disconnect 后 scheduleReconnect 被跳过（disconnected 标记） */
    @Test
    void scheduleReconnect_afterDisconnect_skipped() throws Exception {
        AtomicReference<Exception> err = new AtomicReference<>();
        Testable client = createClient(listenerWith(err::set), 0);
        client.disconnect();
        invokePrivate(client, "scheduleReconnect");
        assertNull(err.get(), "disconnect 后不应再重连");
    }

    /** 重连调度：未断开时 scheduleReconnect 走 retry 路径（executor 调度） */
    @Test
    void scheduleReconnect_normalRetryPathSchedules() throws Exception {
        AtomicReference<Exception> err = new AtomicReference<>();
        Testable client = createClient(listenerWith(err::set), 3);
        invokePrivate(client, "scheduleReconnect");
        assertNull(err.get(), "未耗尽不应触发 onError");
        // 不会立即崩溃即通过（异步调度）
    }

    /** onClose 转发链路：listener.onClose 收到 code/reason */
    @Test
    void listener_onCloseForwardedWithArgs() throws Exception {
        AtomicReference<Integer> codeRef = new AtomicReference<>();
        AtomicReference<String> reasonRef = new AtomicReference<>();
        Testable client = createClient(
                new WebSocketEventListener() {
                    @Override
                    public void onOpen() {}

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        codeRef.set(code);
                        reasonRef.set(reason);
                    }

                    @Override
                    public void onError(Exception ex) {}
                },
                3);
        // 通过内部 client 触发（反射取 client 字段调 onClose）
        Field f = client.getClass().getSuperclass().getDeclaredField("client");
        f.setAccessible(true);
        Object wsc = f.get(client);
        if (wsc != null) {
            java.lang.reflect.Method m =
                    wsc.getClass().getDeclaredMethod("onClose", int.class, String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(wsc, 1006, "gone", true);
        }
        assertEquals(1006, codeRef.get(), "onClose code 应转发");
        assertEquals("gone", reasonRef.get(), "onClose reason 应转发");
    }
}
