package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccessRuleServiceTest {

    private ConfigService configService;
    private FileConfiguration fileConfig;
    private AccessRuleService service;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        fileConfig = mock(FileConfiguration.class);

        when(configService.getConfig("access_rules")).thenReturn(fileConfig);
        when(fileConfig.getStringList("ip_blacklist")).thenReturn(List.of());
        doReturn(List.of()).when(fileConfig).getList("player_name_rules");
        // persist 现在经 updateConfig 原子落盘：模拟同步块内对 fileConfig 执行 set
        when(configService.updateConfig(eq("access_rules"), any())).thenAnswer(inv -> {
            inv.getArgument(1, Consumer.class).accept(fileConfig);
            return true;
        });

        service = new AccessRuleService(configService);
    }

    // ---- IP exact matching ----

    @Test
    void ipExactMatch_blocksExactIp() {
        setupIpPatterns("192.168.1.1");
        assertTrue(service.isIpBlocked("192.168.1.1"));
    }

    @Test
    void ipExactMatch_allowsDifferentIp() {
        setupIpPatterns("192.168.1.1");
        assertFalse(service.isIpBlocked("192.168.1.2"));
    }

    // ---- IP CIDR matching ----

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
    void ipCidrMatch(String pattern, String ip, boolean expected) {
        setupIpPatterns(pattern);
        assertEquals(expected, service.isIpBlocked(ip));
    }

    // ---- IP wildcard matching ----

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
    void ipWildcardMatch(String pattern, String ip, boolean expected) {
        setupIpPatterns(pattern);
        assertEquals(expected, service.isIpBlocked(ip));
    }

    // ---- IP wildcard strictness ----

    @Test
    void ipWildcardMatch_rejectsInvalidOctet() {
        setupIpPatterns("10.*");
        assertFalse(service.isIpBlocked("10.999.999")); // 非法 octet > 255
        assertFalse(service.isIpBlocked("10.256.0.1"));
        assertFalse(service.isIpBlocked("10.0.0.abc")); // 非数字段
        assertFalse(service.isIpBlocked("10.1.2.3.4")); // 超 4 段
    }

    @Test
    void ipWildcardMatch_middleWildcard() {
        setupIpPatterns("10.*.1");
        assertTrue(service.isIpBlocked("10.5.6.1"));
        assertFalse(service.isIpBlocked("10.5.1.2"));
    }

    @Test
    void ipWildcardMatch_ipv4MappedIpv6_blocks() {
        // 双栈服务器 prelogin 常见 ::ffff:a.b.c.d 形态：还原 IPv4 后按通配匹配，
        // 与 exact/CIDR（InetAddress 规范化命中）口径一致，避免 mapped 形式静默绕过通配黑名单。
        setupIpPatterns("192.168.1.*");
        assertTrue(service.isIpBlocked("::ffff:192.168.1.50"));
        assertFalse(service.isIpBlocked("::ffff:192.168.2.50"));
    }

    // ---- IPv6 ----

    @Test
    void ipExactMatch_ipv6_canonicalFormsEqual() {
        setupIpPatterns("2001:db8::1");
        assertTrue(service.isIpBlocked("2001:db8::1"));
        assertTrue(service.isIpBlocked("2001:0db8:0:0:0:0:0:1"));
    }

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
    void ipCidrMatch_ipv6(String pattern, String ip, boolean expected) {
        setupIpPatterns(pattern);
        assertEquals(expected, service.isIpBlocked(ip));
    }

    @Test
    void ipCidrMatch_v4PatternVsV6Ip_notBlocked() {
        setupIpPatterns("10.0.0.0/8");
        assertFalse(service.isIpBlocked("2001:db8::1"));
    }

    @Test
    void ipCidrMatch_v6PatternVsV4Ip_notBlocked() {
        setupIpPatterns("2001:db8::/32");
        assertFalse(service.isIpBlocked("10.0.0.1"));
    }

    // ---- IP matched pattern ----

    @Test
    void matchedIpPattern_returnsHitPattern() {
        setupIpPatterns("1.2.3.4", "10.0.0.0/8", "2001:db8::/32");
        assertEquals("10.0.0.0/8", service.matchedIpPattern("10.1.2.3"));
        assertEquals("2001:db8::/32", service.matchedIpPattern("2001:db8:abcd::5"));
        assertEquals("1.2.3.4", service.matchedIpPattern("1.2.3.4"));
        assertNull(service.matchedIpPattern("5.6.7.8"));
    }

    // ---- IP add/remove ----

    @Test
    void addIpPattern_increasesBlocked() {
        assertFalse(service.isIpBlocked("10.0.0.5"));
        service.addIpPattern("10.0.0.0/8");
        assertTrue(service.isIpBlocked("10.0.0.5"));
        service.addIpPattern("10.0.0.0/8");
        assertEquals(1, service.getIpPatterns().size());
    }

    @Test
    void addIpPattern_returnsTrue_whenAdded() {
        assertTrue(service.addIpPattern("10.0.0.0/8"));
    }

    @Test
    void addIpPattern_duplicate_returnsFalse() {
        assertTrue(service.addIpPattern("10.0.0.0/8"));
        assertFalse(service.addIpPattern("10.0.0.0/8"));
        assertEquals(1, service.getIpPatterns().size());
    }

    @Test
    void addIpPattern_nullOrBlank_returnsFalse() {
        assertFalse(service.addIpPattern(null));
        assertFalse(service.addIpPattern(""));
        assertFalse(service.addIpPattern("   "));
        assertTrue(service.getIpPatterns().isEmpty());
    }

    @Test
    void addIpPattern_trimsTrailingSpace() {
        // greedyString 保留尾随空格：服务层归一化后入库，避免带空格规则永不命中
        assertTrue(service.addIpPattern("1.2.3.4 "));
        assertEquals(List.of("1.2.3.4"), service.getIpPatterns());
        assertTrue(service.isIpBlocked("1.2.3.4"));
    }

    @Test
    void removeIpPattern_trimsTrailingSpace() {
        service.addIpPattern("1.2.3.4");
        assertTrue(service.removeIpPattern(" 1.2.3.4 "));
        assertTrue(service.getIpPatterns().isEmpty());
    }

    @Test
    void addIpPattern_ipv6CanonicalVariant_dedups() {
        assertTrue(service.addIpPattern("2001:db8::1"));
        // 规范等价变体（大小写/前导零/压缩）视为同一条，不再重复入库
        assertFalse(service.addIpPattern("2001:0db8:0:0:0:0:0:1"));
        assertFalse(service.addIpPattern("2001:DB8::1"));
        assertEquals(1, service.getIpPatterns().size());
    }

    @Test
    void removeIpPattern_ipv6CanonicalVariant_removes() {
        service.addIpPattern("2001:db8::1");
        assertTrue(service.removeIpPattern("2001:0db8:0:0:0:0:0:1"));
        assertTrue(service.getIpPatterns().isEmpty());
        assertFalse(service.isIpBlocked("2001:db8::1"));
    }

    @Test
    void removeIpPattern_clearsBlocked() {
        setupIpPatterns("10.0.0.0/8");
        service.removeIpPattern("10.0.0.0/8");
        assertFalse(service.isIpBlocked("10.0.0.5"));
        service.removeIpPattern("nonexistent");
        assertTrue(service.getIpPatterns().isEmpty());
    }

    @Test
    void removeIpPattern_present_returnsTrue() {
        // P3：移除确实存在 → 返回 true，供命令侧报「已移除」
        setupIpPatterns("10.0.0.0/8");
        assertTrue(service.removeIpPattern("10.0.0.0/8"));
        assertFalse(service.isIpBlocked("10.0.0.5"));
    }

    @Test
    void removeIpPattern_missing_returnsFalse() {
        // P3：移除不存在 → 返回 false，供命令侧报「未找到」而非假成功
        setupIpPatterns("10.0.0.0/8");
        assertFalse(service.removeIpPattern("1.2.3.4"));
        assertFalse(service.getIpPatterns().isEmpty());
    }

    @Test
    void removeIpPattern_nullOrEmpty_returnsFalse() {
        assertFalse(service.removeIpPattern(null));
        assertFalse(service.removeIpPattern(""));
    }

    @Test
    void addNullIpPattern_noChange() {
        service.addIpPattern(null);
        service.addIpPattern("");
        assertTrue(service.getIpPatterns().isEmpty());
    }

    @Test
    void addIpPattern_persists() {
        service.addIpPattern("1.2.3.4");
        verify(fileConfig).set(eq("ip_blacklist"), anyList());
        verify(configService).updateConfig(eq("access_rules"), any());
    }

    @Test
    void reloadAfterAdd_keepsPersistedRule() {
        // 真实 YamlConfiguration 走通「add → persist → reload」回环：证明 add 与 reload 不互失更新
        YamlConfiguration real = new YamlConfiguration();
        when(configService.getConfig("access_rules")).thenReturn(real);
        when(configService.updateConfig(eq("access_rules"), any())).thenAnswer(inv -> {
            inv.getArgument(1, Consumer.class).accept(real);
            return true;
        });
        AccessRuleService svc = new AccessRuleService(configService);
        svc.addIpPattern("10.0.0.0/8");
        svc.reload();
        assertTrue(svc.isIpBlocked("10.0.0.5"));
    }

    @Test
    void persistFailure_logsWarning_ruleStillInMemory() {
        // P3-3：updateConfig 返回 false（配置未注册或写入失败）→ 显式告警，内存规则仍生效
        java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
        when(configService.updateConfig(eq("access_rules"), any())).thenReturn(false);
        AccessRuleService svc = new AccessRuleService(configService, logger);

        svc.addIpPattern("9.9.9.9");

        assertTrue(svc.isIpBlocked("9.9.9.9")); // 内存规则已生效
        verify(logger).warning(anyString()); // 落盘失败已告警
    }

    // ---- player name rules ----

    @Test
    void playerNameRule_exactMatchesIgnoringCase() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.EXACT, "Steve");
        assertTrue(service.isPlayerNameBlocked("steve"));
        assertFalse(service.isPlayerNameBlocked("steve2"));
    }

    @Test
    void playerNameRule_prefixSuffixContains() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_");
        service.addPlayerNameRule(PlayerNameRule.MatchType.SUFFIX, "_test");
        service.addPlayerNameRule(PlayerNameRule.MatchType.CONTAINS, "admin");
        assertTrue(service.isPlayerNameBlocked("Bot_Alice"));
        assertTrue(service.isPlayerNameBlocked("Alice_Test"));
        assertTrue(service.isPlayerNameBlocked("the_admin_x"));
        assertFalse(service.isPlayerNameBlocked("Alice"));
    }

    @Test
    void playerNameRule_globMatchesWildcards() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.GLOB, "Steve*");
        assertTrue(service.isPlayerNameBlocked("steve2026"));
        assertFalse(service.isPlayerNameBlocked("alice"));
    }

    @Test
    void playerNameRule_regexMatchesIgnoringCase() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.REGEX, "^bot\\d+$");
        assertTrue(service.isPlayerNameBlocked("BOT42"));
        assertFalse(service.isPlayerNameBlocked("botx"));
    }

    @Test
    void playerNameRule_invalidRegexNotAdded() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.REGEX, "[");
        assertTrue(service.getPlayerNameRules().isEmpty());
    }

    @Test
    void playerNameRule_matchedRule_returnsRule() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_");
        PlayerNameRule hit = service.matchedPlayerNameRule("Bot_Alice");
        assertNotNull(hit);
        assertEquals(PlayerNameRule.MatchType.PREFIX, hit.type());
        assertEquals("bot_", hit.value());
    }

    @Test
    void playerNameRule_duplicateAndRemove() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_");
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "BOT_");
        assertEquals(1, service.getPlayerNameRules().size());
        service.removePlayerNameRule(PlayerNameRule.MatchType.PREFIX, "BOT_");
        assertTrue(service.getPlayerNameRules().isEmpty());
    }

    @Test
    void addPlayerNameRule_returnsTrue_whenAdded() {
        assertTrue(service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_"));
    }

    @Test
    void addPlayerNameRule_duplicate_returnsFalse() {
        assertTrue(service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_"));
        // 大小写不敏感去重 → 未新增，返回 false（供命令侧区分「已添加」与「已存在」）
        assertFalse(service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "BOT_"));
        assertEquals(1, service.getPlayerNameRules().size());
    }

    @Test
    void addPlayerNameRule_nullOrBlank_returnsFalse() {
        assertFalse(service.addPlayerNameRule(null, "bot_"));
        assertFalse(service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, null));
        assertFalse(service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "  "));
        assertTrue(service.getPlayerNameRules().isEmpty());
    }

    @Test
    void addPlayerNameRule_trimsValue() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, " bot_ ");
        PlayerNameRule rule = service.getPlayerNameRules().get(0);
        assertEquals("bot_", rule.value());
    }

    @Test
    void removePlayerNameRule_trimsValue() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_");
        assertTrue(service.removePlayerNameRule(PlayerNameRule.MatchType.PREFIX, " BOT_ "));
        assertTrue(service.getPlayerNameRules().isEmpty());
    }

    @Test
    void removePlayerNameRule_present_returnsTrue() {
        // P3：按类型+值移除，大小写不敏感命中 → 返回 true
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_");
        assertTrue(service.removePlayerNameRule(PlayerNameRule.MatchType.PREFIX, "BOT_"));
        assertTrue(service.getPlayerNameRules().isEmpty());
    }

    @Test
    void removePlayerNameRule_missing_returnsFalse() {
        // P3：不存在 → 返回 false，不改变现有规则
        service.addPlayerNameRule(PlayerNameRule.MatchType.PREFIX, "bot_");
        assertFalse(service.removePlayerNameRule(PlayerNameRule.MatchType.SUFFIX, "_alt"));
        assertEquals(1, service.getPlayerNameRules().size());
    }

    @Test
    void playerNameRule_persists() {
        service.addPlayerNameRule(PlayerNameRule.MatchType.SUFFIX, "_alt");
        verify(fileConfig).set(eq("player_name_rules"), anyList());
        verify(configService).updateConfig(eq("access_rules"), any());
    }

    @Test
    void playerNameRules_reloadFromMaps() {
        doReturn(List.of(Map.of("type", "prefix", "value", "bot_")))
                .when(fileConfig)
                .getList("player_name_rules");
        service.reload();
        assertEquals(1, service.getPlayerNameRules().size());
        assertTrue(service.isPlayerNameBlocked("Bot_Alice"));
    }

    // ---- helpers ----

    private void setupIpPatterns(String... patterns) {
        when(fileConfig.getStringList("ip_blacklist")).thenReturn(List.of(patterns));
        service.reload();
    }
}
