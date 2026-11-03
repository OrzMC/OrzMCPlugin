package com.jokerhub.paper.plugin.orzmc.infra.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 文案调用点护栏（源码级）：{@code msg("key", Map.of("a", …, "b", …))} 必须把语言包里该 key 的
 * <b>{@code {占位符}} 全部填上</b>，且 key 必须存在于内置语言包。
 *
 * <p><b>为什么需要</b>：{@link I18nCatalogConsistencyTest} 只保证「各语言包之间」key/占位符一致，
 * 不保证「代码调用点」填齐——{@code TemplateRenderer.render} 对未提供的占位符是<b>原样保留</b>，
 * 于是 {@code {total}} 这类漏传会直接把 {@code "（共 {total} 页）"} 发到玩家群里
 * （2026-11 分页提示真实踩过）。本测试把这类问题挡在 CI。</p>
 *
 * <p>扫描范围限于「字面量 key + 字面量 Map.of 变量名」的调用（占绝大多数）；变量名动态拼装或
 * 变量 map 的调用跳过（无法静态判定，交由运行时 {@code warnOnce} 兜底）。</p>
 */
class I18nCallSitePlaceholderTest {

    private static final Path REPO = Path.of("").toAbsolutePath();
    private static final Path MAIN_JAVA = REPO.resolve("src/main/java");
    private static final Path ZH_PACK = REPO.resolve("src/main/resources/messages/messages_zh-CN.yml");

    /** {@code msg("key", Map.of("a", x, "b", y))} / {@code msg("key", java.util.Map.of(...))} */
    private static final Pattern MSG_WITH_MAP = Pattern.compile(
            "\\.msg\\(\\s*\"([a-zA-Z0-9_.\\-]+)\"\\s*,\\s*(?:java\\.util\\.)?Map\\.of\\(([^;]*?)\\)\\s*\\)",
            Pattern.DOTALL);

    /** {@code msg("key")} / {@code msg(lang, "key")}：无变量的调用点，用于 key 存在性检查 */
    private static final Pattern MSG_LITERAL_KEY =
            Pattern.compile("\\.msg\\(\\s*(?:[^,\")]+\\s*,\\s*)?\"([a-zA-Z0-9_.\\-]+)\"");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private static final Pattern VAR_NAME = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*,");

    @Test
    void everyCallSiteProvidesAllPlaceholdersOfItsKey() throws IOException {
        Map<String, String> catalog = readCatalog(ZH_PACK);
        List<String> problems = new ArrayList<>();

        for (Path file : javaSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = MSG_WITH_MAP.matcher(source);
            while (m.find()) {
                String key = m.group(1);
                String template = catalog.get(key);
                if (template == null) {
                    continue; // key 存在性由下一个用例负责，这里只查占位符
                }
                Set<String> provided = new LinkedHashSet<>();
                Matcher v = VAR_NAME.matcher(m.group(2));
                while (v.find()) {
                    provided.add(v.group(1));
                }
                Set<String> required = placeholders(template);
                Set<String> missing = new LinkedHashSet<>(required);
                missing.removeAll(provided);
                if (!missing.isEmpty()) {
                    problems.add(file.getFileName() + " key=" + key + " 未提供的占位符=" + missing);
                }
            }
        }

        assertEquals(List.of(), problems, "文案调用点的占位符必须填齐（未填会原样发到玩家群里）");
    }

    @Test
    void everyLiteralKeyExistsInBundledCatalog() throws IOException {
        Map<String, String> catalog = readCatalog(ZH_PACK);
        List<String> problems = new ArrayList<>();

        for (Path file : javaSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = MSG_LITERAL_KEY.matcher(source);
            while (m.find()) {
                String key = m.group(1);
                // 只检查形如 a.b 的语言包 key（排除 "message"、模板 key 等单段标识）
                if (!key.contains(".") || catalog.containsKey(key)) {
                    continue;
                }
                // 允许「前缀 + 动态后缀」的用法（如 "review.type." + id / "rank.group." + g）：跳过以 . 结尾的形态
                if (key.endsWith(".")) {
                    continue;
                }
                problems.add(file.getFileName() + " key=" + key);
            }
        }

        assertEquals(List.of(), problems, "代码引用的语言包 key 必须存在；若为动态拼接（前缀 + id）请用常量拼装");
    }

    @Test
    void catalogPlaceholdersAreSnakeCaseAndClosed() throws IOException {
        Map<String, String> catalog = readCatalog(ZH_PACK);
        assertTrue(catalog.size() > 300, "语言包 key 数量异常（应 > 300），实际 " + catalog.size());
        for (Map.Entry<String, String> e : catalog.entrySet()) {
            for (String placeholder : placeholders(e.getValue())) {
                assertTrue(
                        placeholder.matches("[a-z][a-z0-9_]*"),
                        "占位符应小写下划线: key=" + e.getKey() + " {" + placeholder + "}");
            }
        }
    }

    private static Set<String> placeholders(String template) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(template == null ? "" : template);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** 读 zh-CN 语言包为「扁平 key → 正文」（与 I18nLoader 的展平口径一致：叶子值才算 key）。 */
    private static Map<String, String> readCatalog(Path file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        List<int[]> indents = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.stripTrailing();
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            Matcher m = Pattern.compile("^(\\s*)([^\\s:#][^:]*):\\s*(.*)$").matcher(line);
            if (!m.matches()) {
                continue;
            }
            int indent = m.group(1).length();
            String name = unquote(m.group(2).trim());
            String value = m.group(3).trim();
            while (!indents.isEmpty() && indents.get(indents.size() - 1)[0] >= indent) {
                indents.remove(indents.size() - 1);
                names.remove(names.size() - 1);
            }
            indents.add(new int[] {indent});
            names.add(name);
            if (value.isEmpty() || "\"\"".equals(value) || "''".equals(value)) {
                continue;
            }
            out.put(String.join(".", names), unquote(value));
        }
        return out;
    }

    private static String unquote(String s) {
        String t = s;
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }
}
