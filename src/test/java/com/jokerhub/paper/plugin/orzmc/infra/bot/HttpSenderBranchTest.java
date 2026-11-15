package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HttpSender 分支补测（热点：50% 覆盖）。
 *
 * <p>重点在"网关返回了什么"这一类纯解析/聚合逻辑：批量结果三分类（全部成功 / 部分失败 / 全部失败）、
 * 畸形响应不误标健康、错误信息截断、并发请求的在飞计数聚合。这些分支决定了健康面板能不能说实话。</p>
 */
class HttpSenderBranchTest {

    private HttpSender sender;
    private HealthRegistry health;
    private ThrottledLogger throttled;

    @BeforeEach
    void setUp() {
        ServerLogger serverLogger = mock(ServerLogger.class);
        org.mockito.Mockito.when(serverLogger.logger()).thenReturn(Logger.getLogger("http-sender-test"));
        throttled = mock(ThrottledLogger.class);
        health = new HealthRegistry();
        sender = new HttpSender(serverLogger, throttled, health);
    }

    private static Method method(String name, Class<?>... params) throws Exception {
        Method m = HttpSender.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private void record(String body) throws Exception {
        method("recordBatchResultFailures", String.class).invoke(sender, body);
    }

    // ---------------------------------------------------------------- 批量结果解析

    @Test
    void recordBatch_nullOrBlankOrNonJson_isIgnored() throws Exception {
        record(null);
        record("  ");
        record("not-json");
        record("[1,2,3]");

        assertNull(health.getRaw("easybot").lastError);
        assertEquals(0, health.getRaw("easybot").deliveryFailed);
    }

    @Test
    void recordBatch_missingResults_isIgnored() throws Exception {
        record("{\"total\":3}");
        record("{\"results\":\"oops\"}");
        record("{\"results\":[]}");

        assertEquals(0, health.getRaw("easybot").deliveryFailed);
    }

    @Test
    void recordBatch_allSent_clearsDeliveryState() throws Exception {
        health.setDelivery("easybot", 2, 2, java.util.List.of("a", "b"));

        record("{\"total\":2,\"results\":{\"a\":{\"status\":\"sent\"},\"b\":{\"status\":\"sent\"}}}");

        assertEquals(0, health.getRaw("easybot").deliveryFailed);
        assertEquals(0, health.getRaw("easybot").deliveryTotal);
    }

    @Test
    void recordBatch_partialFailure_isWarningNotError() throws Exception {
        record("{\"total\":3,\"results\":{"
                + "\"a\":{\"status\":\"sent\"},"
                + "\"b\":{\"status\":\"failed\",\"error\":\"blocked\"},"
                + "\"c\":{\"status\":\"failed\",\"errorCode\":\"40034\"}}}");

        var status = health.getRaw("easybot");
        assertEquals(2, status.deliveryFailed);
        assertEquals(3, status.deliveryTotal);
        assertEquals(java.util.List.of("b", "c"), status.deliveryTargets);
        org.mockito.Mockito.verify(throttled)
                .warning(
                        org.mockito.ArgumentMatchers.eq("easybot-batch-partial"),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void recordBatch_allFailed_isError() throws Exception {
        record("{\"total\":2,\"results\":{"
                + "\"a\":{\"status\":\"failed\",\"error\":\"x\"},"
                + "\"b\":{\"status\":\"failed\"}}}");

        assertEquals(2, health.getRaw("easybot").deliveryFailed);
        org.mockito.Mockito.verify(throttled)
                .error(org.mockito.ArgumentMatchers.eq("easybot-batch-fail"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void recordBatch_unknownTotal_isTreatedAsPartial() throws Exception {
        record("{\"results\":{\"a\":{\"status\":\"failed\"}}}");

        var status = health.getRaw("easybot");
        assertEquals(1, status.deliveryFailed);
        assertEquals(0, status.deliveryTotal, "未知 total 置 0，渲染层按部分失败处理");
        org.mockito.Mockito.verify(throttled)
                .warning(
                        org.mockito.ArgumentMatchers.eq("easybot-batch-partial"),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void recordBatch_malformedResults_doesNotMarkFailure() throws Exception {
        record("{\"total\":1,\"results\":{\"a\":\"not-an-object\"}}");

        assertEquals(0, health.getRaw("easybot").deliveryFailed);
    }

    // ---------------------------------------------------------------- 失败原因 / 明细 / 截断

    @Test
    void batchFailureReason_prefersErrorThenErrorCode() throws Exception {
        Method m = method("batchFailureReason", com.google.gson.JsonObject.class);

        assertEquals("", m.invoke(null, json("{}")));
        assertEquals(": boom", m.invoke(null, json("{\"error\":\"boom\"}")));
        assertEquals(": code=40034", m.invoke(null, json("{\"errorCode\":\"40034\"}")));
        assertEquals(": boom", m.invoke(null, json("{\"error\":\"boom\",\"errorCode\":\"40034\"}")));
        assertEquals("", m.invoke(null, json("{\"error\":\"\"}")));
    }

    private static com.google.gson.JsonObject json(String raw) {
        return com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test
    void buildBatchDetail_includesTotalsWhenKnown() throws Exception {
        Method m = method("buildBatchDetail", int.class, java.util.List.class);

        String known = (String) m.invoke(null, 3, java.util.List.of("b -> failed", "c -> failed"));
        assertTrue(known.contains("(2/3)"), "已知 total 时带比例: " + known);
        assertTrue(known.contains("b -> failed; c -> failed"));

        String unknown = (String) m.invoke(null, -1, java.util.List.of("b -> failed"));
        assertFalse(unknown.contains("(1/"), "未知 total 不带比例: " + unknown);
    }

    @Test
    void limitError_truncatesLongMessages() throws Exception {
        Method m = method("limitError", String.class);

        assertEquals("unknown", m.invoke(null, (Object) null));
        assertEquals("short", m.invoke(null, "short"));

        String longValue = "x".repeat(600);
        String limited = (String) m.invoke(null, longValue);
        assertEquals(512 + 3, limited.length());
        assertTrue(limited.endsWith("..."));
    }

    // ---------------------------------------------------------------- 在飞请求聚合

    @Test
    void completeHttpRequest_aggregatesErrorsAcrossConcurrentRequests() throws Exception {
        Method begin = method("beginHttpRequest");
        Method complete = method("completeHttpRequest", String.class);

        begin.invoke(sender);
        begin.invoke(sender); // 两个在飞

        complete.invoke(sender, (Object) null); // 第一个成功，尚未结束批次
        assertNull(health.getRaw("easybot").lastError, "批次未完成前不更新健康");

        complete.invoke(sender, "HTTP 500");
        assertEquals("HTTP 500", health.getRaw("easybot").lastError);
        assertFalse(health.getRaw("easybot").httpOk);
        assertFalse(health.getRaw("easybot").apiReady);
    }

    @Test
    void completeHttpRequest_keepsFirstErrorOfBatch() throws Exception {
        Method begin = method("beginHttpRequest");
        Method complete = method("completeHttpRequest", String.class);

        begin.invoke(sender);
        begin.invoke(sender);
        complete.invoke(sender, "first-error");
        complete.invoke(sender, "second-error");

        assertEquals("first-error", health.getRaw("easybot").lastError, "同批次保留首个错误（不来回跳）");
    }
}
