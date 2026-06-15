package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record CommandPolicies(Map<String, CommandPolicy> policies) {

    public static CommandPolicies from(ConfigurationSection cfg) {
        Map<String, CommandPolicy> policies = new HashMap<>();
        if (cfg == null) return new CommandPolicies(policies);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection s = cfg.getConfigurationSection(key);
            if (s != null) {
                int cooldown = s.getInt("cooldown_secs", 0);
                boolean adminOnly = s.getBoolean("admin_only", false);
                policies.put(key, new CommandPolicy(cooldown, adminOnly));
            }
        }
        return new CommandPolicies(policies);
    }
}
