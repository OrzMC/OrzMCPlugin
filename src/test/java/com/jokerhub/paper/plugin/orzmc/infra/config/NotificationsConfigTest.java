package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.NotifyPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.NotifyPolicy.Notifications;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class NotificationsConfigTest {
    @Test
    public void testNotificationsMapping() {
        // Simulates a "notifications" ConfigurationSection (not the full file root)
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("whitelist_cleanup.private.enabled", true);
        cfg.set("whitelist_cleanup.private.admin_only", true);
        cfg.set("whitelist_cleanup.public.enabled", false);
        cfg.set("whitelist_cleanup.channel_key", "ops-alert");
        cfg.set("tnt_alert.private.enabled", true);
        cfg.set("tnt_alert.private.admin_only", false);
        cfg.set("tnt_alert.public.enabled", true);
        cfg.set("tnt_alert.channel_key", "safety-alerts");

        Notifications ns = Notifications.from(cfg);
        Assertions.assertTrue(ns.policies().containsKey("whitelist_cleanup"));
        NotifyPolicy p1 = ns.policies().get("whitelist_cleanup");
        Assertions.assertTrue(p1.privateEnabled());
        Assertions.assertTrue(p1.privateAdminOnly());
        Assertions.assertFalse(p1.publicEnabled());
        Assertions.assertEquals("ops-alert", p1.channelKey());

        NotifyPolicy p2 = ns.policies().get("tnt_alert");
        Assertions.assertTrue(p2.privateEnabled());
        Assertions.assertFalse(p2.privateAdminOnly());
        Assertions.assertTrue(p2.publicEnabled());
        Assertions.assertEquals("safety-alerts", p2.channelKey());
    }

    @Test
    public void testNullConfig_returnsEmptyPolicies() {
        Notifications ns = Notifications.from(null);
        Assertions.assertTrue(ns.policies().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, true, channel-a",
        "false, true, true, ''",
        "true, false, false, ops",
        "false, false, false, ''",
    })
    public void testNotifyPolicyCombinations(
            boolean privateEnabled, boolean privateAdminOnly, boolean publicEnabled, String channelKey) {
        YamlConfiguration cfg = new YamlConfiguration();
        String key = "test_event";
        cfg.set(key + ".private.enabled", privateEnabled);
        cfg.set(key + ".private.admin_only", privateAdminOnly);
        cfg.set(key + ".public.enabled", publicEnabled);
        cfg.set(key + ".channel_key", channelKey);

        Notifications ns = Notifications.from(cfg);
        NotifyPolicy p = ns.policies().get("test_event");
        Assertions.assertNotNull(p);
        Assertions.assertEquals(privateEnabled, p.privateEnabled());
        Assertions.assertEquals(privateAdminOnly, p.privateAdminOnly());
        Assertions.assertEquals(publicEnabled, p.publicEnabled());
        Assertions.assertEquals(channelKey, p.channelKey());
    }
}
