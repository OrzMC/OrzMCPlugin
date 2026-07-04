package com.jokerhub.paper.plugin.orzmc.infra.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Axis;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalPersistenceTest {

    private ConfigService configService;
    private PortalPersistence persistence;
    private Map<String, PortalService.PortalDef> portalCenters;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        persistence = new PortalPersistence(configService, Logger.getLogger("test"));
        portalCenters = new LinkedHashMap<>();
    }

    @Test
    void load_emptyPortals_returnsEmpty() {
        YamlConfiguration cfg = new YamlConfiguration();
        when(configService.getConfig("portals")).thenReturn(cfg);

        persistence.load(portalCenters, def -> {});
        assertTrue(portalCenters.isEmpty());
    }

    @Test
    void load_withPortals_populatesMap() {
        YamlConfiguration cfg = new YamlConfiguration();
        // 使用 ConfigurationSection 避免 set(map) 的类型转换问题
        ConfigurationSection ps = cfg.createSection("portals");
        ps.createSection("example_com").set("world:0:64:0", "Z");
        when(configService.getConfig("portals")).thenReturn(cfg);

        persistence.load(portalCenters, def -> {});
        assertFalse(portalCenters.isEmpty());
    }

    @Test
    void load_skipsInvalidPortals() {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection ps = cfg.createSection("portals");
        ps.createSection("test").set("bad:coords", "X");
        when(configService.getConfig("portals")).thenReturn(cfg);

        persistence.load(portalCenters, def -> {});
        assertTrue(portalCenters.isEmpty());
    }

    @Test
    void save_writesToConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        when(configService.getConfig("portals")).thenReturn(cfg);

        portalCenters.put("test", new PortalService.PortalDef("world", 0, 64, 0, Axis.Z, "example.com"));
        persistence.save(portalCenters);

        verify(configService).saveConfig("portals");
    }

    @Test
    void save_nullConfig_doesNothing() {
        when(configService.getConfig("portals")).thenReturn(null);

        persistence.save(portalCenters);
        verify(configService, never()).saveConfig(anyString());
    }
}
