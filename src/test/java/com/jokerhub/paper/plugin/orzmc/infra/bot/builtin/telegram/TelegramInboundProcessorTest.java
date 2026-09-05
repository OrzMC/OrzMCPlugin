package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * TelegramInboundProcessor 单测（注入替身 scheduler/handler/resolver/reply sink）：门槛通过后双 runSync
 * 编排、user-only R4 过滤、未绑定会话 fail-closed、回复回执按 chat_id 直发。
 */
class TelegramInboundProcessorTest {

    private static final ImConversation BOUND =
            new ImConversation(true, "telegram:group:-100", "telegram:group:-200", "telegram:user:300");
    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final RecordingHandler handler = new RecordingHandler(true);
    private final RecordingReplySink outbound = new RecordingReplySink();
    private final MessageFormatter formatter = (message, format) -> List.of(message); // 单段原样
    /** 可控 resolver：每 senderId 返回未完成 future，测试手动 complete 精确验证双调度。 */
    private final ConcurrentHashMap<Long, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    private TelegramInboundProcessor processor(ImConversation conversation) {
        return new TelegramInboundProcessor(
                silentLogger(),
                scheduler,
                () -> conversation,
                handler,
                formatter,
                msg -> {
                    CompletableFuture<Boolean> f = new CompletableFuture<>();
                    pending.put(msg.senderId(), f);
                    return f;
                },
                outbound);
    }

    private static TelegramInboundMessage groupMsg(long senderId, long chatId, String text) {
        return new TelegramInboundMessage("group", chatId, 1, text, senderId, false);
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("telegram-processor-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(Level.OFF);
        return raw;
    }

    // =====================================================================
    // 用例
    // =====================================================================
    @Test
    void boundGroupAdmin_schedulesTwiceThenDispatchesAsAdmin() {
        processor(BOUND).onMessage(groupMsg(11, -100, "$help"));
        // 第一次 runSync：发起角色判定（业务未执行、future 未完成）
        assertEquals(1, scheduler.tasks.size(), "门槛通过 → 首次调度（服务器线程发起角色判定）");
        scheduler.runAll();
        assertTrue(handler.calls.isEmpty(), "role 未完成前不进命令层");
        assertTrue(scheduler.tasks.isEmpty(), "role 未完成前无二次调度");
        // 角色完成 → 二次 runSync → 进命令层
        pending.get(11L).complete(true);
        assertEquals(1, scheduler.tasks.size(), "角色完成 → 二次调度进命令层");
        scheduler.runAll();
        assertEquals(1, handler.calls.size());
        RecordingHandler.Call call = handler.calls.get(0);
        assertEquals("$help", call.text());
        assertTrue(call.isAdmin(), "creator/administrator → 管理");
        assertEquals("11", call.senderName());
    }

    @Test
    void memberEvent_dispatchedAsNonAdmin() {
        processor(BOUND).onMessage(groupMsg(99, -100, "$help"));
        scheduler.runAll();
        pending.get(99L).complete(false);
        scheduler.runAll();
        assertEquals(1, handler.calls.size());
        assertFalse(handler.calls.get(0).isAdmin(), "member → 非管理");
    }

    @Test
    void handlerReply_routesBackToSourceChat() {
        processor(BOUND).onMessage(groupMsg(11, -200, "$apply"));
        scheduler.runAll();
        pending.get(11L).complete(true);
        scheduler.runAll();
        assertEquals(1, outbound.replies.size());
        RecordingReplySink.Reply reply = outbound.replies.get(0);
        assertEquals(-200L, reply.chatId());
        assertEquals("echo:$apply", reply.text());
    }

    @Test
    void botSender_isFilteredBeforeScheduling() {
        TelegramInboundMessage bot = new TelegramInboundMessage("group", -100, 1, "$x", 999, true);
        processor(BOUND).onMessage(bot);
        assertTrue(scheduler.tasks.isEmpty(), "R4：bot 来源滤除，防回声环");
        assertTrue(handler.calls.isEmpty());
    }

    @Test
    void unboundSession_isRejectedWithoutScheduling() {
        processor(BOUND).onMessage(groupMsg(11, -999, "$x"));
        assertTrue(scheduler.tasks.isEmpty(), "未绑定会话 fail-closed");
        assertTrue(handler.calls.isEmpty());
    }

    @Test
    void nullMessage_isIgnored() {
        processor(BOUND).onMessage(null);
        assertTrue(scheduler.tasks.isEmpty());
    }

    // =====================================================================
    // 替身
    // =====================================================================
    static final class RecordingScheduler implements ServerScheduler {
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();

        void runAll() {
            while (!tasks.isEmpty()) {
                Runnable task = tasks.remove(0);
                task.run();
            }
        }

        @Override
        public void runSync(Runnable task) {
            tasks.add(task);
        }

        @Override
        public void runAsync(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void runLater(Runnable task, long delayTicks) {
            throw new UnsupportedOperationException();
        }
    }

    static final class RecordingHandler implements BotInboundHandler {
        final boolean echoReply;
        final List<Call> calls = new CopyOnWriteArrayList<>();

        record Call(String text, boolean isAdmin, String senderName) {}

        RecordingHandler(boolean echoReply) {
            this.echoReply = echoReply;
        }

        @Override
        public void handleMessage(
                String message, boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback) {
            calls.add(new Call(message, isAdmin, senderName));
            if (echoReply && callback != null) {
                callback.accept(MessageEnvelope.publicMessage("echo:" + message));
            }
        }
    }

    static final class RecordingReplySink implements TelegramReplySink {
        final List<Reply> replies = new CopyOnWriteArrayList<>();

        record Reply(long chatId, String text) {}

        @Override
        public void sendReply(long chatId, String text) {
            replies.add(new Reply(chatId, text));
        }
    }
}
