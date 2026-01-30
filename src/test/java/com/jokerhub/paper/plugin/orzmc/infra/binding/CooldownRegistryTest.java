package com.jokerhub.paper.plugin.orzmc.infra.binding;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CooldownRegistryTest {
    @Test
    public void testCooldownFlow() throws Exception {
        String key = "tpbow|tester";
        // first call should not be cooling down
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
        // immediate second call should be cooling down
        Assertions.assertTrue(CooldownRegistry.isCoolingDown(key, 1));
        // after 1s should not be cooling down
        Thread.sleep(1000L);
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
    }
}
