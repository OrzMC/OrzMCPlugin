package com.jokerhub.paper.plugin.orzmc.infra.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NotificationsConfigTest {
    @Test
    public void testNotificationsMapping() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("notifications.whitelist_cleanup.private.enabled", true);
        cfg.set("notifications.whitelist_cleanup.private.admin_only", true);
        cfg.set("notifications.whitelist_cleanup.public.enabled", false);
        cfg.set("notifications.whitelist_cleanup.channel_key", "ops-alert");
        cfg.set("notifications.tnt_alert.private.enabled", true);
        cfg.set("notifications.tnt_alert.private.admin_only", false);
        cfg.set("notifications.tnt_alert.public.enabled", true);
        cfg.set("notifications.tnt_alert.channel_key", "safety-alerts");

        TypedConfigs.Notifications ns = TypedConfigs.Notifications.from(cfg);
        Assertions.assertTrue(ns.policies().containsKey("whitelist_cleanup"));
        TypedConfigs.NotifyPolicy p1 = ns.policies().get("whitelist_cleanup");
        Assertions.assertTrue(p1.privateEnabled());
        Assertions.assertTrue(p1.privateAdminOnly());
        Assertions.assertFalse(p1.publicEnabled());
        Assertions.assertEquals("ops-alert", p1.channelKey());

        TypedConfigs.NotifyPolicy p2 = ns.policies().get("tnt_alert");
        Assertions.assertTrue(p2.privateEnabled());
        Assertions.assertFalse(p2.privateAdminOnly());
        Assertions.assertTrue(p2.publicEnabled());
        Assertions.assertEquals("safety-alerts", p2.channelKey());
    }
}
