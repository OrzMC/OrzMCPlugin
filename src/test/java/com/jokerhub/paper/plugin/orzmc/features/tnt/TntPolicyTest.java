package com.jokerhub.paper.plugin.orzmc.features.tnt;

import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TntPolicyTest {
    @Test
    public void testWhitelistContains() {
        // Mock config
        TypedConfigs.TntConfig cfg = new TypedConfigs.TntConfig(
                true,
                false,
                5,
                1000L,
                List.of(Map.of("minX", 0, "maxX", 10, "minY", 0, "maxY", 255, "minZ", 0, "maxZ", 10, "world", "world")),
                List.of());
        TntPolicy policy = new TntPolicy(cfg);
        // Verify within region
        boolean notIn = policy.isNotInWhiteList("world", 5, 100, 5);
        Assertions.assertFalse(notIn);
        // Verify outside region
        boolean notIn2 = policy.isNotInWhiteList("world", 20, 100, 20);
        Assertions.assertTrue(notIn2);
    }
}
