package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicy;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CooldownInterceptorTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 5})
    @Order(1)
    void preHandle_noCooldown_cooldownDisabled_returnsNull(int cooldownSecs) {
        CooldownInterceptor interceptor = new CooldownInterceptor("nocd_cmd", cooldownSecs);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        assertNull(interceptor.preHandle(player, "nocd"));
    }

    @Test
    @Order(2)
    void preHandle_firstCall_returnsNull() {
        CooldownInterceptor interceptor = new CooldownInterceptor("fresh_cmd", 5);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");

        assertNull(interceptor.preHandle(player, "fresh"));
    }

    @Test
    @Order(3)
    void preHandle_secondCallWithinCooldown_returnsTip() {
        CooldownInterceptor interceptor = new CooldownInterceptor("quick_cmd", 10);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Bob");

        // First call — warms the cache
        assertNull(interceptor.preHandle(player, "quick"));
        // Second call immediately — should be within cooldown
        assertNotNull(interceptor.preHandle(player, "quick"));
    }

    @Test
    @Order(4)
    void supplierPolicy_hotFlipCooldown_reEvaluatedPerCall() {
        // P3：command_policies 热生效——cooldown_secs 经 supplier 变更后即时生效（无需重建拦截器）
        AtomicReference<CommandPolicy> ref = new AtomicReference<>(new CommandPolicy(0, false));
        CooldownInterceptor interceptor = new CooldownInterceptor("hot_cmd", ref::get);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Carol");

        // 初始 0 秒：无冷却，连续调用放行
        assertNull(interceptor.preHandle(player, "hot"));
        assertNull(interceptor.preHandle(player, "hot"));

        // 切到 10 秒：首次放行，第二次在冷却内被拦
        ref.set(new CommandPolicy(10, false));
        assertNull(interceptor.preHandle(player, "hot"));
        assertNotNull(interceptor.preHandle(player, "hot"));

        // 切回 0 秒：冷却立即失效
        ref.set(new CommandPolicy(0, false));
        assertNull(interceptor.preHandle(player, "hot"));
    }
}
