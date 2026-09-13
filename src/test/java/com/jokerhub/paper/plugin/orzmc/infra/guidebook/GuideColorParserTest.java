package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

/** 颜色口径统一：命名色（大小写不敏感，含 trim）与 #RGB/#RRGGBB；非法值返回 null 不抛异常。 */
class GuideColorParserTest {

    @Test
    void namedColors_parsedCaseInsensitively() {
        assertEquals(NamedTextColor.RED, GuideColorParser.parse("red"));
        assertEquals(NamedTextColor.AQUA, GuideColorParser.parse("AQUA"));
        assertEquals(NamedTextColor.GOLD, GuideColorParser.parse(" gold "));
    }

    @Test
    void hexColors_parsed() {
        assertEquals(TextColor.fromHexString("#5555FF"), GuideColorParser.parse("#5555FF"));
        assertEquals(TextColor.fromHexString("#00FF00"), GuideColorParser.parse("#0f0"), "#RGB 简写应展开为 6 位");
    }

    @Test
    void invalidOrBlank_returnsNull() {
        assertNull(GuideColorParser.parse(null));
        assertNull(GuideColorParser.parse(""));
        assertNull(GuideColorParser.parse("   "));
        assertNull(GuideColorParser.parse("notacolor"));
        assertNull(GuideColorParser.parse("5555FF")); // 缺少 #
        assertNull(GuideColorParser.parse("#12345")); // 长度非法
        assertNull(GuideColorParser.parse("#gggggg")); // 非十六进制
    }

    @Test
    void isValid_matchesParse() {
        assertTrue(GuideColorParser.isValid("red"));
        assertTrue(GuideColorParser.isValid("#123456"));
        assertFalse(GuideColorParser.isValid("AQUA#"));
    }
}
