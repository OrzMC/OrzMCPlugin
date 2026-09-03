package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record WhitelistKickMessage(String title, List<WhitelistKickMessageItem> ups) {

    public record WhitelistKickMessageItem(String name, String platform) {}

    @SuppressWarnings("unchecked")
    public static WhitelistKickMessage from(ConfigurationSection cfg) {
        String title = "";
        List<WhitelistKickMessageItem> items = new ArrayList<>();
        if (cfg == null) {
            return new WhitelistKickMessage(title, items);
        }
        ConfigurationSection section = cfg.getConfigurationSection("kick_message");
        if (section == null) {
            return new WhitelistKickMessage(title, items);
        }
        title = section.getString("title", "");
        List<Map<?, ?>> ups = section.getMapList("ups");
        if (ups != null) {
            for (Map<?, ?> raw : ups) {
                if (raw == null) continue;
                String name = raw.get("name") == null ? "" : String.valueOf(raw.get("name"));
                String platform = raw.get("platform") == null ? "" : String.valueOf(raw.get("platform"));
                if (name.isEmpty() && platform.isEmpty()) continue;
                items.add(new WhitelistKickMessageItem(name, platform));
            }
        }
        return new WhitelistKickMessage(title, items);
    }
}
