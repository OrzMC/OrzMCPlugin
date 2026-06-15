package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public record IpWhitelist(List<String> allowCountryCode) {

    public static IpWhitelist from(ConfigurationSection cfg) {
        List<String> list = new ArrayList<>();
        if (cfg == null) return new IpWhitelist(list);
        Object raw = cfg.get("allow_country_code");
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) list.add(String.valueOf(o));
            }
        }
        return new IpWhitelist(list);
    }
}
