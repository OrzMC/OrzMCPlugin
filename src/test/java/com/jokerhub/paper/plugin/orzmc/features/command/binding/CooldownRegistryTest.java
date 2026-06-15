package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CooldownRegistryTest {
    @Test
    public void testCooldownFlow() throws Exception {
        String key = "tpbow|tester";
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
        Assertions.assertTrue(CooldownRegistry.isCoolingDown(key, 1));
        Thread.sleep(1000L);
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
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
}
