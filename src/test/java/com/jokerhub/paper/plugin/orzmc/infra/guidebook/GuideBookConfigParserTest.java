package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuidePage;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextItem;
import java.io.StringReader;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * guide_book.yml 解析：v2 简化格式（pages + 行记号）、v1 旧格式兼容（content + style/page_break/newline_count）、
 * 以及非法输入的可定位告警（页号/行号）与纯文本兜底。
 */
class GuideBookConfigParserTest {

    private final GuideBookConfigParser parser = new GuideBookConfigParser();

    private GuideBookConfigParser.ParseResult parse(String yaml) {
        return parser.parse(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    // ------------------------------------------------------------------
    // v2 简化格式
    // ------------------------------------------------------------------

    @Test
    void v2_parsesPagesAndMarkup() {
        var result = parse("""
                enable: true
                title: "服务器指南"
                author: "OrzMC"
                pages:
                  - # 第 1 页
                    - 欢迎新朋友
                    - ""
                    - "**相关链接**"
                    - "[服务器主页](https://orzmc.jokerhub.cn '点击前往主页')"
                  - # 第 2 页
                    - 第二页文字
                """);

        assertFalse(result.hasIssues(), "不应有告警: " + result.issues());
        GuideBookConfig config = result.config();
        assertTrue(config.enable());
        assertEquals("服务器指南", config.title());
        assertEquals("OrzMC", config.author());
        assertEquals(2, config.pages().size());

        List<ContentItem> first = config.pages().get(0).items();
        assertEquals(4, first.size());
        assertEquals("欢迎新朋友", ((TextItem) first.get(0)).content());
        assertEquals("", first.get(1).content());
        assertTrue(first.get(2).style().bold());
        LinkItem link = (LinkItem) first.get(3);
        assertEquals("服务器主页", link.content());
        assertEquals("https://orzmc.jokerhub.cn", link.url());
        assertEquals("点击前往主页", link.hoverText());
        assertEquals("第二页文字", ((TextItem) config.pages().get(1).items().get(0)).content());
    }

    @Test
    void v2_fullFormInsidePages_supportsStyleAndColor() {
        var result = parse("""
                pages:
                  -
                    - text: 加粗标题
                      bold: true
                      color: "AQUA"
                      newline_count: 2
                    - link: 官网
                      url: https://example.com
                      hover: 点我
                """);

        assertFalse(result.hasIssues(), "不应有告警: " + result.issues());
        List<ContentItem> items = result.config().pages().get(0).items();
        TextItem text = (TextItem) items.get(0);
        assertTrue(text.style().bold());
        assertEquals("AQUA", text.style().color());
        assertEquals(2, text.blankLinesAfter());
        LinkItem link = (LinkItem) items.get(1);
        assertEquals("https://example.com", link.url());
        assertEquals("点我", link.hoverText());
    }

    @Test
    void v2_invalidColorAndTypes_areReportedWithLocation() {
        var result = parse("""
                pages:
                  -
                    - text: 标题
                      color: notacolor
                      bold: "yes"
                """);

        assertTrue(result.config().pages().get(0).items().get(0) instanceof TextItem);
        TextItem item = (TextItem) result.config().pages().get(0).items().get(0);
        assertEquals("", item.style().color(), "非法颜色应被忽略");
        assertFalse(item.style().bold(), "非法布尔值应回退默认");
        assertEquals(2, result.issues().size(), "颜色 + 布尔值各一条: " + result.issues());
        assertTrue(
                result.issues().get(0).contains("第 1 页第 1 行"), result.issues().toString());
    }

    @Test
    void v2_unknownLineType_isSkippedWithLocation() {
        var result = parse("""
                pages:
                  -
                    - foo: bar
                    - 正常文字
                """);

        assertEquals(1, result.config().pages().get(0).items().size(), "非法行不应产出空条目");
        assertEquals("正常文字", result.config().pages().get(0).items().get(0).content());
        assertEquals(1, result.issues().size());
        assertTrue(
                result.issues().get(0).contains("第 1 页第 1 行"), result.issues().toString());
    }

    @Test
    void v2_pageNotAList_isReported() {
        var result = parse("""
                pages:
                  - 不是列表
                """);

        assertTrue(result.config().pages().isEmpty());
        assertTrue(result.issues().get(0).contains("第 1 页"), result.issues().toString());
    }

    // ------------------------------------------------------------------
    // v1 旧格式兼容
    // ------------------------------------------------------------------

    @Test
    void v1_legacyContent_isNormalizedToPages() {
        var result = parse("""
                enable: true
                title: "服务器指南"
                author: "OrzMC"
                content:
                  - text:
                      content: "欢迎"
                      newline_count: 2
                      style:
                        bold: true
                        color: "AQUA"
                  - link:
                      content: "官网"
                      url: "https://example.com"
                      hover_text: "点击访问"
                      page_break: true
                  - text:
                      content: "第二页"
                """);

        assertFalse(result.hasIssues(), "旧格式不应产生告警: " + result.issues());
        GuideBookConfig config = result.config();
        assertEquals(2, config.pages().size(), "page_break 应切分为两页");

        List<ContentItem> first = config.pages().get(0).items();
        assertEquals(2, first.size());
        TextItem text = (TextItem) first.get(0);
        assertEquals("欢迎", text.content());
        assertEquals(2, text.blankLinesAfter(), "newline_count 映射为行后换行数");
        assertTrue(text.style().bold());
        assertEquals("AQUA", text.style().color());
        LinkItem link = (LinkItem) first.get(1);
        assertEquals("点击访问", link.hoverText());

        assertEquals("第二页", ((TextItem) config.pages().get(1).items().get(0)).content());
    }

    @Test
    void v1_missingUrl_fallsBackToPlainTextWithIssue() {
        var result = parse("""
                content:
                  - link:
                      content: "没有地址"
                """);

        assertInstanceOf(TextItem.class, result.config().pages().get(0).items().get(0));
        assertTrue(result.issues().get(0).contains("缺少 url"), result.issues().toString());
    }

    // ------------------------------------------------------------------
    // 默认值与整体容错
    // ------------------------------------------------------------------

    @Test
    void defaults_appliedWhenMissing() {
        var result = parse("pages: []\n");

        GuideBookConfig config = result.config();
        assertTrue(config.enable(), "enable 默认应为 true");
        assertEquals("新手指南", config.title());
        assertEquals("服务器", config.author());
        assertFalse(config.hasContent());
    }

    @Test
    void neitherPagesNorContent_reportedAsIssue() {
        var result = parse("enable: true\n");

        assertFalse(result.config().hasContent());
        assertTrue(result.issues().get(0).contains("pages"), result.issues().toString());
    }

    @Test
    void wrongTypeForPages_reportedAsIssue() {
        var result = parse("pages: hello\n");

        assertFalse(result.config().hasContent());
        assertTrue(result.issues().get(0).contains("列表"), result.issues().toString());
    }

    @Test
    void nullConfig_returnsUnavailableConfigWithIssue() {
        var result = parser.parse((FileConfiguration) null);

        assertFalse(result.config().hasContent());
        assertEquals(1, result.issues().size());
        assertTrue(result.issues().get(0).contains("未加载"), result.issues().toString());
    }

    @Test
    void emptyPage_keptAsPageButHasNoContent() {
        var result = parse("pages:\n  - []\n");

        assertEquals(1, result.config().pages().size());
        assertTrue(result.config().pages().get(0).isEmpty());
        assertFalse(result.config().hasContent());
    }

    @Test
    void pagesAreImmutable() {
        var result = parse("pages:\n  - - 文字\n");
        GuidePage page = result.config().pages().get(0);
        assertThrows(UnsupportedOperationException.class, () -> page.items().add(TextItem.blank()));
    }
}
