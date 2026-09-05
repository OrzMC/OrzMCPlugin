package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jokerhub.paper.plugin.orzmc.features.bot.ImAdminService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/**
 * {@link ImCommandRegistrar} 解析健壮性测试：#313 后真机发现「bind 参数间多空格导致解析错误」
 * ——mojang {@code word()} 链不跳空白，多空格/前导/尾随空格均解析失败。修复 = bind/test 改为
 * greedyString 收整段 + 服务端 split 归一（本测试用真实 {@link CommandDispatcher} 验证整条链路）。
 */
class ImCommandRegistrarTest {

    private final CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
    private final CommandSourceStack source = mock(CommandSourceStack.class);
    private final CommandSender sender = mock(CommandSender.class);

    private static final ImAdminService svc = mock(ImAdminService.class);

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        LiteralCommandNode<CommandSourceStack> im = ImCommandRegistrar.build(svc);
        d.getRoot().addChild(im);
        return d;
    }

    private void bind(String rawInput, String... expected) {
        whenSource();
        assertDoesNotThrow(() -> dispatcher.execute("im bind " + rawInput, source));
        verify(svc).bind(same(sender), eq(expected[0]), eq(expected[1]), eq(expected[2]), eq(expected[3]));
    }

    private void whenSource() {
        org.mockito.Mockito.when(source.getSender()).thenReturn(sender);
    }

    // ---- bind：多空格 / 前导 / 尾随 / 单空格均归一 ----

    @Test
    void bind_singleSpaces_parses() {
        bind("qq group oc_abc admin_group", "qq", "group", "oc_abc", "admin_group");
    }

    @Test
    void bind_multipleSpacesBetweenArgs_parses() {
        bind("qq   group    oc_abc    admin_group", "qq", "group", "oc_abc", "admin_group");
    }

    @Test
    void bind_tabsBetweenArgs_parses() {
        bind("qq\tgroup\toc_abc\tadmin_group", "qq", "group", "oc_abc", "admin_group");
    }

    @Test
    void bind_leadingAndTrailingWhitespace_parses() {
        bind("   qq group oc_abc admin_group   ", "qq", "group", "oc_abc", "admin_group");
    }

    @Test
    void bind_qqGroupOpenIdUpperHex_parses() {
        bind(
                "qq group F73A3B0AE04A8E82B75039A1519AE8EB player_group",
                "qq",
                "group",
                "F73A3B0AE04A8E82B75039A1519AE8EB",
                "player_group");
    }

    @Test
    void bind_wrongArgCount_doesNotCallService() {
        org.mockito.Mockito.clearInvocations(svc); // 隔离前面用例的同签名调用，保证 never() 语义
        whenSource();
        assertDoesNotThrow(() -> dispatcher.execute("im bind qq group oc_abc", source)); // 缺 role
        verify(svc, never()).bind(any(), any(), any(), any(), any());
        verify(sender).sendMessage(any(Component.class)); // 用法提示
    }

    // ---- test：前 3 参数多空格归一，text 段保留内部空格 ----

    @Test
    void test_multipleSpacesBeforeText_parses() {
        whenSource();
        assertDoesNotThrow(() -> dispatcher.execute("im test qq   group    oc_abc    hello", source));
        verify(svc).test(same(sender), eq("qq"), eq("group"), eq("oc_abc"), eq("hello"));
    }

    @Test
    void test_textKeepsInternalSpaces() {
        whenSource();
        assertDoesNotThrow(() -> dispatcher.execute("im test qq group oc_abc 你好 世界 收工", source));
        verify(svc).test(same(sender), eq("qq"), eq("group"), eq("oc_abc"), eq("你好 世界 收工"));
    }

    // ---- parse 层面：修复前多空格即 PARSE ERROR ----

    @Test
    void parse_multipleSpaces_noSyntaxErrors() {
        ParseResults<CommandSourceStack> r = dispatcher.parse("im bind qq   group  oc_abc  admin_group", source);
        assertTrue(r.getExceptions().isEmpty(), "多空格输入不应产生语法错误");
        assertEquals(0, r.getReader().getRemaining().length(), "整条输入应被完整消费");
    }
}
