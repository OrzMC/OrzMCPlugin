package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceTest {

    @TempDir
    File tempDir;

    private OrzMC plugin;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        plugin = mock(OrzMC.class);
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("OrzMC"));
        // 注入 classpath 内置默认资源（插件 mock 拿不到 jar 资源），让 schema 升级真实复现
        configService = new ConfigService(plugin, ConfigServiceTest::classpathResource);
    }

    /** 读取 classpath 中的内置默认资源（src/main/resources）。 */
    private static InputStream classpathResource(String name) {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        assertNotNull(in, "classpath resource missing: " + name);
        return in;
    }

    @Test
    void constructor_createsService() {
        assertNotNull(configService);
    }

    @Test
    void setup_registersConfigs() {
        configService.setup();
        assertNotNull(configService.getConfig("config"));
        assertNotNull(configService.getConfig("easybot"));
        assertNotNull(configService.getConfig("templates"));
        assertNotNull(configService.getConfig("portals"));
        assertNotNull(configService.getConfig("access_rules"));
    }

    @Test
    void getConfig_returnsConfigByName() {
        configService.setup();
        assertNotNull(configService.getConfig("config"));
    }

    @Test
    void reloadConfig_returnsTrue_whenRegistered() {
        configService.setup();
        assertTrue(configService.reloadConfig("config"));
    }

    @Test
    void reloadConfig_returnsFalse_whenUnregistered() {
        assertFalse(configService.reloadConfig("nonexistent"));
    }

    @Test
    void reloadAll_doesNotThrow() {
        configService.setup();
        assertDoesNotThrow(() -> configService.reloadAll());
    }

    @Test
    void saveConfig_returnsTrue_whenRegistered() {
        configService.setup();
        assertTrue(configService.saveConfig("config"));
    }

    @Test
    void saveConfig_returnsFalse_whenUnregistered() {
        assertFalse(configService.saveConfig("unknown"));
    }

    @Test
    void tearDown_doesNotThrow() {
        configService.setup();
        assertDoesNotThrow(() -> configService.tearDown());
    }

    @Test
    void manager_returnsNonNull() {
        assertNotNull(configService.manager());
    }

    @Test
    void setup_backfillsMissingRankColorsKeys() {
        // 空磁盘配置 + classpath 内置默认 → schema 自动升级的深合并须把 rank_colors 全键
        // （含 tab_enabled）实体化进配置，否则 /orzmc config get 显示 <null>（行为虽默认 false，
        // 但 UI 与 7 个兄弟键不一致）。
        configService.setup();
        org.bukkit.configuration.file.FileConfiguration cfg = configService.getConfig("config");
        for (String path : List.of(
                "rank_colors.enabled",
                "rank_colors.nametag_enabled",
                "rank_colors.tab_enabled",
                "rank_colors.op_color",
                "rank_colors.colors.admin",
                "rank_colors.colors.builder",
                "rank_colors.colors.member",
                "rank_colors.colors.default")) {
            assertTrue(cfg.contains(path), "缺键未回填: " + path);
        }
        assertFalse(cfg.getBoolean("rank_colors.tab_enabled"));
    }

    // ----------------------------------------------------------------
    // schema 自动升级的服务接缝级验证（最接近生产：真实 setup() 走完整流水线）
    // ----------------------------------------------------------------

    private void seedLegacy(String name, String content) throws Exception {
        Files.write(new File(tempDir, name).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /** 整包 legacy 安装（三份 schema 文件均为无版本标记旧档）一次启动后：备份+合并+翻转+落盘全部发生。 */
    @Test
    void setup_legacyInstall_upgradesAllSchemaFilesToDisk() throws Exception {
        String configContent = "# legacy\n"
                + "whitelist:\n"
                + "  force_whitelist: true\n" // 自定义 → 保留
                + "chat:\n"
                + "  max_messages_per_minute: 6\n" // 旧默认 → 翻转 20
                + "rank_colors:\n"
                + "  tab_enabled: true\n" // 旧默认 → 翻转 false
                + "tnt:\n"
                + "  enable: false\n"; // 自定义 → 保留
        seedLegacy("config.yml", configContent);
        seedLegacy("templates.yml", "# legacy\ntemplates:\n  coord:\n    scale: 2.0\n");
        seedLegacy("easybot.yml", "# legacy\ncmd_prompt_char: '#'\n");

        ConfigService upgraded = new ConfigService(plugin, ConfigServiceTest::classpathResource);
        upgraded.setup();

        // 三个 schema 文件都应产生 .bak，且 config.yml.bak 是原始内容（备份先于一切变更）
        File configBak = new File(tempDir, "config.yml.bak");
        assertTrue(configBak.exists(), "config.yml 应生成 .bak");
        assertEquals(configContent, Files.readString(configBak.toPath()), ".bak 应为升级前原始内容");
        assertTrue(new File(tempDir, "templates.yml.bak").exists());
        assertTrue(new File(tempDir, "easybot.yml.bak").exists());

        // 落盘结果（从磁盘重读，模拟重启后视角）：
        FileConfiguration diskConfig = YamlConfiguration.loadConfiguration(new File(tempDir, "config.yml"));
        assertEquals(ConfigSchema.LATEST_VERSION, diskConfig.getInt(ConfigSchema.VERSION_KEY));
        assertEquals(20, diskConfig.getInt("chat.max_messages_per_minute"), "旧默认 6 应翻转并落盘为 20");
        assertFalse(diskConfig.getBoolean("rank_colors.tab_enabled"), "旧默认 true 应翻转并落盘为 false");
        assertTrue(diskConfig.getBoolean("whitelist.force_whitelist"), "自定义值不得被覆盖");
        assertFalse(diskConfig.getBoolean("tnt.enable"), "自定义 false 不得被覆盖");
        assertTrue(diskConfig.contains("chat.enabled"), "缺失键应补齐落盘");
        assertTrue(diskConfig.contains("rank_colors.colors.admin"), "缺失子键应补齐落盘");

        FileConfiguration diskTemplates = YamlConfiguration.loadConfiguration(new File(tempDir, "templates.yml"));
        assertEquals(ConfigSchema.LATEST_VERSION, diskTemplates.getInt(ConfigSchema.VERSION_KEY));
        assertTrue(diskTemplates.contains("templates.command_output"), "缺失模板键应补齐落盘");
        assertFalse(diskTemplates.contains("templates.server_stop"), "P5-2 server_stop 正文迁语言包，不再回填");
        assertEquals(2.0, diskTemplates.getDouble("templates.coord.scale"), "自定义值不得被覆盖");

        FileConfiguration diskEasybot = YamlConfiguration.loadConfiguration(new File(tempDir, "easybot.yml"));
        assertEquals(ConfigSchema.LATEST_VERSION, diskEasybot.getInt(ConfigSchema.VERSION_KEY));
        // v12 bot 参数搬迁：easybot 自定义 '#' 应迁入 config.yml bot: 段，easybot 旧键被清理（config 为唯一权威）
        assertEquals("#", diskConfig.getString("bot.cmd_prompt_char"), "easybot 自定义前缀应搬迁至 config.yml bot:");
        assertFalse(diskEasybot.contains("cmd_prompt_char"), "搬迁后 easybot.yml 旧键应被清理");

        // 迁移后健康检查不得再报模板 key 缺失（PR3 解除的持久告警不应回归）
        List<String> issues = ConfigHealthCheck.validateAll(upgraded.manager());
        assertTrue(issues.stream().noneMatch(s -> s.startsWith("缺失: templates.")), "升级后不应有模板 key 缺失告警: " + issues);

        // 原子写（评审 C2）：落盘后不应残留 .tmp 临时文件
        assertEquals(
                List.of(),
                java.util.Arrays.stream(tempDir.listFiles((d, n) -> n.endsWith(".tmp")))
                        .map(File::getName)
                        .sorted()
                        .toList(),
                "原子写不应残留 .tmp 临时文件");
    }

    /** 已是最新版的 schema 文件再次启动（setup 二次运行）必须零写入——UP_TO_DATE 路径的落盘级验证。 */
    @Test
    void setup_secondRun_isNoop_doesNotRewriteSchemaFiles() throws Exception {
        seedLegacy("config.yml", "whitelist:\n  force_whitelist: true\n");
        new ConfigService(plugin, ConfigServiceTest::classpathResource).setup();

        File[] schemaFiles = {
            new File(tempDir, "config.yml"), new File(tempDir, "templates.yml"), new File(tempDir, "easybot.yml")
        };
        byte[][] before = new byte[schemaFiles.length][];
        long[] mtimes = new long[schemaFiles.length];
        for (int i = 0; i < schemaFiles.length; i++) {
            before[i] = Files.readAllBytes(schemaFiles[i].toPath());
            mtimes[i] = schemaFiles[i].lastModified();
        }

        // 二次启动（模拟升级后的下一次服务器启动）：不应有任何写盘
        new ConfigService(plugin, ConfigServiceTest::classpathResource).setup();
        for (int i = 0; i < schemaFiles.length; i++) {
            assertArrayEquals(
                    before[i],
                    Files.readAllBytes(schemaFiles[i].toPath()),
                    "已最新 schema 文件二次启动不应被重写: " + schemaFiles[i].getName());
            assertEquals(
                    mtimes[i], schemaFiles[i].lastModified(), "已最新 schema 文件二次启动不应触碰: " + schemaFiles[i].getName());
        }
    }

    // ================================================================
    // guide_book 格式迁移（运行时数据文件：代码内迁移，不进 ConfigSchema）
    // ================================================================

    private static final String LEGACY_GUIDE_BOOK = """
            title: '新手指南'
            content:
              - text:
                  content: '欢迎'
                  newline_count: 2
              - link:
                  content: '主页'
                  url: 'https://orzmc.jokerhub.cn'
                  page_break: true
              - text:
                  content: '第二页'
            """;

    @Test
    void setup_migratesLegacyGuideBookOnceAndRefreshesCache() throws Exception {
        File guideBook = new File(tempDir, "guide_book.yml");
        Files.writeString(guideBook.toPath(), LEGACY_GUIDE_BOOK, StandardCharsets.UTF_8);

        configService.setup();

        String migrated = Files.readString(guideBook.toPath(), StandardCharsets.UTF_8);
        assertTrue(migrated.contains("pages:"), "应迁移为 v2 格式");
        assertFalse(migrated.contains("\ncontent:"), "旧 content 段应被替换");
        assertTrue(new File(tempDir, "guide_book.yml.bak").exists(), "应备份原文件");
        assertTrue(configService.getConfig("guide_book").isList("pages"), "内存缓存应刷新为新格式");
        assertFalse(configService.getConfig("guide_book").isList("content"));
    }

    @Test
    void setup_leavesAlreadyV2GuideBookUntouched() throws Exception {
        File guideBook = new File(tempDir, "guide_book.yml");
        String v2 = "pages:\n  - - 已经是新格式\n";
        Files.writeString(guideBook.toPath(), v2, StandardCharsets.UTF_8);

        configService.setup();

        assertEquals(v2, Files.readString(guideBook.toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(tempDir, "guide_book.yml.bak").exists(), "无需迁移时不应备份");
    }

    @Test
    void setup_freshInstall_keepsShippedV2TemplateAndItsComments() throws Exception {
        // 模拟真实 saveResource：把 classpath 内置模板复制到数据目录（plugin mock 不会真写文件）
        File guideBook = new File(tempDir, "guide_book.yml");
        try (InputStream in = classpathResource("guide_book.yml")) {
            Files.copy(in, guideBook.toPath());
        }

        configService.setup();

        assertFalse(new File(tempDir, "guide_book.yml.bak").exists(), "新装不应产生备份");
        assertTrue(configService.getConfig("guide_book").isList("pages"), "内置模板应为 v2 格式");
        String onDisk = Files.readString(guideBook.toPath(), StandardCharsets.UTF_8);
        assertTrue(onDisk.startsWith("# ====="), "启动不应回写抹掉模板注释（regression: setDefaults 会 saveConfig）");
        assertTrue(onDisk.contains("一行 = 一句话"), "模板注释应完整保留");
    }

    @Test
    void setup_migratedFile_keepsCheatSheetHeaderOnDisk() throws Exception {
        File guideBook = new File(tempDir, "guide_book.yml");
        Files.writeString(guideBook.toPath(), LEGACY_GUIDE_BOOK, StandardCharsets.UTF_8);

        configService.setup();

        String migrated = Files.readString(guideBook.toPath(), StandardCharsets.UTF_8);
        assertTrue(migrated.startsWith("# ====="), "迁移产物应带速查注释头，且不被启动流程抹掉");
        assertTrue(migrated.contains("/orzmc config reload"), migrated.substring(0, Math.min(400, migrated.length())));
    }
}
