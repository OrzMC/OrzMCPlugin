package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerNameRuleTest {

    @ParameterizedTest
    @CsvSource({
        "player, true", // 裸 player 关键词
        "player list, true",
        "Player exact foo, true", // 大小写不敏感
        "-player exact foo, true",
        "exact foo, true",
        "PREFIX bot_, true", // 匹配类型首词大小写不敏感
        "Regex .*, true",
        "contains a, true",
        "1.2.3.4, false", // 纯 IP
        "10.0.0.0/8, false", // CIDR
        "192.168.*.*, false", // 通配符
        "-1.2.3.4, false" // IP 移除简写（去 `-` 后首词非匹配类型）
    })
    void looksLikePlayerRuleSyntax(String raw, boolean expected) {
        assertEquals(expected, PlayerNameRule.looksLikePlayerRuleSyntax(raw), "raw=" + raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1.2.3.4", "server.example.com"})
    void looksLikePlayerRuleSyntax_nonRuleInput_returnsFalse(String raw) {
        assertFalse(PlayerNameRule.looksLikePlayerRuleSyntax(raw));
    }

    @Test
    void looksLikePlayerRuleSyntax_null_returnsFalse() {
        assertFalse(PlayerNameRule.looksLikePlayerRuleSyntax(null));
    }

    @Test
    void looksLikePlayerRuleSyntax_colonJoinedForm_detected() {
        // exact:foo、prefix_bot 冒号/粘连形式：首词类型词也是玩家名规则语法，
        // 防 `$d exact:foo` / `/blacklist add exact:foo` 落入 IP 添加存一条死规则并假「已添加」。
        assertTrue(PlayerNameRule.looksLikePlayerRuleSyntax("exact:foo"));
        assertTrue(PlayerNameRule.looksLikePlayerRuleSyntax("prefix:bot_"));
        assertTrue(PlayerNameRule.looksLikePlayerRuleSyntax("contains:admin"));
        // 真实 IPv6 字面量冒号前缀是数字，MatchType.from 为 null，不受影响
        assertFalse(PlayerNameRule.looksLikePlayerRuleSyntax("2001:db8::1"));
    }

    @Test
    void parse_invalidType_invalid() {
        PlayerNameRule.ParsedRule parsed = PlayerNameRule.parse("bogus", "foo");
        assertFalse(parsed.valid());
        assertNull(parsed.type());
    }

    @Test
    void parse_invalidRegex_invalid() {
        PlayerNameRule.ParsedRule parsed = PlayerNameRule.parse("regex", "[");
        assertFalse(parsed.valid());
    }

    @Test
    void matches_regexUsesFind_semantics() {
        // REGEX 用 find()：未锚定的正则即「包含」语义（regex admin 命中名字含 admin 的玩家）；
        // 需要整名匹配时显式写 ^...$。
        PlayerNameRule rule = PlayerNameRule.of(PlayerNameRule.MatchType.REGEX, "admin");
        assertTrue(rule.matches("the_admin_x"));
        assertFalse(rule.matches("alice"));
        PlayerNameRule anchored = PlayerNameRule.of(PlayerNameRule.MatchType.REGEX, "^bot\\d+$");
        assertTrue(anchored.matches("BOT42"));
        assertFalse(anchored.matches("bot1x"));
    }

    @Test
    void equalsAndHashCode_caseInsensitiveValue() {
        // 与 AccessRuleService.sameRule（equalsIgnoreCase 去重）口径一致，避免
        // equals/hashCode 与去重判定对仅大小写不同的规则互相矛盾。
        PlayerNameRule a = PlayerNameRule.of(PlayerNameRule.MatchType.PREFIX, "bot_");
        PlayerNameRule b = PlayerNameRule.of(PlayerNameRule.MatchType.PREFIX, "BOT_");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        PlayerNameRule c = PlayerNameRule.of(PlayerNameRule.MatchType.PREFIX, "bot");
        assertNotEquals(a, c);
        assertNotEquals(a, PlayerNameRule.of(PlayerNameRule.MatchType.EXACT, "bot_"));
    }
}
