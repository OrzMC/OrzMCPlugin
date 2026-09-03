package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PrisonDenyInterceptor;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * {@link BrigadierSupport} 纯静态助手测试：坐牢拒绝链的追加语义（null 守卫 / 不污染入参）
 * 与 {@code guardedExec} 运行时短路语义（拦截器拒绝时不执行 delegate）。
 *
 * <p>这是 #243 拆出 {@code FeatureCommandRegistrar.withPrisonDeny} 实例方法与静态助手的
 * 共有逻辑——7 个特性注册器 + 协调器的开放命令都依赖它，此前零测试。</p>
 */
class BrigadierSupportTest {

    // ---- withPrisonDeny 列表语义 ----

    @Test
    void withPrisonDeny_nullCheck_returnsSameList() {
        List<CommandInterceptor> base = new ArrayList<>(List.of());

        List<CommandInterceptor> res = BrigadierSupport.withPrisonDeny(base, null);

        assertSame(base, res, "prisonCheck 为 null（LP 缺失恒 false）时应原样返回，避免无谓拷贝");
    }

    @Test
    void withPrisonDeny_present_appendsDenyInterceptor() {
        List<CommandInterceptor> base = new ArrayList<>();

        List<CommandInterceptor> res = BrigadierSupport.withPrisonDeny(base, p -> true);

        assertEquals(1, res.size());
        assertInstanceOf(PrisonDenyInterceptor.class, res.get(0), "追加的应是坐牢拒绝拦截器");
        assertTrue(base.isEmpty(), "不得污染调用方传入的原始列表");
    }

    @Test
    void withPrisonDeny_present_keepsOriginalOrder() {
        CommandInterceptor first = new CommandInterceptor() {
            @Override
            public Component preHandle(CommandSender sender, String commandName) {
                return null;
            }
        };
        List<CommandInterceptor> res = BrigadierSupport.withPrisonDeny(List.of(first), p -> true);

        assertEquals(2, res.size());
        assertSame(first, res.get(0), "原有拦截器顺序保持在先，坐牢拒绝追加在链尾");
        assertInstanceOf(PrisonDenyInterceptor.class, res.get(1));
    }

    // ---- guardedExec 运行时短路 ----

    @Test
    void guardedExec_prisonDenied_blocksDelegateAndSendsTip() throws Exception {
        CommandInterceptor deny = new PrisonDenyInterceptor(p -> true);
        AtomicBoolean delegateRan = new AtomicBoolean(false);
        Command<CommandSourceStack> delegate = ctx -> {
            delegateRan.set(true);
            return 0;
        };

        Player player = mock(Player.class);
        Command<CommandSourceStack> wrapped = BrigadierSupport.guardedExec("guide", List.of(deny), delegate);

        int code = wrapped.run(commandContext(player));

        assertEquals(1, code, "被拦截时短路返回 1（Brigadier 约定）");
        assertFalse(delegateRan.get(), "坐牢拒绝时不得执行 delegate 命令体");
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void guardedExec_prisonAllowed_runsDelegate() throws Exception {
        CommandInterceptor allow = new PrisonDenyInterceptor(p -> false);
        AtomicBoolean delegateRan = new AtomicBoolean(false);
        Command<CommandSourceStack> delegate = ctx -> {
            delegateRan.set(true);
            return 0;
        };

        Player player = mock(Player.class);
        Command<CommandSourceStack> wrapped = BrigadierSupport.guardedExec("guide", List.of(allow), delegate);

        int code = wrapped.run(commandContext(player));

        assertEquals(0, code, "放行时返回 delegate 自身结果");
        assertTrue(delegateRan.get());
        verify(player, never()).sendMessage(any(Component.class));
    }

    @SuppressWarnings("unchecked")
    private CommandContext<CommandSourceStack> commandContext(CommandSender sender) {
        CommandSourceStack stack = mock(CommandSourceStack.class);
        when(stack.getSender()).thenReturn(sender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(stack);
        return ctx;
    }
}
