package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * QqInboundProcessor 单测：事件解析→会话门槛→线程调度（mock ServerScheduler）→BotInboundHandler 分派、
 * R4 源过滤 bot 消息、D11 未绑定会话拒绝、回复回执（格式化分段 + msg_id 被动通道）。
 */
class QqInboundProcessorTest {

    /** 绑定会话：管理群 / 玩家群 / 管理私聊。 */
    private static final ImConversation BOUND = new ImConversation(true, "qq:group:G-1", "qq:group:G-2", "qq:user:U-1");

    private static String groupFrame(String type, String group, String role, boolean bot, String id, String content) {
        return "{\"op\":0,\"s\":1,\"t\":\"" + type + "\",\"d\":{\"id\":\"" + id + "\",\"group_openid\":\"" + group
                + "\",\"content\":\"" + content + "\",\"author\":{\"member_openid\":\"M-1\",\"member_role\":\""
                + role + "\",\"username\":\"群主\",\"bot\":" + bot + "}}}";
    }

    private static String c2cFrame(String user, String id, String content) {
        return "{\"op\":0,\"s\":1,\"t\":\"C2C_MESSAGE_CREATE\",\"d\":{\"id\":\"" + id + "\",\"content\":\"" + content
                + "\",\"author\":{\"user_openid\":\"" + user + "\"}}}";
    }

    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final RecordingHandler handler = new RecordingHandler(true);
    private final RecordingReplySink outbound = new RecordingReplySink();
    private final MessageFormatter formatter = (message, format) -> List.of(message); // 单段原样

    private QqInboundProcessor processor(ImConversation conversation) {
        return new QqInboundProcessor(silentLogger(), scheduler, () -> conversation, handler, formatter, outbound);
    }

    // =====================================================================
    // 用例
    // =====================================================================

    @Test
    void groupOwnerEvent_schedulesAndDispatchesToServerThread() {
        processor(BOUND)
                .onGatewayEvent(
                        "GROUP_AT_MESSAGE_CREATE",
                        groupFrame("GROUP_AT_MESSAGE_CREATE", "G-1", "owner", false, "m1", "$help"));

        // 门槛通过后仅排入服务器线程任务，业务不在 WS 回调线程执行（R12）
        assertEquals(1, scheduler.tasks.size(), "应调度到服务器线程");
        assertTrue(handler.calls.isEmpty(), "任务未执行前业务不应被调用");
        scheduler.runAll();

        assertEquals(1, handler.calls.size());
        RecordingHandler.Call call = handler.calls.get(0);
        assertEquals("$help", call.text());
        assertTrue(call.isAdmin(), "owner 角色 → 管理");
        assertEquals("群主", call.senderName());
    }

    @Test
    void handlerReply_routesBackToSourceChatWithMsgId() {
        processor(BOUND)
                .onGatewayEvent(
                        "GROUP_AT_MESSAGE_CREATE",
                        groupFrame("GROUP_AT_MESSAGE_CREATE", "G-1", "owner", false, "m1", "$apply"));
        scheduler.runAll();

        // handler 回执信封 → 格式化分段 → 送回来源群（被动通道带 msg_id，D14）
        assertEquals(1, outbound.replies.size());
        QqReplySinkReply reply = outbound.replies.get(0);
        assertEquals("group", reply.chatType());
        assertEquals("G-1", reply.chatId());
        assertEquals("echo:$apply", reply.text());
        assertEquals("m1", reply.replyMsgId());
    }

    @Test
    void memberEvent_dispatchedAsNonAdmin() {
        processor(BOUND)
                .onGatewayEvent(
                        "GROUP_AT_MESSAGE_CREATE",
                        groupFrame("GROUP_AT_MESSAGE_CREATE", "G-2", "member", false, "m2", "$help"));
        scheduler.runAll();

        assertEquals(1, handler.calls.size());
        assertFalse(handler.calls.get(0).isAdmin(), "member → 非管理");
    }

    @Test
    void c2cFromBoundAdminDm_dispatchesAsNonAdmin() {
        processor(BOUND).onGatewayEvent("C2C_MESSAGE_CREATE", c2cFrame("U-1", "c1", "私聊"));
        scheduler.runAll();

        assertEquals(1, handler.calls.size());
        assertFalse(handler.calls.get(0).isAdmin(), "C2C 无角色非管理");
        assertEquals(1, outbound.replies.size());
        assertEquals("user", outbound.replies.get(0).chatType());
        assertEquals("U-1", outbound.replies.get(0).chatId());
    }

    @Test
    void unboundSession_isRejectedWithoutScheduling() {
        // G-3 不在绑定会话集 → fail-closed 拒绝，不打扰陌生会话（D11）
        processor(BOUND)
                .onGatewayEvent(
                        "GROUP_AT_MESSAGE_CREATE",
                        groupFrame("GROUP_AT_MESSAGE_CREATE", "G-3", "member", false, "m3", "$help"));

        assertTrue(scheduler.tasks.isEmpty(), "未绑定会话不应进入命令层");
        assertTrue(handler.calls.isEmpty());
        assertTrue(outbound.replies.isEmpty());
    }

    @Test
    void unboundSession_logsCopyableBindCommands() {
        // UX：未绑定候选日志直接给出可复制执行的完整 bind 命令（无需管理员自行拼接）
        var logs = new CopyOnWriteArrayList<String>();
        QqInboundProcessor p =
                new QqInboundProcessor(captureLogger(logs), scheduler, () -> BOUND, handler, formatter, outbound);
        p.onGatewayEvent(
                "GROUP_AT_MESSAGE_CREATE",
                groupFrame("GROUP_AT_MESSAGE_CREATE", "G-3", "member", false, "m3", "$help"));

        String joined = String.join("\n", logs);
        assertTrue(joined.contains("qq:group:G-3"), "日志应含目标会话");
        assertTrue(joined.contains("/config im bind qq group G-3 admin_group"), "日志应直接给出管理群绑定命令：\n" + joined);
        assertTrue(joined.contains("/config im bind qq group G-3 player_group"), "日志应给出玩家群绑定命令");
    }

    @Test
    void disabledConversation_rejectsEverything() {
        processor(new ImConversation(false, "qq:group:G-1", "", ""))
                .onGatewayEvent(
                        "GROUP_AT_MESSAGE_CREATE",
                        groupFrame("GROUP_AT_MESSAGE_CREATE", "G-1", "owner", false, "m4", "$x"));

        assertTrue(scheduler.tasks.isEmpty(), "会话未启用一律拒绝");
    }

    @Test
    void botAuthoredMessage_isFilteredBeforeGate() {
        processor(BOUND)
                .onGatewayEvent(
                        "GROUP_AT_MESSAGE_CREATE",
                        groupFrame("GROUP_AT_MESSAGE_CREATE", "G-1", "member", true, "m5", "$x"));

        assertTrue(scheduler.tasks.isEmpty(), "R4：机器人消息滤除，防回声环");
        assertTrue(handler.calls.isEmpty());
    }

    @Test
    void malformedOrUnsupportedEvent_isIgnored() {
        QqInboundProcessor p = processor(BOUND);
        p.onGatewayEvent("GROUP_AT_MESSAGE_CREATE", "not-json");
        p.onGatewayEvent("AT_MESSAGE_CREATE", "{\"d\":{}}"); // 频道消息不支持
        p.onGatewayEvent(
                "GROUP_AT_MESSAGE_CREATE",
                groupFrame("GROUP_AT_MESSAGE_CREATE", "G-1", "member", false, "m6", "  ")); // 空文本

        assertTrue(scheduler.tasks.isEmpty());
        assertTrue(handler.calls.isEmpty());
    }

    // =====================================================================
    // 替身
    // =====================================================================

    /** 记录 runSync 任务但不立即执行（模拟服务器线程），测试可手动 runAll。 */
    static final class RecordingScheduler implements ServerScheduler {
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();

        void runAll() {
            tasks.forEach(Runnable::run);
            tasks.clear();
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

    record QqReplySinkReply(String chatType, String chatId, String text, String replyMsgId) {}

    static final class RecordingReplySink implements QqReplySink {
        final List<QqReplySinkReply> replies = new CopyOnWriteArrayList<>();

        @Override
        public void sendReply(String chatType, String chatId, String text, String replyMsgId) {
            replies.add(new QqReplySinkReply(chatType, chatId, text, replyMsgId));
        }
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("qq-inbound-processor-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.OFF);
        return raw;
    }

    /** 捕获 INFO 日志到列表（供日志文案断言）。 */
    private static Logger captureLogger(java.util.List<String> into) {
        Logger raw = Logger.getLogger("qq-inbound-processor-test-capture");
        raw.setUseParentHandlers(false);
        raw.setLevel(java.util.logging.Level.INFO);
        raw.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                into.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return raw;
    }
}
