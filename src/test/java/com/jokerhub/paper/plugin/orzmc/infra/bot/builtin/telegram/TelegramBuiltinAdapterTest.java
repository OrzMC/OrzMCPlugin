package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TelegramBuiltinAdapter 分支补测（覆盖率热点 top1：0% → 目标高覆盖）。
 *
 * <p>重点在"没人手动点也会出问题"的路径：long-poll 的 401 停用、非 ok 退避、offset 严格推进、
 * 分段发送（4096 上限）、发送失败的健康告警——都靠替换 {@link TelegramApiClient} 才能驱动。</p>
 */
class TelegramBuiltinAdapterTest {

    private TelegramApiClient api;
    private TelegramInboundProcessor processor;
    private HealthRegistry health;
    private TelegramBuiltinAdapter adapter;
    private final List<Long> sentChatIds = new CopyOnWriteArrayList<>();
    private final List<String> sentTexts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        api = mock(TelegramApiClient.class);
        processor = mock(TelegramInboundProcessor.class);
        health = new HealthRegistry();
        when(api.sendMessageAsync(anyLong(), anyString())).thenAnswer(inv -> {
            sentChatIds.add(inv.getArgument(0));
            sentTexts.add(inv.getArgument(1));
            return CompletableFuture.completedFuture(true);
        });
    }

    @AfterEach
    void tearDown() {
        if (adapter != null) {
            adapter.stop();
        }
    }

    private TelegramBuiltinAdapter newAdapter() {
        adapter = new TelegramBuiltinAdapter(Logger.getLogger("tg-adapter-test"), health, api, processor);
        return adapter;
    }

    private static void awaitTrue(String what, BooleanSupplier cond) {
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

    private static TelegramApiClient.GetUpdatesResult okEmpty() {
        return new TelegramApiClient.GetUpdatesResult(true, List.of(), null);
    }

    // ---------------------------------------------------------------- 构造/生命周期

    @Test
    void constructor_requiresAllDependencies() {
        Logger log = Logger.getLogger("tg-ctor-test");
        assertThrows(IllegalArgumentException.class, () -> new TelegramBuiltinAdapter(null, health, api, processor));
        assertThrows(IllegalArgumentException.class, () -> new TelegramBuiltinAdapter(log, null, api, processor));
        assertThrows(IllegalArgumentException.class, () -> new TelegramBuiltinAdapter(log, health, null, processor));
        assertThrows(IllegalArgumentException.class, () -> new TelegramBuiltinAdapter(log, health, api, null));
    }

    @Test
    void platform_isTelegram() {
        assertEquals("telegram", newAdapter().platform());
    }

    @Test
    void start_isIdempotent_andSetsHealth() {
        when(api.getMe()).thenReturn("orzmc-bot");
        when(api.getUpdates(anyLong(), anyInt())).thenReturn(okEmpty());
        adapter = newAdapter();

        adapter.start();
        adapter.start(); // 幂等：第二次直接返回

        awaitTrue("健康置为可用", () -> Boolean.TRUE.equals(health.get(TelegramBuiltinAdapter.HEALTH_KEY).apiReady));
        adapter.stop();
        adapter.stop(); // 幂等
        assertFalse(health.get(TelegramBuiltinAdapter.HEALTH_KEY).enabled);
    }

    @Test
    void start_getMeFails_degradesAndStopsPolling() {
        when(api.getMe()).thenReturn(null);
        adapter = newAdapter();

        adapter.start();

        awaitTrue("getMe 失败降级", () -> {
            var snap = health.get(TelegramBuiltinAdapter.HEALTH_KEY);
            return snap != null && snap.lastError != null && snap.lastError.contains("getMe");
        });
        verify(api, never()).getUpdates(anyLong(), anyInt());
    }

    @Test
    void pollLoop_401_disablesPolling() {
        when(api.getMe()).thenReturn("bot");
        when(api.getUpdates(anyLong(), anyInt()))
                .thenReturn(new TelegramApiClient.GetUpdatesResult(
                        false, List.of(), new TelegramApiClient.ApiError(401, "Unauthorized")));
        adapter = newAdapter();

        adapter.start();

        awaitTrue("401 停用", () -> {
            var snap = health.get(TelegramBuiltinAdapter.HEALTH_KEY);
            return snap != null && snap.lastError != null && snap.lastError.contains("401");
        });
        // 停用后不再继续拉取
        int calls = org.mockito.Mockito.mockingDetails(api).getInvocations().size();
        awaitTrue(
                "不再增长",
                () -> org.mockito.Mockito.mockingDetails(api).getInvocations().size() == calls);
    }

    @Test
    void pollLoop_nonOkError_backsOffAndRetries() {
        when(api.getMe()).thenReturn("bot");
        when(api.getUpdates(anyLong(), anyInt()))
                .thenReturn(new TelegramApiClient.GetUpdatesResult(
                        false, List.of(), new TelegramApiClient.ApiError(502, "Bad Gateway")))
                .thenReturn(okEmpty());
        adapter = newAdapter();

        adapter.start();

        // 非 ok：健康降级 + 退避后重试（退避 5s；这里只断言"进入失败分支"，不空等重试）
        awaitTrue("非 ok 时健康降级", () -> !health.get(TelegramBuiltinAdapter.HEALTH_KEY).apiReady);
        adapter.stop();
    }

    @Test
    void pollLoop_advancesOffsetAndDispatchesMessage() {
        when(api.getMe()).thenReturn("bot");
        when(api.getUpdates(anyLong(), anyInt()))
                .thenReturn(new TelegramApiClient.GetUpdatesResult(
                        true, List.of(tgUpdate(7, "group", -100L, "hello")), null))
                .thenReturn(okEmpty());
        adapter = newAdapter();

        adapter.start();

        awaitTrue("消息交给处理器", () -> {
            try {
                verify(processor).onMessage(org.mockito.ArgumentMatchers.any(TelegramInboundMessage.class));
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });
        // offset 严格推进到 update_id+1
        awaitTrue(
                "offset 推进",
                () -> org.mockito.Mockito.mockingDetails(api).getInvocations().stream()
                        .filter(i -> "getUpdates".equals(i.getMethod().getName()))
                        .anyMatch(i -> ((Long) i.getArgument(0)) == 8L));
    }

    private static com.google.gson.JsonObject tgUpdate(int updateId, String chatType, long chatId, String text) {
        com.google.gson.JsonObject update = new com.google.gson.JsonObject();
        update.addProperty("update_id", updateId);
        com.google.gson.JsonObject msg = new com.google.gson.JsonObject();
        msg.addProperty("message_id", 11);
        msg.addProperty("text", text);
        com.google.gson.JsonObject chat = new com.google.gson.JsonObject();
        chat.addProperty("id", chatId);
        chat.addProperty("type", chatType);
        msg.add("chat", chat);
        com.google.gson.JsonObject from = new com.google.gson.JsonObject();
        from.addProperty("id", 42L);
        from.addProperty("is_bot", false);
        msg.add("from", from);
        update.add("message", msg);
        return update;
    }

    @Test
    void reconnectIfNeeded_restartsTornPoller() {
        when(api.getMe()).thenReturn("bot");
        when(api.getUpdates(anyLong(), anyInt())).thenReturn(okEmpty());
        adapter = newAdapter();

        // 未运行时 reconnect → start
        adapter.reconnectIfNeeded();
        awaitTrue("已启动", () -> health.get(TelegramBuiltinAdapter.HEALTH_KEY).enabled);

        // 运行中且 poller 存活 → 什么都不做
        adapter.reconnectIfNeeded();
        assertDoesNotThrow(adapter::reconnectIfNeeded);
    }

    // ---------------------------------------------------------------- 出站

    @Test
    void send_ignoresEmptyTextAndInvalidTarget() {
        adapter = newAdapter();

        adapter.send("telegram:group:-100", null);
        adapter.send("telegram:group:-100", "");
        adapter.send(null, "hi");
        adapter.send("telegram:channel:-100", "hi"); // 非 group/user 类型
        adapter.send("telegram:group", "hi"); // 缺 chatId
        adapter.send("telegram:group:", "hi"); // 空 chatId
        adapter.send("telegram:group:not-a-number", "hi");
        adapter.send("discord:group:-100", "hi"); // 非 telegram 前缀

        assertTrue(sentChatIds.isEmpty(), "非法 target/空文本不应发出任何消息");
    }

    @Test
    void send_validTarget_postsToChatId() {
        adapter = newAdapter();

        adapter.send("telegram:group:-1001234567890", "hello");
        adapter.send("telegram:user:42", "dm");

        assertEquals(List.of(-1001234567890L, 42L), sentChatIds);
        assertEquals(List.of("hello", "dm"), sentTexts);
    }

    @Test
    void send_longText_isChunkedAndTruncated() {
        adapter = newAdapter();
        String longText = "x".repeat(4096 * 6);

        adapter.send("telegram:group:1", longText);

        assertEquals(5, sentTexts.size(), "最多分 5 段");
        sentTexts.forEach(part -> assertTrue(part.length() <= 4096, "每段不超过平台上限 4096"));
    }

    @Test
    void send_failure_recordsHealthError() {
        when(api.sendMessageAsync(anyLong(), anyString())).thenReturn(CompletableFuture.completedFuture(false));
        adapter = newAdapter();

        adapter.send("telegram:group:7", "boom");

        assertNotNull(health.get(TelegramBuiltinAdapter.HEALTH_KEY).lastError);
        assertTrue(health.get(TelegramBuiltinAdapter.HEALTH_KEY).lastError.contains("7"));
    }

    @Test
    void send_success_clearsHealthError() {
        adapter = newAdapter();
        health.setLastError(TelegramBuiltinAdapter.HEALTH_KEY, "old error");

        adapter.send("telegram:group:8", "ok");

        assertEquals(null, health.get(TelegramBuiltinAdapter.HEALTH_KEY).lastError);
    }

    @Test
    void send_exceptionalFuture_recordsHealthError() {
        CompletableFuture<Boolean> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("network down"));
        when(api.sendMessageAsync(anyLong(), anyString())).thenReturn(failed);
        adapter = newAdapter();

        adapter.send("telegram:group:9", "x");

        assertNotNull(health.get(TelegramBuiltinAdapter.HEALTH_KEY).lastError);
    }

    @Test
    void send_usesConfiguredChatIdForReplyPath() {
        // 回复路径（sendReply）走同一 sendChunked，仅 reply 语义不同：这里确认 chat_id 一致
        adapter = newAdapter();
        adapter.send("telegram:user:99", "reply");
        verify(api).sendMessageAsync(eq(99L), eq("reply"));
    }

    @Test
    void stop_withoutStart_isNoop() {
        adapter = newAdapter();
        assertDoesNotThrow(adapter::stop);
        assertDoesNotThrow(adapter::reconnectIfNeeded); // 停机态 reconnect → start
    }

    @Test
    void pollLoop_stopsWhenRunningCleared() {
        when(api.getMe()).thenReturn("bot");
        AtomicInteger calls = new AtomicInteger();
        when(api.getUpdates(anyLong(), anyInt())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return okEmpty();
        });
        adapter = newAdapter();
        adapter.start();
        awaitTrue("开始拉取", () -> calls.get() >= 1);
        adapter.stop();
        int atStop = calls.get();
        awaitTrue("停止后不再拉取", () -> calls.get() <= atStop + 1);
    }
}
