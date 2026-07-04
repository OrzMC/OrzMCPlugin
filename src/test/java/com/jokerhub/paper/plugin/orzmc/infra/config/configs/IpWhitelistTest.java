package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class IpWhitelistTest {

    @Test
    void fromNull_returnsEmptyList() {
        IpWhitelist config = IpWhitelist.from(null);
        assertTrue(config.allowCountryCode().isEmpty());
    }

    @Test
    void fromEmpty_returnsEmptyList() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        IpWhitelist config = IpWhitelist.from(cfg);
        assertTrue(config.allowCountryCode().isEmpty());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        List<String> list = new ArrayList<>();
        list.add("CN");
        list.add("JP");
        list.add("US");
        when(cfg.get("allow_country_code")).thenReturn(list);

        IpWhitelist config = IpWhitelist.from(cfg);
        assertEquals(List.of("CN", "JP", "US"), config.allowCountryCode());
    }

    @Test
    void from_ignoresNullElements() {
        ConfigurationSection cfg = mock(ConfigurationSection.class);
        List<String> list = new ArrayList<>();
        list.add("CN");
        list.add(null);
        list.add("US");
        when(cfg.get("allow_country_code")).thenReturn(list);

        IpWhitelist config = IpWhitelist.from(cfg);
        assertEquals(List.of("CN", "US"), config.allowCountryCode());
    }
}
