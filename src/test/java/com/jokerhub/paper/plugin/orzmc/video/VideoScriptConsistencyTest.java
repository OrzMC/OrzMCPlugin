package com.jokerhub.paper.plugin.orzmc.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 视频系列一致性门禁（CI，随 {@code ./gradlew test} 执行）。
 *
 * <p>守护四类不变量（与 {@code videos/tools/build-episode.py} / {@code verify.sh} 同源）：
 *
 * <ol>
 *   <li><b>预算</b>：单集 ≤ 时长硬上限、镜头 ≤ max_shots、信息点 ≤ max_information_points、语速落在区间内、
 *       字幕单行 ≤ 18 字且单句 ≤ 6s；
 *   <li><b>事实与锚点</b>：{@code facts.commands} 必须是 features.md 命令表中的命令、{@code facts.config_keys}
 *       必须能在 {@code src/main/resources} 中解析、{@code documentation_anchors} 必须能定位到 features.md 标题
 *       ——防止视频脚本与实现漂移；
 *   <li><b>映射完整</b>：{@code videos/coverage.yml} 中的集号必须存在、已产出源必须登记、features.md 的命令表
 *       必须全部登记（新增命令会被门禁拦下，提醒补视频映射）；
 *   <li><b>零产物与隐私</b>：{@code git ls-files videos} 不得出现媒体/卡片等产物；源中不得出现真实 IP / 域名 /
 *       QQ 号 / 会话 key / Token（官方域名白名单除外）。
 * </ol>
 */
@DisplayName("视频系列一致性（预算 / 事实锚点 / 映射完整 / 零产物）")
class VideoScriptConsistencyTest {

    private static final Path REPO = Path.of("").toAbsolutePath();
    private static final Path VIDEOS = REPO.resolve("videos");
    private static final Path EPISODES = VIDEOS.resolve("episodes");
    private static final Path FEATURES = REPO.resolve("docs/features.md");
    private static final Path RESOURCES = REPO.resolve("src/main/resources");

    private static final int SUBTITLE_LINE_MAX = 18;
    private static final double SUBTITLE_CUE_MAX_S = 6.0;
    private static final String CN_NUM = "零一二三四五六七八九";

    /** 官方/公开域名白名单（项目自有或平台地址，不算敏感信息）。 */
    private static final Set<String> DOMAIN_ALLOWLIST = Set.of(
            "orzmc.jokerhub.cn",
            "jokerhub.cn",
            "github.com",
            "hangar.papermc.io",
            "modrinth.com",
            "papermc.io",
            "qq.com");

    // ── 工具方法 ──────────────────────────────────────────────────────────

    private static Object loadYaml(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new Yaml().load(reader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static List<Map<String, Object>> shots(Map<String, Object> ep) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object shot : (List<?>) ep.getOrDefault("shots", List.of())) {
            out.add(map(shot));
        }
        return out;
    }

    private static int narrationChars(String text) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        return count;
    }

    /** 一镜字幕：字符串按标点切分；列表视为已切好的多句。 */
    private static List<String> subtitleLines(Map<String, Object> shot) {
        Object value = shot.get("subtitle");
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().trim());
                }
            }
            return out;
        }
        List<String> out = new ArrayList<>();
        Matcher matcher = Pattern.compile("[^，。！？；：、,.!?;:]+[，。！？；：、,.!?;:]?").matcher(value.toString());
        while (matcher.find()) {
            String part = matcher.group().trim();
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out.isEmpty() ? List.of(value.toString().trim()) : out;
    }

    private static String cnNumeral(int n) {
        if (n < 10) {
            return String.valueOf(CN_NUM.charAt(n));
        }
        if (n < 20) {
            return "十" + (n % 10 == 0 ? "" : CN_NUM.charAt(n % 10));
        }
        return "" + CN_NUM.charAt(n / 10) + "十" + (n % 10 == 0 ? "" : CN_NUM.charAt(n % 10));
    }

    private static Path episodeFile(String id) throws IOException {
        try (Stream<Path> files = Files.list(EPISODES)) {
            return files.filter(p -> p.getFileName().toString().startsWith(id + "-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("找不到分集源：videos/episodes/" + id + "-*.yml"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> episode(String id) throws IOException {
        return (Map<String, Object>) loadYaml(episodeFile(id));
    }

    private static List<Path> episodeSources() throws IOException {
        try (Stream<Path> files = Files.list(EPISODES)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        }
    }

    // ── 1. 预算 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("每集预算：≤ 时长上限 / 镜头与信息点上限 / 语速区间 / 字幕行宽与单句时长")
    void budgetWithinLimits() throws IOException {
        List<Path> sources = episodeSources();
        assertFalse(sources.isEmpty(), "videos/episodes/ 下应有分集源");

        for (Path source : sources) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ep = (Map<String, Object>) loadYaml(source);
            Map<String, Object> meta = map(ep.get("meta"));
            Map<String, Object> budget = map(ep.get("budget"));
            List<Map<String, Object>> shots = shots(ep);
            String id = String.valueOf(meta.get("id"));
            String where = source.getFileName().toString();

            double total = 0;
            int chars = 0;
            for (Map<String, Object> shot : shots) {
                double duration = ((Number) shot.get("duration_s")).doubleValue();
                total += duration;
                chars += narrationChars(String.valueOf(shot.getOrDefault("narration", "")));
                List<String> lines = subtitleLines(shot);
                for (String line : lines) {
                    assertTrue(
                            line.length() <= SUBTITLE_LINE_MAX,
                            where + " 镜 " + shot.get("id") + " 字幕行超 " + SUBTITLE_LINE_MAX + " 字：" + line);
                }
                if (!lines.isEmpty()) {
                    double cue = duration / lines.size();
                    assertTrue(
                            cue <= SUBTITLE_CUE_MAX_S,
                            where + " 镜 " + shot.get("id") + " 字幕单句 " + cue + "s 超过 " + SUBTITLE_CUE_MAX_S + "s");
                }
            }

            assertTrue(
                    total <= ((Number) budget.get("duration_cap_s")).doubleValue(),
                    where + " 总时长 " + total + "s 超过硬上限");
            assertTrue(shots.size() <= ((Number) budget.get("max_shots")).intValue(), where + " 镜头数超过 max_shots（应拆集）");

            List<?> infoPoints = (List<?>) meta.getOrDefault("information_points", List.of());
            assertTrue(
                    infoPoints.size() <= ((Number) budget.get("max_information_points")).intValue(),
                    where + " 信息点超过上限（应拆集）");

            List<?> range = (List<?>) budget.get("speaking_rate_range");
            double rate = total > 0 ? chars / total : 0;
            assertTrue(
                    rate >= ((Number) range.get(0)).doubleValue() && rate <= ((Number) range.get(1)).doubleValue(),
                    where + " 语速 " + rate + " 字/秒超出 " + range + "（改稿或调时长，勿靠加速）");
            assertTrue(
                    Math.abs(chars - ((Number) budget.get("narration_chars")).intValue())
                            <= 0.10 * ((Number) budget.get("narration_chars")).intValue(),
                    where + " 口播 " + chars + " 字与预算偏差超过 10%");
            assertTrue(chars > 0 && total > 0, where + " 缺少口播或时长：" + id);
        }
    }

    // ── 2. 事实与锚点（防漂移） ────────────────────────────────────────────

    private static Set<String> documentedCommands() throws IOException {
        Set<String> commands = new LinkedHashSet<>();
        Matcher matcher =
                Pattern.compile("^\\|\\s*`(\\$[a-z])`", Pattern.MULTILINE).matcher(Files.readString(FEATURES));
        while (matcher.find()) {
            commands.add(matcher.group(1));
        }
        return commands;
    }

    private static boolean configKeyExists(String key) throws IOException {
        String file = "config.yml";
        String dotted = key;
        if (key.contains(":")) {
            file = key.substring(0, key.indexOf(':'));
            dotted = key.substring(key.indexOf(':') + 1);
        }
        Path path = RESOURCES.resolve(file);
        if (!Files.exists(path)) {
            return false;
        }
        Object current = loadYaml(path);
        for (String part : dotted.split("\\.")) {
            if (!(current instanceof Map<?, ?> m) || !m.containsKey(part)) {
                return false;
            }
            current = m.get(part);
        }
        return true;
    }

    private static boolean templateKeyExists(String key) throws IOException {
        Path path = RESOURCES.resolve("templates.yml");
        if (!Files.exists(path) || key.contains("*")) {
            return true; // 通配兜底项（如 templates.*）不做存在性校验
        }
        Object current = loadYaml(path);
        for (String part : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> m) || !m.containsKey(part)) {
                return false;
            }
            current = m.get(part);
        }
        return true;
    }

    private static boolean anchorExists(String anchor) throws IOException {
        Matcher matcher =
                Pattern.compile("^features\\.md\\s*§(\\d+)(?:\\.(\\d+))?$").matcher(anchor.trim());
        if (!matcher.matches()) {
            return false;
        }
        String text = Files.readString(FEATURES);
        int major = Integer.parseInt(matcher.group(1));
        if (matcher.group(2) == null) {
            return Pattern.compile("^##\\s*" + cnNumeral(major) + "、", Pattern.MULTILINE)
                    .matcher(text)
                    .find();
        }
        return Pattern.compile("^###\\s*" + major + "\\." + matcher.group(2) + "\\s", Pattern.MULTILINE)
                .matcher(text)
                .find();
    }

    @Test
    @DisplayName("事实与锚点：命令 / 配置键 / 模板键 / features.md 章节必须真实存在")
    void factsAndAnchorsExist() throws IOException {
        Set<String> commands = documentedCommands();
        for (Path source : episodeSources()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ep = (Map<String, Object>) loadYaml(source);
            String where = source.getFileName().toString();
            Map<String, Object> facts = map(ep.get("facts"));

            for (Object cmd : (List<?>) facts.getOrDefault("commands", List.of())) {
                assertTrue(commands.contains(cmd), where + " facts.commands 中的 " + cmd + " 不在 features.md §2.2 命令表");
            }
            for (Object key : (List<?>) facts.getOrDefault("config_keys", List.of())) {
                assertTrue(configKeyExists(key.toString()), where + " facts.config_keys 中的 " + key + " 不存在");
            }
            for (Object key : (List<?>) facts.getOrDefault("template_keys", List.of())) {
                assertTrue(templateKeyExists(key.toString()), where + " facts.template_keys 中的 " + key + " 不存在");
            }
            List<?> anchors = (List<?>) ep.getOrDefault("documentation_anchors", List.of());
            assertFalse(anchors.isEmpty(), where + " 缺少 documentation_anchors（脚本必须绑定权威文档）");
            for (Object anchor : anchors) {
                assertTrue(anchorExists(anchor.toString()), where + " 锚点 `" + anchor + "` 无法在 docs/features.md 定位");
            }
        }
    }

    // ── 3. 覆盖映射完整性 ──────────────────────────────────────────────────

    @Test
    @DisplayName("映射完整：集号存在、已产出源已登记、命令表全部登记")
    void coverageMappingComplete() throws IOException {
        Map<String, Object> coverage = map(loadYaml(VIDEOS.resolve("coverage.yml")));
        Map<String, Object> episodes = map(coverage.get("episodes"));
        assertFalse(episodes.isEmpty(), "coverage.yml 缺少 episodes 段");

        Set<String> registered = new LinkedHashSet<>();
        int produced = 0;
        for (Map.Entry<String, Object> entry : episodes.entrySet()) {
            Map<String, Object> cfg = map(entry.getValue());
            registered.add(String.valueOf(cfg.get("id")));
            if (Boolean.TRUE.equals(cfg.get("produced"))) {
                produced++;
                assertTrue(
                        Files.exists(episodeFile(String.valueOf(cfg.get("id")))),
                        "coverage 标记 produced 却找不到源：" + cfg.get("id"));
            } else {
                assertTrue(cfg.containsKey("cap_s"), entry.getKey() + " 缺少 cap_s");
            }
        }
        assertEquals(26, episodes.size(), "分集蓝本应为 26 集（EP0–EP25）");
        assertTrue(produced >= 2, "至少 EP0/EP1 应已产出源");

        for (Path source : episodeSources()) {
            String id = source.getFileName().toString().split("-")[0];
            assertTrue(registered.contains(id), "源 " + source.getFileName() + " 未登记到 coverage.yml");
        }

        // features.md 命令表 ↔ coverage.commands：新增命令必须补登记（提示视频需评估）
        Map<String, Object> commandMap = map(coverage.get("commands"));
        for (String cmd : documentedCommands()) {
            assertTrue(commandMap.containsKey(cmd), "命令 " + cmd + " 未登记到 coverage.yml 的 commands 映射");
        }

        // 章节映射值必须是已知集号
        for (String section : List.of("chapters", "commands", "config_keys", "template_keys")) {
            for (Object eps : map(coverage.get(section)).values()) {
                for (Object ep : (List<?>) eps) {
                    assertTrue(episodes.containsKey(ep.toString()), section + " 映射引用了未知集号 " + ep);
                }
            }
        }
    }

    // ── 4. 零产物与隐私 ───────────────────────────────────────────────────

    @Test
    @DisplayName("零产物：videos/ 不得跟踪媒体/卡片等产物（videos/assets/ 输入素材除外）")
    void noArtifactsTracked() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "ls-files", "videos")
                .directory(REPO.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), "git ls-files 执行失败");

        Pattern artifact = Pattern.compile("\\.(mp4|mov|mkv|webm|mp3|wav|m4a|srt|ass|png|jpg|jpeg|psd|aep|ai)$");
        List<String> bad = new ArrayList<>();
        for (String line : output.split("\n")) {
            String file = line.trim();
            if (file.isEmpty() || file.startsWith("videos/assets/")) {
                continue;
            }
            if (artifact.matcher(file).find()) {
                bad.add(file);
            }
        }
        assertTrue(bad.isEmpty(), "videos/ 下有产物被跟踪（应改为 .build/ 生成物且不入库）：" + bad);
    }

    @Test
    @DisplayName("隐私：源中不得出现真实 IP / 域名 / QQ 号 / 会话 key / Token")
    void noSensitiveContent() throws IOException {
        List<Pattern> patterns = List.of(
                Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b"),
                Pattern.compile("\\b(?:[a-zA-Z0-9-]+\\.)+(?:com|cn|net|org|io|me|xyz)\\b"),
                Pattern.compile("\\b\\d{6,12}\\b"),
                Pattern.compile("\\b[A-F0-9]{32,}\\b"),
                Pattern.compile("(?i)\\b(?:token|secret|password|api[_-]?key)\\s*[:=]\\s*\\S+"));

        for (Path source : episodeSources()) {
            String text = Files.readString(source);
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    String hit = matcher.group();
                    boolean allowed = hit.startsWith("1.0.")
                            || hit.equals("25565")
                            || hit.equals("19132")
                            || DOMAIN_ALLOWLIST.stream().anyMatch(d -> hit.equals(d) || hit.endsWith("." + d));
                    assertTrue(allowed, source.getFileName() + " 出现疑似敏感信息：" + hit);
                }
            }
        }
    }

    @Test
    @DisplayName("模板：_template/episode.yml 提供必备字段（新集复制即用）")
    void templateHasRequiredFields() throws IOException {
        Map<String, Object> template = map(loadYaml(VIDEOS.resolve("_template/episode.yml")));
        Map<String, Object> meta = map(template.get("meta"));
        Map<String, Object> budget = map(template.get("budget"));
        for (String key : List.of("id", "slug", "title", "baseline_version", "information_points")) {
            assertTrue(meta.containsKey(key), "_template/episode.yml meta 缺少 " + key);
        }
        for (String key :
                List.of("duration_cap_s", "target_duration_s", "narration_chars", "max_shots", "speaking_rate_range")) {
            assertTrue(budget.containsKey(key), "_template/episode.yml budget 缺少 " + key);
        }
        assertTrue(template.containsKey("shots"), "_template/episode.yml 缺少 shots 示例");
        assertTrue(template.containsKey("documentation_anchors"), "_template/episode.yml 缺少 documentation_anchors");
        assertTrue(template.containsKey("facts"), "_template/episode.yml 缺少 facts");
        assertTrue(template.containsKey("aspect_variants"), "_template/episode.yml 缺少 aspect_variants");
    }
}
