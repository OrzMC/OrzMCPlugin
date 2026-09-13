package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.ContentItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuidePage;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.LinkItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextItem;
import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.TextStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * guide_book.yml 旧格式（v1 {@code content:}）→ v2 简化格式（{@code pages:}）的一次性启动迁移。
 *
 * <p>{@code guide_book.yml} 是**运行时数据文件**，按 {@code docs/dev/config-schema-governance.md}
 * 永不纳入 {@code ConfigSchema} 自动迁移；格式升级只能走代码内专门迁移，即本类。</p>
 *
 * <p>安全约束（do-no-harm）：</p>
 * <ol>
 *   <li><b>只迁移旧格式</b>：文件含 {@code content} 且不含 {@code pages}；已经是 v2 → 零动作（幂等，不重写不备份）；</li>
 *   <li><b>迁移前回读校验</b>：新文本重新解析后必须与迁移前模型完全相等（书名/作者/开关/逐页逐行），否则放弃迁移；</li>
 *   <li><b>任何失败都保留原文件</b>：YAML 解析失败、解析后无内容、校验不一致、写入异常 → 原文件不动，只写日志；</li>
 *   <li><b>先备份后写</b>：原文件复制为 {@code guide_book.yml.bak}（只保留最近一次），再以「同目录 tmp + 原子改名」写入。</li>
 * </ol>
 */
public final class GuideBookMigrator {

    /** 迁移结果。 */
    public enum Outcome {
        /** 无需迁移（已是 v2 / 无 content 段 / 文件不存在）。 */
        NOT_NEEDED,
        /** 迁移完成（已备份并写入新格式）。 */
        MIGRATED,
        /** 检测到旧格式但放弃迁移，原文件保留。 */
        SKIPPED_INVALID,
        /** IO 异常，原文件保留。 */
        FAILED
    }

    public record Result(Outcome outcome, String detail) {}

    /** 迁移后文件头部速查注释（与 docs/guide-book.md 的速查表同口径）。 */
    public static final String HEADER = String.join(
            "\n",
            List.of(
                    "# ==================================================",
                    "#         Guide Book 内容配置（v2 简化格式）",
                    "# ==================================================",
                    "# 【3 分钟上手】一行 = 一句话；一页 = 一个列表；改完执行 /orzmc config reload 即生效。",
                    "#   pages:",
                    "#     -                     # ← 一页",
                    "#       - 普通文字",
                    "#       - \"\"                # 空一行",
                    "#       - \"**粗体**\"         # **…** = 粗体",
                    "#       - \"<u>下划线</u>\"     # <u>…</u> = 下划线（可与 ** 叠加）",
                    "#       - \"[文字](https://地址 '悬停提示')\"   # 链接，悬停提示可省略",
                    "#     -                     # ← 第二页（想翻页就再起一个列表）",
                    "#       - 第二页文字",
                    "# 【需要颜色/精细控制】用全写法（可与简写混用）：",
                    "#     - text: 文字",
                    "#       bold: true",
                    "#       color: \"AQUA\"        # 命名色（red/AQUA/gold…）或 #RRGGBB",
                    "#       newline_count: 2     # 这段后面空几行（默认 1）",
                    "# 【完整说明】docs/guide-book.md",
                    "# 【本文件来源】由插件启动时从旧 content: 格式自动迁移生成（原文件见 guide_book.yml.bak）。",
                    ""));

    /** 简写行里不能出现、否则改用全写法的字符（链接文本/悬停）。 */
    private static final String LINK_UNSAFE_CHARS = "\\*<>[]\"'()";

    /** 文本简写需要转义的记号字符。 */
    private static final char[] TEXT_ESCAPE_CHARS = {'\\', '*', '<', '['};

    private final GuideBookConfigParser parser = new GuideBookConfigParser();

    /** 检测并迁移；不抛异常（失败返回结果并由日志记录）。 */
    public Result migrate(Path file, Logger logger) {
        if (file == null || !Files.isRegularFile(file)) {
            return new Result(Outcome.NOT_NEEDED, "guide_book.yml 不存在");
        }
        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return failed(logger, "读取 guide_book.yml 失败: " + e.getMessage());
        }

        YamlConfiguration disk = new YamlConfiguration();
        try {
            disk.loadFromString(raw);
        } catch (Exception e) {
            return new Result(Outcome.SKIPPED_INVALID, "guide_book.yml YAML 解析失败，保留原文件（请检查缩进）: " + e.getMessage());
        }
        if (disk.isList("pages")) {
            return new Result(Outcome.NOT_NEEDED, "已是 v2 格式，无需迁移");
        }
        if (!disk.isList("content")) {
            return new Result(Outcome.NOT_NEEDED, "未检测到旧格式 content 段，无需迁移");
        }

        GuideBookConfigParser.ParseResult before = parser.parse(disk);
        GuideBookConfig config = before.config();
        if (!config.hasContent() || config.pages().stream().anyMatch(GuidePage::isEmpty)) {
            return new Result(Outcome.SKIPPED_INVALID, "旧格式解析后无可迁移内容，保留原文件");
        }

        String migrated;
        try {
            migrated = renderV2(config);
        } catch (RuntimeException e) {
            return failed(logger, "生成 v2 内容失败: " + e);
        }
        if (!roundTripEquals(migrated, config)) {
            return new Result(Outcome.SKIPPED_INVALID, "迁移结果回读校验不一致，已放弃迁移并保留原文件");
        }

        try {
            backup(file);
            writeAtomically(file, migrated);
        } catch (IOException e) {
            return failed(logger, "写入 guide_book.yml 失败: " + e.getMessage());
        }
        return new Result(
                Outcome.MIGRATED,
                "已迁移为 v2 格式（" + config.pages().size() + " 页 / " + itemCount(config) + " 行），原文件备份为 " + file.getFileName()
                        + ".bak");
    }

    // ------------------------------------------------------------------
    // 渲染 v2 文本
    // ------------------------------------------------------------------

    /** 把归一化后的模型渲染为 v2 文本（简写优先，颜色等复杂项用全写法）。 */
    public String renderV2(GuideBookConfig config) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        out.append("enable: ").append(config.enable()).append('\n');
        out.append("title: ").append(yamlString(config.title())).append('\n');
        out.append("author: ").append(yamlString(config.author())).append('\n');
        out.append("pages:\n");
        List<GuidePage> pages = config.pages();
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            out.append("  - # ===== 第 ").append(pageIndex + 1).append(" 页 =====\n");
            for (ContentItem item : pages.get(pageIndex).items()) {
                out.append(renderItem(item));
            }
        }
        return out.toString();
    }

    private String renderItem(ContentItem item) {
        if (item instanceof LinkItem link) {
            String shorthand = shorthandLink(link);
            if (shorthand != null) {
                return "    - " + yamlString(shorthand) + "\n";
            }
        } else if (item instanceof TextItem text) {
            String shorthand = shorthandText(text);
            if (shorthand != null) {
                return "    - " + yamlString(shorthand) + "\n";
            }
        }
        return renderFullForm(item);
    }

    /**
     * 简写文本行（含 {@code **}/{@code <u>} 包裹）；以下情形返回 {@code null} 改用全写法：
     * 带颜色、行后换行数非默认 1（简写固定为 1）、内容为空白（空行语义/前后空格无法无损表达）。
     */
    private String shorthandText(TextItem item) {
        TextStyle style = item.style();
        if (style.hasColor() || item.blankLinesAfter() != 1) {
            return null;
        }
        if (item.content().isBlank()) {
            return style.equals(TextStyle.NONE) && item.content().isEmpty() ? "" : null;
        }
        String content = markupEscape(item.content());
        if (!style.bold() && !style.underlined()) {
            return content;
        }
        String inner = style.underlined() ? "<u>" + content + "</u>" : content;
        return style.bold() ? "**" + inner + "**" : inner;
    }

    /** 简写链接行；行后换行数非默认 1、样式非空或文本/悬停/地址含记号字符时返回 {@code null}（改用全写法）。 */
    private String shorthandLink(LinkItem item) {
        if (!item.style().equals(TextStyle.NONE) || item.blankLinesAfter() != 1) {
            return null;
        }
        if (item.content().isEmpty() || !item.hasUrl()) {
            return null;
        }
        for (String unsafe : List.of(item.content(), item.hoverText(), item.url())) {
            for (char c : LINK_UNSAFE_CHARS.toCharArray()) {
                if (unsafe.indexOf(c) >= 0) {
                    return null;
                }
            }
            if (unsafe.indexOf(' ') >= 0) {
                return null;
            }
        }
        String hover = item.hoverText().isEmpty() ? "" : " '" + item.hoverText() + "'";
        return "[" + item.content() + "](" + item.url() + hover + ")";
    }

    /** 全写法（颜色 / 含记号字符 / 链接样式等场景）。 */
    private String renderFullForm(ContentItem item) {
        StringBuilder out = new StringBuilder();
        if (item instanceof LinkItem link) {
            out.append("    - link: ").append(yamlString(link.content())).append('\n');
            out.append("      url: ").append(yamlString(link.url())).append('\n');
            if (!link.hoverText().isEmpty()) {
                out.append("      hover: ").append(yamlString(link.hoverText())).append('\n');
            }
        } else {
            out.append("    - text: ").append(yamlString(item.content())).append('\n');
        }
        TextStyle style = item.style();
        if (style.bold()) {
            out.append("      bold: true\n");
        }
        if (style.underlined()) {
            out.append("      underlined: true\n");
        }
        if (style.hasColor()) {
            out.append("      color: ").append(yamlString(style.color())).append('\n');
        }
        if (item.blankLinesAfter() != 1) {
            out.append("      newline_count: ").append(item.blankLinesAfter()).append('\n');
        }
        return out.toString();
    }

    /** 文本记号转义：只转义真的会被解析成记号的字符，保证回读后与原文一致。 */
    private static String markupEscape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            for (char escape : TEXT_ESCAPE_CHARS) {
                if (c == escape) {
                    out.append('\\');
                    break;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** YAML 双引号标量（转义 {@code \} 与 {@code "}；换行转义为 {@code \n}）。 */
    private static String yamlString(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 2).append('"');
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    // ------------------------------------------------------------------
    // 校验 / 文件操作
    // ------------------------------------------------------------------

    private boolean roundTripEquals(String migratedText, GuideBookConfig expected) {
        try {
            YamlConfiguration roundTrip = new YamlConfiguration();
            roundTrip.loadFromString(migratedText);
            return parser.parse(roundTrip).config().equals(expected);
        } catch (Exception e) {
            return false;
        }
    }

    private void backup(Path file) throws IOException {
        Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
    }

    /** 同目录 tmp + 原子改名（跨文件系统降级为 REPLACE_EXISTING，与 ConfigManager 落盘策略一致）。 */
    private void writeAtomically(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static int itemCount(GuideBookConfig config) {
        return config.pages().stream().mapToInt(page -> page.items().size()).sum();
    }

    private Result failed(Logger logger, String detail) {
        logger.warning("guide_book.yml 迁移失败: " + detail);
        return new Result(Outcome.FAILED, detail);
    }
}
