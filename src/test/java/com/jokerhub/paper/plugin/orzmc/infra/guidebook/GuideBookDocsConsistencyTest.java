package com.jokerhub.paper.plugin.orzmc.infra.guidebook;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * 文档 / 模板 / 迁移产物三者一致性（防「改了格式没改文档」）。
 *
 * <ul>
 *   <li>手册 `docs/guide-book.md` 的规范示例（{@code guide-book-example} 标记块）必须能被 v2 解析器解析；</li>
 *   <li>内置模板 `src/main/resources/guide_book.yml` 必须可解析且非空；</li>
 *   <li>手册与迁移产物头部注释必须覆盖 3 个记号与生效方式关键字（新手拿到的任一入口都自带速查）。</li>
 * </ul>
 */
class GuideBookDocsConsistencyTest {

    private static final Path MANUAL = Path.of("docs", "guide-book.md");
    private static final Path TEMPLATE = Path.of("src", "main", "resources", "guide_book.yml");
    private static final String START = "<!-- guide-book-example:start -->";
    private static final String END = "<!-- guide-book-example:end -->";

    private final GuideBookConfigParser parser = new GuideBookConfigParser();

    private static String read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), path + " 不存在（文档/模板被移动或删除？）");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static YamlConfiguration parseYaml(String text) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(text);
        return cfg;
    }

    /** 取出手册里 ```yaml 代码块包裹的规范示例。 */
    private static String manualExampleBlock(String manual) {
        int start = manual.indexOf(START);
        int end = manual.indexOf(END);
        assertTrue(start >= 0 && end > start, "手册缺少 " + START + " / " + END + " 标记块");
        String block = manual.substring(start + START.length(), end);
        int fenceStart = block.indexOf("```yaml");
        int fenceEnd = block.indexOf("```", fenceStart + 1);
        assertTrue(fenceStart >= 0 && fenceEnd > fenceStart, "标记块内应有 ```yaml 示例");
        return block.substring(fenceStart + "```yaml".length(), fenceEnd);
    }

    @Test
    void manualExample_parsesWithNoIssues() throws Exception {
        String example = manualExampleBlock(read(MANUAL));

        GuideBookConfigParser.ParseResult result = parser.parse(parseYaml(example));

        assertFalse(result.hasIssues(), "手册示例不应产生解析告警: " + result.issues());
        assertTrue(result.config().enable(), "手册示例应为启用状态");
        assertTrue(result.config().pages().size() >= 2, "手册示例应示范分页");
        assertTrue(result.config().hasContent());
    }

    @Test
    void shippedTemplate_parsesWithNoIssuesAndHasContent() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("guide_book.yml")) {
            assertNotNull(in, "内置模板 guide_book.yml 应在 classpath 中");
            YamlConfiguration cfg =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));

            GuideBookConfigParser.ParseResult result = parser.parse(cfg);

            assertFalse(result.hasIssues(), "内置模板不应产生解析告警: " + result.issues());
            assertTrue(result.config().hasContent(), "内置模板应有内容");
        }
    }

    @Test
    void manualAndMigrationHeader_coverMarkersAndReloadCommand() throws Exception {
        String manual = read(MANUAL);
        String header = GuideBookMigrator.HEADER;

        for (String token : List.of("**", "<u>", "[", "pages", "/orzmc config reload")) {
            assertTrue(manual.contains(token), "手册应覆盖记号/用法: " + token);
            assertTrue(header.contains(token), "迁移产物头部注释应覆盖记号/用法: " + token);
        }
    }

    @Test
    void manual_mentionsMigrationBackupAndLimits() throws Exception {
        String manual = read(MANUAL);

        for (String token : List.of("guide_book.yml.bak", "1024", "100 页", "content:")) {
            assertTrue(manual.contains(token), "手册应说明迁移/上限细节: " + token);
        }
    }

    @Test
    void templatePathReference_stillMatchesShippedResource() throws Exception {
        // 模板文件路径变更时，本测试与手册/迁移器头部注释需同步更新
        assertTrue(Files.isRegularFile(TEMPLATE), TEMPLATE + " 应存在");
        assertTrue(read(TEMPLATE).contains("pages:"), "内置模板应使用 v2 的 pages 结构");
    }
}
