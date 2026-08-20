package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Portals.PortalEntry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PortalsWriterTest {

    @Test
    void nullArguments_areNoop() {
        YamlConfiguration cfg = new YamlConfiguration();
        PortalsWriter.write(null, Map.of());
        PortalsWriter.write(cfg, null);
        assertFalse(cfg.contains("portals"), "null 入参应短路不写");
    }

    @Test
    void write_groupsEntriesByTargetAndEncodesDots() {
        YamlConfiguration cfg = new YamlConfiguration();
        Map<String, PortalEntry> entries = new LinkedHashMap<>();
        entries.put("world:100:64:200", new PortalEntry("hub.example.com:25565", "X"));
        entries.put("world:200:64:300", new PortalEntry("hub.example.com:25565", "Z"));

        PortalsWriter.write(cfg, entries);

        // 目标地址 '.' 编码为 '_'，中心坐标原样
        assertEquals("X", cfg.getString("portals.hub_example_com:25565.world:100:64:200"));
        assertEquals("Z", cfg.getString("portals.hub_example_com:25565.world:200:64:300"));
    }

    @Test
    void write_clearsOldPortalSection() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("portals.old_target.some:center", "X");

        PortalsWriter.write(cfg, Map.of("new:center", new PortalEntry("new.example.com:25565", "X")));

        assertFalse(cfg.contains("portals.old_target"), "旧 target 分组应被清除");
        assertEquals("X", cfg.getString("portals.new_example_com:25565.new:center"));
    }
}
