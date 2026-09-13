package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.infra.guidebook.models.GuideBookConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 旧格式（v1 {@code content:}）→ v2（{@code pages:}）自动迁移：golden 一致性、备份、幂等、
 * 失败时保留原文件、以及复杂内容（记号字符/颜色/换行数/链接样式）无损。
 */
class GuideBookMigratorTest {

    /** 历史内置模板（v1 写法：text/link 双层包裹、style 子段、page_break 在 link 内层）。 */
    private static final String LEGACY_V1 = """
            title: '新手指南'
            author: '腐竹'
            content:
              - text:
                  content: '欢迎新朋友来到我的世界！'
                  newline_count: 2
              - text:
                  content: '相关链接'
                  style:
                    bold: true
              - link:
                  content: '服务器主页'
                  url: 'https://orzmc.jokerhub.cn'
                  hover_text: '点击前往主页'
              - link:
                  content: 'MC插件使用百科书'
                  url: 'https://mineplugin.org/'
                  hover_text: 点击跳转插件百科
                  page_break: true
              - text:
                  content: '第二页内容'
            """;

    private final GuideBookMigrator migrator = new GuideBookMigrator();
    private final GuideBookConfigParser parser = new GuideBookConfigParser();
    private final Logger logger = Logger.getLogger("GuideBookMigratorTest");

    private Path write(Path dir, String content) throws IOException {
        Path file = dir.resolve("guide_book.yml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private GuideBookConfig parse(String text) {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.loadFromString(text);
            return parser.parse(cfg).config();
        } catch (Exception e) {
            throw new IllegalStateException("测试样例 YAML 非法: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // 正常迁移
    // ------------------------------------------------------------------

    @Test
    void legacyFile_isMigratedWithBackupAndIdenticalContent(@TempDir Path dir) throws IOException {
        Path file = write(dir, LEGACY_V1);

        GuideBookMigrator.Result result = migrator.migrate(file, logger);

        assertEquals(GuideBookMigrator.Outcome.MIGRATED, result.outcome(), result.detail());
        String migrated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(migrated.contains("pages:"), "迁移后应为 v2 格式");
        assertFalse(migrated.contains("\ncontent:"), "旧 content 段应被替换");

        // 备份保留原始 v1 文件
        Path backup = dir.resolve("guide_book.yml.bak");
        assertTrue(Files.exists(backup));
        assertEquals(LEGACY_V1, Files.readString(backup, StandardCharsets.UTF_8));

        // golden：迁移前后模型逐页逐行一致
        assertEquals(parse(LEGACY_V1).pages(), parse(migrated).pages());
        assertEquals(parse(LEGACY_V1).title(), parse(migrated).title());
        assertEquals(parse(LEGACY_V1).author(), parse(migrated).author());
    }

    @Test
    void legacyFileWithTrickyContent_preservesEverything(@TempDir Path dir) throws IOException {
        String tricky = """
                title: "标题"
                author: "作者"
                content:
                  - text:
                      content: "含记号 * 与 [方括号] 与 \\\\ 反斜杠"
                      newline_count: 3
                  - text:
                      content: "带颜色"
                      style:
                        color: "AQUA"
                        underlined: true
                  - link:
                      content: "样式链接"
                      url: "https://example.com/a(b)"
                      hover_text: "带 空格 的悬停"
                      style:
                        bold: true
                  - text:
                      content: ""
                """;
        Path file = write(dir, tricky);

        GuideBookMigrator.Result result = migrator.migrate(file, logger);

        assertEquals(GuideBookMigrator.Outcome.MIGRATED, result.outcome(), result.detail());
        assertEquals(
                parse(tricky).pages(),
                parse(Files.readString(file, StandardCharsets.UTF_8)).pages());
    }

    @Test
    void renderV2_roundTripsAllItemShapes() throws Exception {
        GuideBookConfig config = parse("""
                enable: false
                title: "指南"
                author: "服主"
                pages:
                  - - 普通文字
                    - ""
                    - "**粗体**"
                    - "<u>下划线</u>"
                    - "**<u>两者</u>**"
                    - text: 带颜色
                      color: "gold"
                      newline_count: 0
                    - link: 链接
                      url: "https://example.com"
                      hover: "悬停"
                    - link: 带样式链接
                      url: "https://example.com/b(c)"
                      bold: true
                """);

        String rendered = migrator.renderV2(config);

        assertEquals(config, parse(rendered), "渲染结果应能无损回读:\n" + rendered);
        assertTrue(rendered.contains("**粗体**"), rendered);
        assertTrue(rendered.contains("<u>下划线</u>"), rendered);
        assertTrue(rendered.contains("[链接](https://example.com '悬停')"), rendered);
    }

    // ------------------------------------------------------------------
    // 幂等 / 跳过 / 失败保留
    // ------------------------------------------------------------------

    @Test
    void alreadyV2_isNoOpAndDoesNotTouchFile(@TempDir Path dir) throws IOException {
        String v2 = "pages:\n  - - 已经是新格式\n";
        Path file = write(dir, v2);

        GuideBookMigrator.Result result = migrator.migrate(file, logger);

        assertEquals(GuideBookMigrator.Outcome.NOT_NEEDED, result.outcome());
        assertEquals(v2, Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(Files.exists(dir.resolve("guide_book.yml.bak")), "无需迁移时不应产生备份");
    }

    @Test
    void secondRunAfterMigration_isNoOpAndKeepsOriginalBackup(@TempDir Path dir) throws IOException {
        Path file = write(dir, LEGACY_V1);
        assertEquals(
                GuideBookMigrator.Outcome.MIGRATED,
                migrator.migrate(file, logger).outcome());
        String afterFirst = Files.readString(file, StandardCharsets.UTF_8);

        GuideBookMigrator.Result second = migrator.migrate(file, logger);

        assertEquals(GuideBookMigrator.Outcome.NOT_NEEDED, second.outcome());
        assertEquals(afterFirst, Files.readString(file, StandardCharsets.UTF_8), "二次启动不应再次改写");
        assertEquals(LEGACY_V1, Files.readString(dir.resolve("guide_book.yml.bak"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(dir.resolve("guide_book.yml.tmp")), "临时文件应被清理");
    }

    @Test
    void noContentSection_isNoOp(@TempDir Path dir) throws IOException {
        String noContent = "enable: true\ntitle: '指南'\n";
        Path file = write(dir, noContent);

        GuideBookMigrator.Result result = migrator.migrate(file, logger);

        assertEquals(GuideBookMigrator.Outcome.NOT_NEEDED, result.outcome());
        assertEquals(noContent, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void invalidYaml_keepsOriginalFile(@TempDir Path dir) throws IOException {
        String broken = "content:\n  - text: [未闭合\n";
        Path file = write(dir, broken);

        GuideBookMigrator.Result result = migrator.migrate(file, logger);

        assertEquals(GuideBookMigrator.Outcome.SKIPPED_INVALID, result.outcome(), result.detail());
        assertEquals(broken, Files.readString(file, StandardCharsets.UTF_8), "解析失败必须保留原文件");
        assertFalse(Files.exists(dir.resolve("guide_book.yml.bak")));
    }

    @Test
    void legacyWithoutUsableContent_isSkippedAndKept(@TempDir Path dir) throws IOException {
        String unusable = "content: []\n";
        Path file = write(dir, unusable);

        GuideBookMigrator.Result result = migrator.migrate(file, logger);

        assertEquals(GuideBookMigrator.Outcome.SKIPPED_INVALID, result.outcome());
        assertEquals(unusable, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void missingFile_isNoOp(@TempDir Path dir) {
        GuideBookMigrator.Result result = migrator.migrate(dir.resolve("absent.yml"), logger);

        assertEquals(GuideBookMigrator.Outcome.NOT_NEEDED, result.outcome());
    }

    // ------------------------------------------------------------------
    // 头部速查注释（迁移产物仍自带教程）
    // ------------------------------------------------------------------

    @Test
    void migratedFile_keepsCheatSheetHeader(@TempDir Path dir) throws IOException {
        Path file = write(dir, LEGACY_V1);

        migrator.migrate(file, logger);

        String migrated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(migrated.startsWith("# =================================================="), migrated);
        assertTrue(migrated.contains("一行 = 一句话"), migrated);
        assertTrue(migrated.contains("**粗体**"), migrated);
        assertTrue(migrated.contains("<u>下划线</u>"), migrated);
        assertTrue(migrated.contains("/orzmc config reload"), migrated);
        assertTrue(migrated.contains("docs/guide-book.md"), migrated);
    }
}
