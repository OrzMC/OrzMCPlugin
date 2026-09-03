package com.jokerhub.paper.plugin.orzmc.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

/**
 * 协调器 {@code FeatureCommandRegistrar} 仍内联的简单命令集成回归网。
 *
 * <p>guide/menu/tpbow 的玩家行为（开背包/发弓）已由各自 Feature 集成测试覆盖，本类只补
 * 协调器 registerSimple 专属接缝：<b>拦截链组合</b>（非玩家被 PlayerOnly 短路，回显
 * 「需要玩家执行」而非执行 delegate）与 admin 简单命令 /maintenance 的同步回显接线。
 * 文案「需要玩家执行」能区分 PlayerOnly 短路与 registerSimple 内部 else 兜底
 * （后者是 styles.error「仅玩家可用」），从而锁定链组合正确。</p>
 */
@Tag("integration")
public class CoordinatorInlineIntegrationTest {

    private ServerMock server;
    private OrzMC plugin;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(OrzMC.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---- registerSimple 开放命令（guide/menu/tpbow）：非玩家被 PlayerOnly 链短路 ----

    @Test
    public void guideNonPlayerSender_blockedByPlayerOnlyChain() {
        String reply = dispatchAndCaptureFirstText(nonPlayerSender(), "guide");
        assertTrue(reply.contains("需要玩家执行"), "非玩家应被 PlayerOnly 短路，实际: " + reply);
    }

    @Test
    public void menuNonPlayerSender_blockedByPlayerOnlyChain() {
        String reply = dispatchAndCaptureFirstText(nonPlayerSender(), "menu");
        assertTrue(reply.contains("需要玩家执行"), "非玩家应被 PlayerOnly 短路，实际: " + reply);
    }

    @Test
    public void tpbowNonPlayerSender_blockedByPlayerOnlyChain() {
        String reply = dispatchAndCaptureFirstText(nonPlayerSender(), "tpbow");
        assertTrue(reply.contains("需要玩家执行"), "非玩家应被 PlayerOnly 短路，实际: " + reply);
    }

    // ---- admin 内联命令 ----
    // /orzdebug 未单独列测：其 greedyString 子分支在 MockBukkit dispatchCommand 下走 vanilla
    // "Unknown command" 兜底（连无参根用法分支都不可达，见 SecurityIntegrationTest:62 同类限制），
    // 真实 Bot 入站解析由 BotInboundHandler 单测覆盖。coordinator 的 admin 内联路径由下方
    // /maintenance（同 adminInterceptors 构造 + 真实派发）代表覆盖。

    @Test
    public void maintenanceStatusOpPlayer_repliesCurrentState() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "maintenance status"));

        assertNotNull(player.nextComponentMessage(), "/maintenance status 应同步回显当前维护状态");
    }

    @Test
    public void maintenanceNoArgsOpPlayer_repliesUsage() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "maintenance"));

        assertNotNull(player.nextComponentMessage(), "/maintenance 无参应回显用法");
    }

    // ---- helpers ----

    private CommandSender nonPlayerSender() {
        CommandSender sender = mock(CommandSender.class);
        return sender;
    }

    /** dispatch 到 mock sender，捕获其收到的全部消息，返回第一条纯文本（无消息则返回空串）。 */
    private String dispatchAndCaptureFirstText(CommandSender sender, String command) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        assertDoesNotThrow(() -> server.dispatchCommand(sender, command));
        verify(sender, times(1)).sendMessage(captor.capture());
        return PlainTextComponentSerializer.plainText().serialize(captor.getValue());
    }
}
