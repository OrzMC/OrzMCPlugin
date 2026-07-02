package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BlacklistServiceTest {

    private ConfigService configService;
    private FileConfiguration fileConfig;
    private ConfigurationSection section;
    private BlacklistService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        fileConfig = mock(FileConfiguration.class);
        section = mock(ConfigurationSection.class);

        when(configService.getConfig("ip_blacklist")).thenReturn(fileConfig);
        when(fileConfig.getConfigurationSection("ip_blacklist")).thenReturn(section);

        service = new BlacklistService(configService);
    }

    // ---- exact matching ----

    @Test
    void exactMatch_blocksExactIp() {
        setupPatterns("192.168.1.1");
        assertTrue(service.isBlocked("192.168.1.1"));
    }

    @Test
    void exactMatch_allowsDifferentIp() {
        setupPatterns("192.168.1.1");
        assertFalse(service.isBlocked("192.168.1.2"));
    }

    // ---- CIDR matching ----

    @ParameterizedTest
    @CsvSource({
        "10.0.0.0/8,     10.0.0.1,      true",
        "10.0.0.0/8,     10.255.255.255, true",
        "10.0.0.0/8,     11.0.0.1,      false",
        "192.168.1.0/24, 192.168.1.100, true",
        "192.168.1.0/24, 192.168.2.1,   false",
        "0.0.0.0/0,      1.2.3.4,        true",
        "203.0.113.0/24, 203.0.113.1,   true",
    })
    void cidrMatch(String pattern, String ip, boolean expected) {
        setupPatterns(pattern);
        assertEquals(expected, service.isBlocked(ip));
    }

    // ---- wildcard matching ----

    @ParameterizedTest
    @CsvSource({
        "192.168.1.*,   192.168.1.100,   true",
        "192.168.1.*,   192.168.2.100,   false",
        "10.*,          10.0.0.1,        true",
        "10.*,          10.255.255.255,  true",
        "10.*,          11.0.0.1,        false",
        "203.0.113.*,   203.0.113.55,    true",
        "203.0.113.*,   203.0.114.55,    false",
    })
    void wildcardMatch(String pattern, String ip, boolean expected) {
        setupPatterns(pattern);
        assertEquals(expected, service.isBlocked(ip));
    }

    // ---- blacklist is empty by default ----

    @Test
    void emptyPatterns_allowsAll() {
        setupPatterns(); // no patterns
        assertFalse(service.isBlocked("1.2.3.4"));
        assertFalse(service.isBlocked("10.0.0.1"));
        assertFalse(service.isBlocked("192.168.1.1"));
    }

    // ---- null / empty IP ----

    @Test
    void nullIp_notBlocked() {
        assertFalse(service.isBlocked(null));
    }

    @Test
    void emptyIp_notBlocked() {
        assertFalse(service.isBlocked(""));
    }

    // ---- add / remove ----

    @Test
    void addPattern_increasesBlocked() {
        assertFalse(service.isBlocked("10.0.0.5"));
        service.add("10.0.0.0/8");
        assertTrue(service.isBlocked("10.0.0.5"));
    }

    @Test
    void removePattern_clearsBlocked() {
        setupPatterns("10.0.0.0/8");
        assertTrue(service.isBlocked("10.0.0.5"));
        service.remove("10.0.0.0/8");
        assertFalse(service.isBlocked("10.0.0.5"));
    }

    @Test
    void addDuplicate_noChange() {
        setupPatterns("1.2.3.4");
        service.add("1.2.3.4");
        assertEquals(1, service.getPatterns().size());
    }

    @Test
    void removeNonExistent_noError() {
        service.remove("nonexistent");
        assertTrue(service.getPatterns().isEmpty());
    }

    @Test
    void addNull_noChange() {
        service.add(null);
        assertTrue(service.getPatterns().isEmpty());
    }

    @Test
    void addEmpty_noChange() {
        service.add("");
        assertTrue(service.getPatterns().isEmpty());
    }

    // ---- getPatterns ----

    @Test
    void getPatterns_returnsAddedPatterns() {
        service.add("1.2.3.4");
        service.add("5.6.7.0/24");
        assertEquals(2, service.getPatterns().size());
        assertTrue(service.getPatterns().contains("1.2.3.4"));
        assertTrue(service.getPatterns().contains("5.6.7.0/24"));
    }

    @Test
    void getPatterns_reflectsReload() {
        setupPatterns("1.2.3.4");
        assertEquals(List.of("1.2.3.4"), service.getPatterns());
    }

    // ---- persist is called on add/remove ----

    @Test
    void add_callsSave() {
        when(fileConfig.get("ip_blacklist")).thenReturn(null);
        service.add("1.2.3.4");
        verify(fileConfig).set(eq("ip_blacklist"), anyList());
        verify(configService).saveConfig("ip_blacklist");
    }

    // ---- helper ----

    private void setupPatterns(String... patterns) {
        when(section.get("ip_blacklist")).thenReturn(java.util.List.of(patterns));
        service.reload();
    }
}
