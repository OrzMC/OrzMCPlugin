package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

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
 * DiscordInboundProcessor 单测（注入替身 scheduler/handler/resolver/reply sink）：门槛通过后双 runSync
 * 编排、bot 来源 R4 滤除、未绑定会话 fail-closed、回复回执按来源频道直发。
 */
class DiscordInboundProcessorTest {

    private static final ImConversation BOUND =
            new ImConversation(true, "discord:group:111", "discord:group:222", "discord:user:333");
    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final RecordingHandler handler = new RecordingHandler(true);
    private final RecordingReplySink outbound = new RecordingReplySink();
    private final MessageFormatter formatter = (message, format) -> List.of(message); // 单段原样
    /** 可控 resolver：每 senderId 返回未完成 future，测试手动 complete 精确验证双调度。 */
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();

    private DiscordInboundProcessor processor(ImConversation conversation) {
        return new DiscordInboundProcessor(
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

    private static DiscordInboundMessage groupMsg(String senderId, String text) {
        return new DiscordInboundMessage("group", "111", "g1", "m1", text, senderId, "alice", false);
    }

    private static Logger silentLogger() {
        Logger raw = Logger.getLogger("discord-processor-test");
        raw.setUseParentHandlers(false);
        raw.setLevel(Level.OFF);
        return raw;
    }

    // =====================================================================
    // 用例
    // =====================================================================
    @Test
    void boundGroupAdmin_schedulesTwiceThenDispatchesAsAdmin() {
        processor(BOUND).onMessage(groupMsg("u1", "$help"));
        // 第一次 runSync：发起角色判定（业务未执行、future 未完成）
        assertEquals(1, scheduler.tasks.size(), "门槛通过 → 首次调度（服务器线程发起角色判定）");
        scheduler.runAll();
        assertTrue(handler.calls.isEmpty(), "role 未完成前不进命令层");
        assertTrue(scheduler.tasks.isEmpty(), "role 未完成前无二次调度");
        // 角色完成 → 二次 runSync → 进命令层
        pending.get("u1").complete(true);
        assertEquals(1, scheduler.tasks.size(), "角色完成 → 二次调度进命令层");
        scheduler.runAll();
        assertEquals(1, handler.calls.size());
        RecordingHandler.Call call = handler.calls.get(0);
        assertEquals("$help", call.text());
        assertTrue(call.isAdmin(), "creator/administrator → 管理");
        assertEquals("u1", call.senderName());
    }

    @Test
    void memberEvent_dispatchedAsNonAdmin() {
        processor(BOUND).onMessage(groupMsg("u99", "$help"));
        scheduler.runAll();
        pending.get("u99").complete(false);
        scheduler.runAll();
        assertEquals(1, handler.calls.size());
        assertFalse(handler.calls.get(0).isAdmin(), "member → 非管理");
    }

    @Test
    void handlerReply_routesBackToSourceChannel() {
        processor(BOUND).onMessage(groupMsg("u1", "$l"));
        scheduler.runAll();
        pending.get("u1").complete(false);
        scheduler.runAll();
        // handler 产生回复信封 → 回复 sink 收一次（来源频道直发）
        assertEquals(1, outbound.replies.size());
        assertEquals("111", outbound.replies.get(0).channelId(), "回复回来源频道");
    }

    @Test
    void botSender_filteredBeforeDispatch() {
        processor(BOUND).onMessage(new DiscordInboundMessage("group", "111", "g1", "m1", "$l", "bot1", "bot", true));
        assertTrue(scheduler.tasks.isEmpty(), "bot 来源不调度（R4）");
        assertTrue(handler.calls.isEmpty());
    }

    @Test
    void unboundConversation_failsClosed() {
        // 来源会话不在绑定集（不同频道）
        DiscordInboundProcessor p = processor(BOUND);
        p.onMessage(new DiscordInboundMessage("group", "999", "g1", "m1", "$l", "u1", "alice", false));
        assertTrue(scheduler.tasks.isEmpty(), "未绑定会话不调度（fail-closed）");
        assertTrue(handler.calls.isEmpty());
    }

    @Test
    void boundDmSession_allowed() {
        DiscordInboundProcessor p = processor(BOUND);
        // admin_dm = discord:user:333 → DM 消息（channel_id=777，author=333）target=user:333 ∈ 绑定
        p.onMessage(new DiscordInboundMessage("user", "777", null, "m1", "$h", "333", "alice", false));
        scheduler.runAll();
        pending.get("333").complete(false);
        scheduler.runAll();
        assertEquals(1, handler.calls.size(), "绑定私聊会话可上行问答");
    }

    // =====================================================================
    // 替身
    // =====================================================================
    private static final class RecordingScheduler implements ServerScheduler {
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();

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

        void runAll() {
            while (!tasks.isEmpty()) {
                Runnable task = tasks.remove(0);
                task.run();
            }
        }
    }

    private static final class RecordingHandler implements BotInboundHandler {
        private final boolean reply;
        final List<Call> calls = new CopyOnWriteArrayList<>();

        RecordingHandler(boolean reply) {
            this.reply = reply;
        }

        @Override
        public void handleMessage(
                String content, boolean isAdmin, String senderName, Consumer<MessageEnvelope> replySink) {
            calls.add(new Call(content, isAdmin, senderName));
            if (reply) {
                replySink.accept(MessageEnvelope.publicMessage("echo:" + content));
            }
        }

        record Call(String text, boolean isAdmin, String senderName) {}
    }

    private static final class RecordingReplySink implements DiscordReplySink {
        final List<Reply> replies = new CopyOnWriteArrayList<>();

        @Override
        public void sendReply(DiscordInboundMessage source, String text) {
            replies.add(new Reply(source.channelId(), text));
        }

        record Reply(String channelId, String text) {}
    }
}
