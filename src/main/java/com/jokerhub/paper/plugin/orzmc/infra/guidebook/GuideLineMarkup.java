package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextStyle;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * v2 简写行记号解析（纯函数，无 Bukkit 依赖）。
 *
 * <p>支持三种记号，其余一律按普通文字：</p>
 * <ul>
 *   <li>{@code **文字**} —— 粗体（可整行，也可行内局部）</li>
 *   <li>{@code <u>文字</u>} —— 下划线（可与 {@code **} 嵌套）</li>
 *   <li>{@code [文字](https://url '悬停提示')} —— 链接（悬停可省略；整行必须只有链接）</li>
 * </ul>
 *
 * <p>转义：{@code \*}、{@code \[}、{@code \]}、{@code \(}、{@code \)}、{@code \<}、{@code \>}、{@code \\}
 * 表示字面量。记号不成对/链接格式错误时不猜测用户意图：整行按纯文本渲染并返回一条 issue（内容不丢）。</p>
 *
 * <p>行内混排返回多段 {@link ContentItem}：仅最后一段带 {@code blankLinesAfter}，其余为 0（保持同一视觉行）。</p>
 */
public final class GuideLineMarkup {

    /** 解析结果：items 至少一项；issue 非空表示已降级为纯文本。 */
    public record Result(List<ContentItem> items, @Nullable String issue) {

        public boolean hasIssue() {
            return issue != null;
        }
    }

    private static final String BOLD = "**";
    private static final String UNDERLINE_OPEN = "<u>";
    private static final String UNDERLINE_CLOSE = "</u>";

    private GuideLineMarkup() {}

    /** 解析一行简写内容；{@code blankLinesAfter} 作用于本行最后一段。 */
    public static Result parse(String raw, int blankLinesAfter) {
        String line = raw == null ? "" : raw;
        if (line.isBlank()) {
            return new Result(List.of(withBlankLines(TextItem.blank(), blankLinesAfter)), null);
        }

        LinkAttempt link = tryParseLink(line, blankLinesAfter);
        if (link != null) {
            return new Result(List.of(link.item()), link.issue());
        }

        return parseStyledText(line, blankLinesAfter);
    }

    // ------------------------------------------------------------------
    // 链接
    // ------------------------------------------------------------------

    private record LinkAttempt(ContentItem item, @Nullable String issue) {}

    @Nullable
    private static LinkAttempt tryParseLink(String line, int blankLinesAfter) {
        if (!line.startsWith("[")) {
            return null;
        }
        int closeBracket = line.indexOf(']');
        if (closeBracket < 0 || !line.startsWith("](", closeBracket)) {
            // 以 [ 开头但不是链接写法：按普通文字，附带提示（可能是想写链接）
            return new LinkAttempt(
                    new TextItem(line, TextStyle.NONE, blankLinesAfter),
                    "以 '[' 开头的行不是合法链接，链接写法应为 [文字](https://地址 '悬停提示')；本行已按普通文字渲染");
        }
        if (!line.endsWith(")")) {
            return new LinkAttempt(new TextItem(line, TextStyle.NONE, blankLinesAfter), "链接缺少右括号 ')'，本行已按普通文字渲染");
        }
        String text = line.substring(1, closeBracket);
        String target = line.substring(closeBracket + 2, line.length() - 1).trim();
        if (target.isEmpty()) {
            return new LinkAttempt(new TextItem(line, TextStyle.NONE, blankLinesAfter), "链接缺少地址，写法应为 [文字](https://地址)");
        }
        String url = target;
        String hover = "";
        int space = target.indexOf(' ');
        if (space > 0) {
            url = target.substring(0, space).trim();
            hover = target.substring(space + 1).trim();
            if (hover.length() >= 2
                    && ((hover.startsWith("'") && hover.endsWith("'"))
                            || (hover.startsWith("\"") && hover.endsWith("\"")))) {
                hover = hover.substring(1, hover.length() - 1);
            }
        }
        if (text.isEmpty()) {
            return new LinkAttempt(
                    new TextItem(line, TextStyle.NONE, blankLinesAfter), "链接缺少显示文字，写法应为 [文字](https://地址)");
        }
        return new LinkAttempt(new LinkItem(text, url, hover, TextStyle.NONE, blankLinesAfter), null);
    }

    // ------------------------------------------------------------------
    // 粗体 / 下划线
    // ------------------------------------------------------------------

    private static Result parseStyledText(String line, int blankLinesAfter) {
        List<ContentItem> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean bold = false;
        boolean underlined = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                buffer.append(line.charAt(i + 1));
                i += 2;
                continue;
            }
            if (line.startsWith(BOLD, i)) {
                if (buffer.length() > 0) {
                    segments.add(new TextItem(buffer.toString(), new TextStyle(bold, underlined, ""), 0));
                    buffer.setLength(0);
                }
                bold = !bold;
                i += BOLD.length();
                continue;
            }
            if (line.startsWith(UNDERLINE_OPEN, i)) {
                if (buffer.length() > 0) {
                    segments.add(new TextItem(buffer.toString(), new TextStyle(bold, underlined, ""), 0));
                    buffer.setLength(0);
                }
                underlined = true;
                i += UNDERLINE_OPEN.length();
                continue;
            }
            if (line.startsWith(UNDERLINE_CLOSE, i)) {
                if (!underlined) {
                    return degraded(line, blankLinesAfter, "下划线闭合标记 </u> 没有对应的 <u>");
                }
                if (buffer.length() > 0) {
                    segments.add(new TextItem(buffer.toString(), new TextStyle(bold, underlined, ""), 0));
                    buffer.setLength(0);
                }
                underlined = false;
                i += UNDERLINE_CLOSE.length();
                continue;
            }
            buffer.append(c);
            i++;
        }
        if (bold || underlined) {
            return degraded(line, blankLinesAfter, "粗体 '**' 或下划线 '<u>' 标记未闭合，本行已按普通文字渲染");
        }
        if (buffer.length() > 0) {
            segments.add(new TextItem(buffer.toString(), new TextStyle(false, false, ""), 0));
        }
        if (segments.isEmpty()) {
            return new Result(List.of(withBlankLines(TextItem.blank(), blankLinesAfter)), null);
        }
        List<ContentItem> result = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            result.add(
                    index == segments.size() - 1
                            ? withBlankLines(segments.get(index), blankLinesAfter)
                            : segments.get(index));
        }
        return new Result(result, null);
    }

    /** 记号不成对：整行按纯文本渲染（同时解开转义，内容不丢）。 */
    private static Result degraded(String line, int blankLinesAfter, String issue) {
        TextItem plain = new TextItem(unescape(line), TextStyle.NONE, 0);
        return new Result(List.of(withBlankLines(plain, blankLinesAfter)), issue);
    }

    /** 把本行「之后追加的换行数」落到该行最后一段上（行内混排时前面的段为 0）。 */
    private static ContentItem withBlankLines(ContentItem item, int blankLinesAfter) {
        if (item instanceof TextItem text) {
            return new TextItem(text.content(), text.style(), blankLinesAfter);
        }
        LinkItem link = (LinkItem) item;
        return new LinkItem(link.content(), link.url(), link.hoverText(), link.style(), blankLinesAfter);
    }

    private static String unescape(String line) {
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                out.append(line.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
