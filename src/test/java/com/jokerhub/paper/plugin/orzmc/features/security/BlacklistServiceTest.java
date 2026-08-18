package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BlacklistServiceTest {

    private ConfigService configService;
    private FileConfiguration fileConfig;
    private BlacklistService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        fileConfig = mock(FileConfiguration.class);

        when(configService.getConfig("ip_blacklist")).thenReturn(fileConfig);
        when(fileConfig.getStringList("ip_blacklist")).thenReturn(List.of());

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

    // ---- IPv6 精确匹配（文本形式规范化）----

    @Test
    void exactMatch_ipv6_canonicalFormsEqual() {
        setupPatterns("2001:db8::1");
        assertTrue(service.isBlocked("2001:db8::1"));
        assertTrue(service.isBlocked("2001:0db8:0:0:0:0:0:1"));
    }

    @Test
    void exactMatch_ipv6_allowsDifferent() {
        setupPatterns("2001:db8::1");
        assertFalse(service.isBlocked("2001:db8::2"));
    }

    // ---- IPv6 CIDR ----

    @ParameterizedTest
    @CsvSource({
        "2001:db8::/32,     2001:db8:1::5,   true",
        "2001:db8::/32,     2001:db9::1,     false",
        "fd00::/8,          fd12:3456::1,    true",
        "fd00::/8,          2001:db8::1,     false",
        "::/0,              ::1,             true",
        "::1/128,           ::1,             true",
        "::1/128,           ::2,             false",
    })
    void cidrMatch_ipv6(String pattern, String ip, boolean expected) {
        setupPatterns(pattern);
        assertEquals(expected, service.isBlocked(ip));
    }

    // ---- IPv4 / IPv6 族不匹配 ----

    @Test
    void cidrMatch_v4PatternVsV6Ip_notBlocked() {
        setupPatterns("10.0.0.0/8");
        assertFalse(service.isBlocked("2001:db8::1"));
    }

    @Test
    void cidrMatch_v6PatternVsV4Ip_notBlocked() {
        setupPatterns("2001:db8::/32");
        assertFalse(service.isBlocked("10.0.0.1"));
    }

    // ---- 前缀越界 ----

    @ParameterizedTest
    @CsvSource({
        "2001:db8::/129,  2001:db8::1,   false",
        "2001:db8::/-1,   2001:db8::1,   false",
    })
    void cidrMatch_invalidPrefix_notBlocked(String pattern, String ip, boolean expected) {
        setupPatterns(pattern);
        assertEquals(expected, service.isBlocked(ip));
    }

    // ---- matchedPattern（P2-4 封禁告警需命中规则）----

    @Test
    void matchedPattern_returnsHitPattern() {
        setupPatterns("1.2.3.4", "10.0.0.0/8", "2001:db8::/32");
        assertEquals("10.0.0.0/8", service.matchedPattern("10.1.2.3"));
        assertEquals("2001:db8::/32", service.matchedPattern("2001:db8:abcd::5"));
        assertEquals("1.2.3.4", service.matchedPattern("1.2.3.4"));
    }

    @Test
    void matchedPattern_noHit_returnsNull() {
        setupPatterns("1.2.3.4");
        assertNull(service.matchedPattern("5.6.7.8"));
        assertNull(service.matchedPattern(""));
        assertNull(service.matchedPattern(null));
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
        when(fileConfig.getStringList("ip_blacklist")).thenReturn(java.util.List.of(patterns));
        service.reload();
    }
}
