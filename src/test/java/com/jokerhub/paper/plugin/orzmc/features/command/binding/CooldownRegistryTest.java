package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CooldownRegistryTest {
    @Test
    public void testCooldownFlow() {
        String key = "tpbow|tester";
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.function.LongSupplier original = CooldownRegistry.clock;
        CooldownRegistry.clock = now::get;
        CooldownRegistry.reset();
        try {
            Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
            Assertions.assertTrue(CooldownRegistry.isCoolingDown(key, 1));
            now.set(1000); // 假时钟推进 1s，冷却过期
            Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
        } finally {
            CooldownRegistry.clock = original;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 60})
    public void isCoolingDown_firstCall_alwaysFalse(int cooldownSecs) {
        String key = "first|user_" + cooldownSecs;
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, cooldownSecs));
    }

    @Test
    public void isCoolingDown_immediateSecondCall_returnsTrue() {
        String key = "immediate|test";
        CooldownRegistry.isCoolingDown(key, 10); // warms the cache
        Assertions.assertTrue(CooldownRegistry.isCoolingDown(key, 10));
    }

    @Test
    public void isCoolingDown_zeroCooldown_alwaysFalse() {
        String key = "nocd|test";
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 0));
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 0));
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 0));
    }

    @Test
    public void reset_clearsCooldownState() {
        String key = "reset|test";
        CooldownRegistry.reset();
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 10));
        Assertions.assertTrue(CooldownRegistry.isCoolingDown(key, 10)); // 冷却中
        CooldownRegistry.reset();
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 10)); // reset 后重新放行
    }
}
