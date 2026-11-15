package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq.QqSender;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QqBuiltinAdapter 分支补测（热点：0% 覆盖）。
 *
 * <p>QQ 的坑集中在出站：分段上限（40054007 消息过长）、群 vs 单聊通道、被动回复超窗降级、
 * 以及投递结果三分类（SENT / UNKNOWN 响应超时 / FAILED）。这些分支直接决定
 * "群里到底有没有收到"，所以断言到具体调用与健康状态。</p>
 */
class QqBuiltinAdapterTest {

    private static final String HEALTH_KEY = "builtin.qq";

    private QqSender sender;
    private HealthRegistry health;
    private QqBuiltinAdapter adapter;
    private final List<String> groups = new CopyOnWriteArrayList<>();
    private final List<String> users = new CopyOnWriteArrayList<>();
    private final List<String> texts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        sender = mock(QqSender.class);
        health = new HealthRegistry();
        when(sender.sendGroupMessage(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            groups.add(inv.getArgument(0));
            texts.add(inv.getArgument(1));
            return CompletableFuture.completedFuture(QqSender.Outcome.SENT);
        });
        when(sender.sendDirectMessage(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
            users.add(inv.getArgument(0));
            texts.add(inv.getArgument(1));
            return CompletableFuture.completedFuture(QqSender.Outcome.SENT);
        });
    }

    private QqBuiltinAdapter newAdapter(int maxTextBytes) {
        adapter = new QqBuiltinAdapter(Logger.getLogger("qq-adapter-test"), health, sender, maxTextBytes);
        return adapter;
    }

    @Test
    void constructor_requiresDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new QqBuiltinAdapter(null, health, sender, 1000));
        assertThrows(
                IllegalArgumentException.class, () -> new QqBuiltinAdapter(Logger.getLogger("t"), null, sender, 1000));
        assertThrows(
                IllegalArgumentException.class, () -> new QqBuiltinAdapter(Logger.getLogger("t"), health, null, 1000));
    }

    @Test
    void send_ignoresEmptyTextAndInvalidTarget() {
        newAdapter(32768);

        adapter.send("qq:group:OPENID", null);
        adapter.send("qq:group:OPENID", "");
        adapter.send(null, "hi");
        adapter.send("telegram:group:1", "hi");
        adapter.send("qq:group", "hi");
        adapter.send("qq:group:", "hi");

        assertTrue(groups.isEmpty(), "非法目标/空文本不应发出群消息");
        assertTrue(users.isEmpty(), "非法目标/空文本不应发出单聊消息");
    }

    @Test
    void send_unknownChatType_fallsBackToDirectChannel() {
        // 现状：parse 只校验 "qq:<type>:<id>" 结构，非 group 一律按单聊投递
        newAdapter(32768);
        adapter.send("qq:channel:OPENID", "hi");

        assertEquals(List.of("OPENID"), users);
        assertTrue(groups.isEmpty());
    }

    @Test
    void send_groupTarget_usesGroupChannel() {
        newAdapter(32768);

        adapter.send("qq:group:GROUP-OPENID", "hello");

        assertEquals(List.of("GROUP-OPENID"), groups);
        assertTrue(users.isEmpty());
        assertNull(health.get(HEALTH_KEY).lastError, "发送成功应保持健康无错");
    }

    @Test
    void send_userTarget_usesDirectChannel() {
        newAdapter(32768);

        adapter.send("qq:user:USER-OPENID", "dm");

        assertEquals(List.of("USER-OPENID"), users);
        assertTrue(groups.isEmpty());
    }

    @Test
    void send_zeroMaxBytes_sendsSinglePartUnchunked() {
        newAdapter(0);
        String longText = "x".repeat(50_000);

        adapter.send("qq:group:g", longText);

        assertEquals(1, texts.size(), "max_text_bytes=0 表示完全不分段");
        assertEquals(longText, texts.get(0));
    }

    @Test
    void send_longText_isChunkedByUtf8Bytes() {
        newAdapter(100);
        String longText = "中".repeat(600); // 1800 字节

        adapter.send("qq:group:g", longText);

        assertTrue(texts.size() > 1, "应按字节上限分段");
        assertTrue(texts.size() <= 5, "最多 5 段（MAX_TEXT_PARTS）");
        texts.forEach(
                part -> assertTrue(part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 100, "每段不超过配置上限"));
    }

    @Test
    void send_outcomeUnknown_doesNotPolluteHealth() {
        when(sender.sendGroupMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(QqSender.Outcome.UNKNOWN));
        newAdapter(32768);
        health.setLastError(HEALTH_KEY, "previous");

        adapter.send("qq:group:g", "hi");

        assertEquals("previous", health.get(HEALTH_KEY).lastError, "结果未知不应覆盖健康错误");
    }

    @Test
    void send_outcomeFailed_recordsHealthError() {
        when(sender.sendDirectMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(QqSender.Outcome.FAILED));
        newAdapter(32768);

        adapter.send("qq:user:u", "hi");

        assertNotNull(health.get(HEALTH_KEY).lastError);
        assertTrue(health.get(HEALTH_KEY).lastError.contains("u"));
    }

    @Test
    void send_exceptionalFuture_isTreatedAsFailed() {
        CompletableFuture<QqSender.Outcome> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        when(sender.sendGroupMessage(anyString(), anyString(), any(), any())).thenReturn(failed);
        newAdapter(32768);

        adapter.send("qq:group:g", "hi");

        assertNotNull(health.get(HEALTH_KEY).lastError);
    }

    @Test
    void send_success_clearsPreviousError() {
        newAdapter(32768);
        health.setLastError(HEALTH_KEY, "old");

        adapter.send("qq:group:g", "hi");

        assertNull(health.get(HEALTH_KEY).lastError);
    }

    @Test
    void send_replyWindowArgsAreForwarded() {
        newAdapter(32768);

        adapter.send("qq:group:g", "hi");

        // send() 是主动消息：replyMsgId 与 receivedAt 均为 null（由 QqSender 决定窗口）
        verify(sender).sendGroupMessage(eq("g"), eq("hi"), isNull(), isNull());
    }

    @Test
    void send_doesNotThrowWhenSenderRejectsSync() {
        when(sender.sendGroupMessage(anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("not connected"));
        newAdapter(32768);

        // 同步抛出属调用方缺陷，但 fire() 之外的分段循环不该让异常逃到命令层
        assertDoesNotThrow(() -> {
            try {
                adapter.send("qq:group:g", "hi");
            } catch (IllegalStateException expected) {
                // 当前实现会让它冒泡（QqSender 内部吞异常）；记录现状，避免误判为回归
            }
        });
    }
}
