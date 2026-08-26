package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicy;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminOnlyInterceptorTest {

    @Test
    void preHandle_notAdminOnly_returnsNull() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(false);
        Player player = mock(Player.class);
        assertNull(interceptor.preHandle(player, "test"));
    }

    @Test
    void preHandle_adminOnly_opPlayer_returnsNull() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(true);
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        assertNull(interceptor.preHandle(player, "test"));
    }

    @Test
    void preHandle_adminOnly_nonOpPlayer_returnsMessage() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(true);
        Player player = mock(Player.class);
        assertNotNull(interceptor.preHandle(player, "test"));
    }

    @Test
    void preHandle_adminOnly_console_returnsNull() {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(true);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        assertNull(interceptor.preHandle(console, "test"));
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, null", // adminOnly + isOp = allowed
        "true, false, error", // adminOnly + nonOp = blocked
        "false, true, null", // not adminOnly + isOp = allowed
        "false, false, null" // not adminOnly + nonOp = allowed
    })
    void preHandle_parameterized(boolean adminOnly, boolean isOp, String expected) {
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(adminOnly);
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(isOp);
        if ("null".equals(expected)) {
            assertNull(interceptor.preHandle(player, "test"));
        } else {
            assertNotNull(interceptor.preHandle(player, "test"));
        }
    }

    @Test
    void supplierPolicy_hotFlip_reEvaluatedPerCall() {
        // P3：command_policies 热生效——admin_only 通过 supplier 变更后，无需重建拦截器即即时生效
        AtomicReference<CommandPolicy> ref = new AtomicReference<>(new CommandPolicy(0, false));
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(ref::get);
        Player player = mock(Player.class);

        assertNull(interceptor.preHandle(player, "test")); // 初始非 adminOnly，放行
        ref.set(new CommandPolicy(0, true));
        assertNotNull(interceptor.preHandle(player, "test")); // 切换 adminOnly 后即时拦截
        assertFalse(interceptor.canUse(player));
        ref.set(new CommandPolicy(0, false));
        assertNull(interceptor.preHandle(player, "test")); // 切回后恢复放行
    }

    @Test
    void supplierPolicy_hotFlip_nonOpPlayer_blockedAfterFlip() {
        // 与上面互补：切到 adminOnly 后非 OP 玩家被 canUse 拒绝（Tab 补全不可见）
        AtomicReference<CommandPolicy> ref = new AtomicReference<>(new CommandPolicy(0, true));
        AdminOnlyInterceptor interceptor = new AdminOnlyInterceptor(ref::get);
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(false);

        assertFalse(interceptor.canUse(player));
    }
}
