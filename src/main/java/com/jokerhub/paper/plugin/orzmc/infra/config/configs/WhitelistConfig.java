package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public record WhitelistConfig(boolean forceWhitelist, int cleanupInactiveDays, int paginationDelayTicks) {

    public static WhitelistConfig from(ConfigurationSection cfg) {
        if (cfg == null) return new WhitelistConfig(true, 90, 5);
        boolean forceWhitelist = cfg.getBoolean("force_whitelist", true);
        int cleanupInactiveDays = cfg.getInt("cleanup_inactive_days", 90);
        int paginationDelayTicks = cfg.getInt("pagination_delay_ticks", 5);
        return new WhitelistConfig(forceWhitelist, cleanupInactiveDays, paginationDelayTicks);
    }
}
