package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextStyle;
import java.util.List;
import org.junit.jupiter.api.Test;

/** v2 简写行记号：{@code **粗体**} / {@code <u>下划线</u>} / {@code [文字](url '悬停')} + 转义 + 容错降级。 */
class GuideLineMarkupTest {

    private static List<ContentItem> parse(String line) {
        GuideLineMarkup.Result result = GuideLineMarkup.parse(line, 1);
        return result.items();
    }

    private static TextItem onlyText(String line) {
        List<ContentItem> items = parse(line);
        assertEquals(1, items.size(), "应为单段文本: " + items);
        assertInstanceOf(TextItem.class, items.get(0));
        return (TextItem) items.get(0);
    }

    @Test
    void plainText_keepsContentAndTrailingBlankLines() {
        TextItem item = onlyText("欢迎新朋友");
        assertEquals("欢迎新朋友", item.content());
        assertEquals(TextStyle.NONE, item.style());
        assertEquals(1, item.blankLinesAfter());
    }

    @Test
    void blankLine_rendersAsEmptyLine() {
        TextItem item = onlyText("");
        assertEquals("", item.content());
        assertEquals(1, item.blankLinesAfter());
    }

    @Test
    void boldWholeLine() {
        TextItem item = onlyText("**相关链接**");
        assertEquals("相关链接", item.content());
        assertTrue(item.style().bold());
        assertFalse(item.style().underlined());
    }

    @Test
    void underlineWholeLine() {
        TextItem item = onlyText("<u>下划线</u>");
        assertEquals("下划线", item.content());
        assertTrue(item.style().underlined());
        assertFalse(item.style().bold());
    }

    @Test
    void boldAndUnderlineCanNest() {
        TextItem item = onlyText("**<u>加粗下划线</u>**");
        assertEquals("加粗下划线", item.content());
        assertTrue(item.style().bold());
        assertTrue(item.style().underlined());
    }

    @Test
    void inlineMixedStyles_splitIntoSegmentsOnSameLine() {
        List<ContentItem> items = parse("**重点**：请看这里");
        assertEquals(2, items.size(), "行内混排应拆段: " + items);
        TextItem first = (TextItem) items.get(0);
        TextItem second = (TextItem) items.get(1);
        assertEquals("重点", first.content());
        assertTrue(first.style().bold());
        assertEquals(0, first.blankLinesAfter(), "非末段不换行，保持同一视觉行");
        assertEquals("：请看这里", second.content());
        assertFalse(second.style().bold());
        assertEquals(1, second.blankLinesAfter());
    }

    @Test
    void linkWithHover() {
        List<ContentItem> items = parse("[服务器主页](https://orzmc.jokerhub.cn '点击前往主页')");
        assertEquals(1, items.size());
        LinkItem link = (LinkItem) items.get(0);
        assertEquals("服务器主页", link.content());
        assertEquals("https://orzmc.jokerhub.cn", link.url());
        assertEquals("点击前往主页", link.hoverText());
    }

    @Test
    void linkWithoutHover() {
        LinkItem link = (LinkItem) parse("[主页](https://orzmc.cn)").get(0);
        assertEquals("https://orzmc.cn", link.url());
        assertEquals("", link.hoverText());
    }

    @Test
    void linkWithDoubleQuotedHover() {
        LinkItem link = (LinkItem) parse("[主页](https://orzmc.cn \"点我\")").get(0);
        assertEquals("点我", link.hoverText());
    }

    @Test
    void linkUrlContainingParentheses() {
        LinkItem link = (LinkItem) parse("[wiki](https://a.com/(b))").get(0);
        assertEquals("https://a.com/(b)", link.url());
    }

    @Test
    void escapes_keepLiteralCharacters() {
        TextItem item = onlyText("\\*\\*不是粗体\\*\\*");
        assertEquals("**不是粗体**", item.content());
        assertFalse(item.style().bold());
    }

    @Test
    void unclosedBold_degradesToPlainTextWithIssue() {
        GuideLineMarkup.Result result = GuideLineMarkup.parse("**没闭合", 1);
        assertTrue(result.hasIssue());
        TextItem item = (TextItem) result.items().get(0);
        assertEquals("**没闭合", item.content());
        assertFalse(item.style().bold());
    }

    @Test
    void closingUnderlineWithoutOpen_degradesWithIssue() {
        GuideLineMarkup.Result result = GuideLineMarkup.parse("文字</u>", 1);
        assertTrue(result.hasIssue());
        assertEquals("文字</u>", ((TextItem) result.items().get(0)).content());
    }

    @Test
    void incompleteLink_degradesToPlainTextWithIssue() {
        GuideLineMarkup.Result missingParen = GuideLineMarkup.parse("[主页](https://orzmc.cn", 1);
        assertTrue(missingParen.hasIssue());
        assertEquals("[主页](https://orzmc.cn", ((TextItem) missingParen.items().get(0)).content());

        GuideLineMarkup.Result emptyUrl = GuideLineMarkup.parse("[主页]()", 1);
        assertTrue(emptyUrl.hasIssue());
        assertInstanceOf(TextItem.class, emptyUrl.items().get(0));

        GuideLineMarkup.Result bracketOnly = GuideLineMarkup.parse("[abc] 不是链接", 1);
        assertTrue(bracketOnly.hasIssue());
        assertEquals("[abc] 不是链接", ((TextItem) bracketOnly.items().get(0)).content());
    }

    @Test
    void trailingBlankLinesAreAppliedToLastSegmentOnly() {
        GuideLineMarkup.Result result = GuideLineMarkup.parse("**标题** 后续", 3);
        List<ContentItem> items = result.items();
        assertEquals(0, items.get(0).blankLinesAfter());
        assertEquals(3, items.get(items.size() - 1).blankLinesAfter());
    }
}
