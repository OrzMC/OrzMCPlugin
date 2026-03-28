package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CooldownRegistryTest {
    @Test
    public void testCooldownFlow() throws Exception {
        String key = "tpbow|tester";
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
        Assertions.assertTrue(CooldownRegistry.isCoolingDown(key, 1));
        Thread.sleep(1000L);
        Assertions.assertFalse(CooldownRegistry.isCoolingDown(key, 1));
    }
}
