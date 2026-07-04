package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

class IpBlacklistTest {

    @Test
    void fromNull_returnsEmptyList() {
        IpBlacklist config = IpBlacklist.from(null);
        assertTrue(config.patterns().isEmpty());
    }

    @Test
    void fromEmpty_returnsEmptyList() {
        ConfigurationSection section = mock(ConfigurationSection.class);
        IpBlacklist config = IpBlacklist.from(section);
        assertTrue(config.patterns().isEmpty());
    }

    @Test
    void fromFullSection_returnsCorrectValues() {
        ConfigurationSection section = mock(ConfigurationSection.class);
        when(section.get("ip_blacklist")).thenReturn(List.of("192.168.1.1", "10.0.0.0/8"));

        IpBlacklist config = IpBlacklist.from(section);
        assertEquals(List.of("192.168.1.1", "10.0.0.0/8"), config.patterns());
    }

    @Test
    void from_ignoresNullElements() {
        ConfigurationSection section = mock(ConfigurationSection.class);
        List<String> list = new ArrayList<>();
        list.add("1.2.3.4");
        list.add(null);
        list.add("5.6.7.0/24");
        when(section.get("ip_blacklist")).thenReturn(list);

        IpBlacklist config = IpBlacklist.from(section);
        assertEquals(List.of("1.2.3.4", "5.6.7.0/24"), config.patterns());
    }
}
