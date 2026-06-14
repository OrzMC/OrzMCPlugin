package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record NotifyPolicy(
        boolean privateEnabled, boolean privateAdminOnly, boolean publicEnabled, String channelKey) {

    public record Notifications(Map<String, NotifyPolicy> policies) {

        public static Notifications from(ConfigurationSection cfg) {
            Map<String, NotifyPolicy> policies = new HashMap<>();
            if (cfg == null) return new Notifications(policies);
            for (String key : cfg.getKeys(false)) {
                ConfigurationSection s = cfg.getConfigurationSection(key);
                if (s != null) {
                    boolean privateEnabled = s.getBoolean("private.enabled", false);
                    boolean privateAdminOnly = s.getBoolean("private.admin_only", true);
                    boolean publicEnabled = s.getBoolean("public.enabled", true);
                    String channelKey = s.getString("channel_key", "");
                    policies.put(key, new NotifyPolicy(privateEnabled, privateAdminOnly, publicEnabled, channelKey));
                }
            }
            return new Notifications(policies);
        }
    }
}
