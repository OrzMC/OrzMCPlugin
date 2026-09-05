package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * FeishuInboundProcessor 单测（注入替身 scheduler/handler/resolver/reply sink）：门槛通过后双 runSync
 * 编排（先服务器线程发起角色判定 → async resolver 完成后再次调度进命令层）、user-only R4 过滤、未绑定
 * 会话 fail-closed、回复回执按 chat_id 直发。
 */
class FeishuInboundProcessorTest {

    private static final ImConversation BOUND =
            new ImConversation(true, "feishu:group:oc_admin", "feishu:group:oc_player", "feishu:user:oc_dm");

    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final RecordingHandler handler = new RecordingHandler(true);
    private final RecordingReplySink outbound = new RecordingReplySink();
    private final MessageFormatter formatter = (message, format) -> List.of(message); // 单段原样

    private static byte[] groupTextEvent(String senderId, String senderType, String chatId, String text) {
        JsonObject senderIdObj = new JsonObject();
        senderIdObj.addProperty("open_id", senderId);
        JsonObject sender = new JsonObject();
        sender.add("sender_id", senderIdObj);
        sender.addProperty("sender_type", senderType);

        JsonObject message = new JsonObject();
        message.addProperty("message_id", "om_1");
        message.addProperty("chat_id", chatId);
        message.addProperty("chat_type", "group");
        message.addProperty("message_type", "text");
        JsonObject content = new JsonObject();
        content.addProperty("text", text);
        message.addProperty("content", content.toString());

        JsonObject header = new JsonObject();
        header.addProperty("event_id", "ev_1");
        header.addProperty("event_type", "im.message.receive_v1");
        JsonObject root = new JsonObject();
        root.addProperty("schema", "2.0");
        root.add("header", header);
        JsonObject event = new JsonObject();
        event.add("sender", sender);
        event.add("message", message);
        root.add("event", event);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 可控 resolver：每 senderId 返回未完成 future，测试手动 complete 以精确验证双调度。 */
    private final java.util.Map<String, CompletableFuture<Boolean>> pending =
            new java.util.concurrent.ConcurrentHashMap<>();

    private FeishuInboundProcessor processor(ImConversation conversation) {
        return new FeishuInboundProcessor(
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

    // =====================================================================
    // 用例
    // =====================================================================

    @Test
    void boundGroupOwner_schedulesTwiceThenDispatchesAsAdmin() {
        processor(BOUND).onEvent(groupTextEvent("ou_owner", "user", "oc_admin", "$help"));

        // 第一次 runSync：发起角色判定（业务未执行、future 未完成）
        assertEquals(1, scheduler.tasks.size(), "门槛通过 → 首次调度（服务器线程发起角色判定）");
        scheduler.runAll(); // 执行首次任务：resolver.isAdmin 已发起
        assertTrue(handler.calls.isEmpty(), "role 未完成前不进命令层");
        assertTrue(scheduler.tasks.isEmpty(), "role 未完成前无二次调度");

        // 角色完成 → 二次 runSync → 进命令层
        pending.get("ou_owner").complete(true);
        assertEquals(1, scheduler.tasks.size(), "角色完成 → 二次调度进命令层");
        scheduler.runAll();

        assertEquals(1, handler.calls.size());
        RecordingHandler.Call call = handler.calls.get(0);
        assertEquals("$help", call.text());
        assertTrue(call.isAdmin(), "owner → 管理");
        assertEquals("ou_owner", call.senderName());
    }

    @Test
    void memberEvent_dispatchedAsNonAdmin() {
        processor(BOUND).onEvent(groupTextEvent("ou_member", "user", "oc_player", "$help"));
        scheduler.runAll();
        pending.get("ou_member").complete(false);
        scheduler.runAll();

        assertEquals(1, handler.calls.size());
        assertFalse(handler.calls.get(0).isAdmin(), "member → 非管理");
    }

    @Test
    void handlerReply_routesBackToSourceChat() {
        processor(BOUND).onEvent(groupTextEvent("ou_owner", "user", "oc_player", "$apply"));
        scheduler.runAll();
        pending.get("ou_owner").complete(true);
        scheduler.runAll();

        assertEquals(1, outbound.replies.size());
        RecordingReplySink.Reply reply = outbound.replies.get(0);
        assertEquals("oc_player", reply.chatId());
        assertEquals("echo:$apply", reply.text());
    }

    @Test
    void botSender_isFilteredBeforeScheduling() {
        processor(BOUND).onEvent(groupTextEvent("ou_bot", "app", "oc_admin", "$x"));
        assertTrue(scheduler.tasks.isEmpty(), "R4：非 user 来源滤除，防回声环");
        assertTrue(handler.calls.isEmpty());
    }

    @Test
    void unboundSession_isRejectedWithoutScheduling() {
        processor(BOUND).onEvent(groupTextEvent("ou_stranger", "user", "oc_stranger", "$x"));
        assertTrue(scheduler.tasks.isEmpty(), "未绑定会话 fail-closed");
        assertTrue(handler.calls.isEmpty());
    }

    @Test
    void mediaEvent_isIgnored() {
        // 图片消息（message_type=image）→ parser 丢弃，不调度
        JsonObject senderIdObj = new JsonObject();
        senderIdObj.addProperty("open_id", "ou_owner");
        JsonObject sender = new JsonObject();
        sender.add("sender_id", senderIdObj);
        sender.addProperty("sender_type", "user");
        JsonObject message = new JsonObject();
        message.addProperty("message_id", "om_2");
        message.addProperty("chat_id", "oc_admin");
        message.addProperty("chat_type", "group");
        message.addProperty("message_type", "image");
        message.addProperty("content", "{\"image_key\":\"img_1\"}");
        JsonObject header = new JsonObject();
        header.addProperty("event_id", "ev_2");
        header.addProperty("event_type", "im.message.receive_v1");
        JsonObject root = new JsonObject();
        root.addProperty("schema", "2.0");
        root.add("header", header);
        JsonObject event = new JsonObject();
        event.add("sender", sender);
        event.add("message", message);
        root.add("event", event);

        processor(BOUND).onEvent(root.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(scheduler.tasks.isEmpty(), "媒体消息不应进入命令层（D6 仅文本）");
    }

    // =====================================================================
    // 替身
    // =====================================================================

    /** drain 型 scheduler：runAll 循环执行直至队列空（处理 runSync 中再次 runSync 的编排）。 */
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

    static final class RecordingReplySink implements FeishuReplySink {
        final List<Reply> replies = new CopyOnWriteArrayList<>();

        record Reply(String chatId, String text) {}

        @Override
        public void sendReply(String chatId, String text) {
            replies.add(new Reply(chatId, text));
        }
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("feishu-inbound-processor-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return raw;
    }
}
