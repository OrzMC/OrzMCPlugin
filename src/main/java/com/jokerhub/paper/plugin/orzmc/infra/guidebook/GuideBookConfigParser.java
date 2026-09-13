package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuidePage;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * guide_book.yml 解析器（纯函数，不读盘、不写盘、不抛异常）。
 *
 * <p>两种输入格式归一化到同一模型（{@link GuideBookConfig} 的页列表）：</p>
 * <ul>
 *   <li><b>v2 简化格式（推荐）</b>：{@code pages:} 下每项为一页，页内每行是字符串简写记号
 *       （见 {@link GuideLineMarkup}）或全写法 Map；</li>
 *   <li><b>v1 旧格式（兼容）</b>：{@code content:} 扁平列表 + {@code text:}/{@code link:} 包裹、
 *       {@code style:} 子段、{@code newline_count}、{@code page_break}。</li>
 * </ul>
 *
 * <p>任何非法输入都不会中断解析：条目按纯文本兜底或跳过，同时产出一条带「页号/行号」的 issue，
 * 由调用方写日志与配置健康检查——取代旧实现"日志说跳过、实际仍产出空条目"的行为。</p>
 */
public final class GuideBookConfigParser {

    private static final String DEFAULT_TITLE = "新手指南";
    private static final String DEFAULT_AUTHOR = "服务器";

    /** 解析结果：{@code issues} 为空表示完全干净；非空时内容仍可用（已降级兜底）。 */
    public record ParseResult(GuideBookConfig config, List<String> issues) {

        public ParseResult {
            issues = List.copyOf(issues);
        }

        public boolean hasIssues() {
            return !issues.isEmpty();
        }
    }

    public ParseResult parse(@Nullable FileConfiguration config) {
        List<String> issues = new ArrayList<>();
        if (config == null) {
            issues.add("guide_book.yml 未加载（文件缺失或读取失败），指南书不可用");
            return new ParseResult(new GuideBookConfig(true, DEFAULT_TITLE, DEFAULT_AUTHOR, List.of()), issues);
        }

        boolean enable = boolValue(config.get("enable"), true, "enable", issues);
        String title = stringValue(config.get("title"), DEFAULT_TITLE);
        String author = stringValue(config.get("author"), DEFAULT_AUTHOR);

        List<GuidePage> pages;
        if (config.isList("pages")) {
            pages = parsePages(config.getList("pages"), issues);
        } else if (config.isList("content")) {
            pages = parseLegacyContent(config.getList("content"), issues);
        } else if (config.get("pages") != null || config.get("content") != null) {
            issues.add("pages 与 content 都需为列表（当前类型不符），指南书将为空");
            pages = List.of();
        } else {
            issues.add("未配置 pages（v2 简化格式）或 content（旧格式），指南书将为空");
            pages = List.of();
        }
        return new ParseResult(new GuideBookConfig(enable, title, author, pages), issues);
    }

    // ------------------------------------------------------------------
    // v2：pages
    // ------------------------------------------------------------------

    private List<GuidePage> parsePages(List<?> rawPages, List<String> issues) {
        List<GuidePage> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < rawPages.size(); pageIndex++) {
            int pageNo = pageIndex + 1;
            Object rawPage = rawPages.get(pageIndex);
            if (!(rawPage instanceof List<?> lines)) {
                issues.add("第 " + pageNo + " 页不是列表：每页应为一个 '-' 列表，例如 \"- 第一行\"");
                continue;
            }
            List<ContentItem> items = new ArrayList<>();
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                items.addAll(parseLine(lines.get(lineIndex), "第 " + pageNo + " 页第 " + (lineIndex + 1) + " 行", issues));
            }
            pages.add(new GuidePage(items));
        }
        return pages;
    }

    // ------------------------------------------------------------------
    // v1：content（兼容旧格式）
    // ------------------------------------------------------------------

    private List<GuidePage> parseLegacyContent(List<?> rawContent, List<String> issues) {
        List<GuidePage> pages = new ArrayList<>();
        List<ContentItem> current = new ArrayList<>();
        for (int index = 0; index < rawContent.size(); index++) {
            Object rawItem = rawContent.get(index);
            String where = "第 " + (index + 1) + " 个内容项";
            current.addAll(parseLine(rawItem, where, issues));
            if (rawItem instanceof Map<?, ?> map && boolValue(pageBreakOf(map), false, where + " page_break", issues)) {
                pages.add(new GuidePage(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty() || pages.isEmpty()) {
            pages.add(new GuidePage(current));
        }
        return pages;
    }

    /**
     * v1 的 {@code page_break} 既可能写在条目外层，也可能写在 {@code text:}/{@code link:} 内层
     * （历史内置模板即后者），两种位置都要识别。
     */
    private static Object pageBreakOf(Map<?, ?> map) {
        Object outer = map.get("page_break");
        if (outer != null) {
            return outer;
        }
        for (String wrapper : List.of("text", "link")) {
            if (map.get(wrapper) instanceof Map<?, ?> inner) {
                Object innerValue = inner.get("page_break");
                if (innerValue != null) {
                    return innerValue;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 单行（简写字符串 / 全写法 Map）
    // ------------------------------------------------------------------

    private List<ContentItem> parseLine(@Nullable Object rawLine, String where, List<String> issues) {
        if (rawLine == null) {
            return List.of(TextItem.blank());
        }
        if (rawLine instanceof String text) {
            GuideLineMarkup.Result result = GuideLineMarkup.parse(text, 1);
            if (result.hasIssue()) {
                issues.add(where + ": " + result.issue());
            }
            return result.items();
        }
        if (rawLine instanceof Map<?, ?> map) {
            return parseFullForm(map, where, issues);
        }
        issues.add(where + ": 无法识别的行类型（应为文字字符串、text: 或 link:），已跳过");
        return List.of();
    }

    /** 全写法：{@code text: 文字} / {@code link: 文字} + 同级样式键，或 v1 的 {@code text: {content: …}} 包裹写法。 */
    private List<ContentItem> parseFullForm(Map<?, ?> map, String where, List<String> issues) {
        if (map.containsKey("text")) {
            Object rawText = map.get("text");
            Map<?, ?> inner = rawText instanceof Map<?, ?> innerMap ? innerMap : null;
            String content = stringValue(inner != null ? inner.get("content") : rawText, "");
            TextStyle style = styleOf(map, inner, where, issues);
            int blankLines = intValue(
                    inner != null ? inner.get("newline_count") : map.get("newline_count"),
                    1,
                    where + " newline_count",
                    issues);
            return List.of(new TextItem(content, style, blankLines));
        }
        if (map.containsKey("link")) {
            Object rawLink = map.get("link");
            Map<?, ?> inner = rawLink instanceof Map<?, ?> innerMap ? innerMap : null;
            String content = stringValue(inner != null ? inner.get("content") : rawLink, "");
            String url = stringValue(inner != null ? inner.get("url") : map.get("url"), "");
            Object rawHover = inner != null ? inner.get("hover_text") : map.get("hover_text");
            if (rawHover == null) {
                rawHover = map.get("hover");
            }
            String hover = stringValue(rawHover, "");
            TextStyle style = styleOf(map, inner, where, issues);
            int blankLines = intValue(
                    inner != null ? inner.get("newline_count") : map.get("newline_count"),
                    1,
                    where + " newline_count",
                    issues);
            if (url.isBlank()) {
                issues.add(where + ": 链接缺少 url，已按普通文字渲染（写法：link: 文字 + url: https://地址）");
                return List.of(new TextItem(content, style, blankLines));
            }
            return List.of(new LinkItem(content, url, hover, style, blankLines));
        }
        issues.add(where + ": 未知内容类型（应为 text: 或 link:），已跳过");
        return List.of();
    }

    private TextStyle styleOf(Map<?, ?> map, @Nullable Map<?, ?> inner, String where, List<String> issues) {
        // style 子段可能写在条目外层，也可能在 v1 的 text:/link: 内层（历史内置模板即后者）
        Map<?, ?> styleMap = inner != null && inner.get("style") instanceof Map<?, ?> innerStyle
                ? innerStyle
                : (map.get("style") instanceof Map<?, ?> outerStyle ? outerStyle : null);
        Object boldRaw = styleMap != null ? styleMap.get("bold") : map.get("bold");
        Object underlinedRaw = styleMap != null ? styleMap.get("underlined") : map.get("underlined");
        Object colorRaw = styleMap != null ? styleMap.get("color") : map.get("color");
        boolean bold = boolValue(boldRaw, false, where + " bold", issues);
        boolean underlined = boolValue(underlinedRaw, false, where + " underlined", issues);
        String color = stringValue(colorRaw, "");
        if (!color.isEmpty() && !GuideColorParser.isValid(color)) {
            issues.add(where + ": 颜色 '" + color + "' 不是命名色（如 red/AQUA）或 #RRGGBB，已忽略该颜色");
            color = "";
        }
        return new TextStyle(bold, underlined, color);
    }

    // ------------------------------------------------------------------
    // 类型安全取值（非法类型只告警，不抛异常）
    // ------------------------------------------------------------------

    private static boolean boolValue(@Nullable Object raw, boolean fallback, String where, List<String> issues) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text) {
            if (text.equalsIgnoreCase("true")) {
                return true;
            }
            if (text.equalsIgnoreCase("false")) {
                return false;
            }
        }
        issues.add(where + ": 需为 true/false，当前为 '" + raw + "'，已按 " + fallback + " 处理");
        return fallback;
    }

    private static int intValue(@Nullable Object raw, int fallback, String where, List<String> issues) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (raw instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                // 落到下方告警
            }
        }
        issues.add(where + ": 需为整数，当前为 '" + raw + "'，已按 " + fallback + " 处理");
        return fallback;
    }

    private static String stringValue(@Nullable Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof String text) {
            return text;
        }
        return String.valueOf(raw);
    }
}
