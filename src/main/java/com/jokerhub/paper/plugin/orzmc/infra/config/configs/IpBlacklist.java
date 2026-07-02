package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public record IpBlacklist(List<String> patterns) {

    public static IpBlacklist from(ConfigurationSection section) {
        List<String> list = new ArrayList<>();
        if (section == null) return new IpBlacklist(list);
        Object raw = section.get("ip_blacklist");
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) list.add(String.valueOf(o));
            }
        }
        return new IpBlacklist(list);
    }
}
