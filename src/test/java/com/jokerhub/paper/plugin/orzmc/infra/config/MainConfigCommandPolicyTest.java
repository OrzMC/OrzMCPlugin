package com.jokerhub.paper.plugin.orzmc.infra.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainConfigCommandPolicyTest {
    @Test
    public void testPoliciesMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("commands.tpbow.cooldown_secs", 5);
        cfg.set("commands.tpbow.admin_only", true);
        cfg.set("commands.menu.cooldown_secs", 0);
        TypedConfigs.MainConfig mc = TypedConfigs.MainConfig.from(cfg);
        Assertions.assertTrue(mc.commandPolicies().containsKey("tpbow"));
        Assertions.assertEquals(5, mc.commandPolicies().get("tpbow").cooldownSeconds());
        Assertions.assertTrue(mc.commandPolicies().get("tpbow").adminOnly());
        Assertions.assertTrue(mc.commandPolicies().containsKey("menu"));
        Assertions.assertEquals(0, mc.commandPolicies().get("menu").cooldownSeconds());
        Assertions.assertFalse(mc.commandPolicies().get("menu").adminOnly());
    }
}
