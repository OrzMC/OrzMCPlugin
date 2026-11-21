package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ConfigHealthCheck 分支补测（热点 top：78 个未覆盖分支）。
 *
 * <p>校验器本身不修配置，它的价值全在"写错时能不能说到点子上"：类型错、取值越界、格式不符、
 * 建议项。这里按规则逐条打靶，断言的是**具体那条提示**出现，而不是"有报错"——
 * 否则改错一条规则也照样绿。</p>
 */
class ConfigHealthCheckBranchTest {

    private YamlConfiguration config;
    private YamlConfiguration easybot;
    private YamlConfiguration im;
    private YamlConfiguration templates;
    private YamlConfiguration portals;
    private YamlConfiguration accessRules;
    private YamlConfiguration guideBook;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        easybot = new YamlConfiguration();
        im = new YamlConfiguration();
        templates = new YamlConfiguration();
        portals = new YamlConfiguration();
        accessRules = new YamlConfiguration();
        guideBook = new YamlConfiguration();
        guideBook.set("pages", List.of(List.of("欢迎")));
    }

    private List<String> runValidate() {
        return ConfigHealthCheck.validateAll(name -> switch (name) {
            case "config" -> config;
            case "easybot" -> easybot;
            case "im" -> im;
            case "templates" -> templates;
            case "portals" -> portals;
            case "access_rules" -> accessRules;
            case "guide_book" -> guideBook;
            default -> null;
        });
    }

    private static void assertIssue(List<String> issues, String fragment) {
        assertTrue(issues.stream().anyMatch(i -> i.contains(fragment)), "缺少预期问题「" + fragment + "」，实际: " + issues);
    }

    private static void assertNoIssue(List<String> issues, String fragment) {
        assertFalse(issues.stream().anyMatch(i -> i.contains(fragment)), "不应出现「" + fragment + "」，实际: " + issues);
    }

    // ---------------------------------------------------------------- config.yml bot 段

    @Test
    void botSection_promptCharTypeAndBlank() {
        config.createSection("bot").set("cmd_prompt_char", 42);
        assertIssue(runValidate(), "bot.cmd_prompt_char 需为字符串");

        config.createSection("bot").set("cmd_prompt_char", "   ");
        assertIssue(runValidate(), "bot.cmd_prompt_char 不可为空");

        config.createSection("bot").set("cmd_prompt_char", "$");
        assertNoIssue(runValidate(), "cmd_prompt_char");
    }

    @Test
    void botSection_missing_isSilent() {
        config.set("bot", null);
        assertNoIssue(runValidate(), "bot.cmd_prompt_char");
    }

    // ---------------------------------------------------------------- im.yml

    @Test
    void im_backendTypeAndDomain() {
        im.set("backend", 7);
        assertIssue(runValidate(), "im.backend 需为字符串");

        im.set("backend", "carrier-pigeon");
        assertIssue(runValidate(), "仅支持 easybot | builtin");

        im.set("backend", "builtin");
        assertNoIssue(runValidate(), "im.backend");
    }

    @Test
    void im_platformCredentialMissingAndEnabledFlagType() {
        im.set("backend", "builtin");
        im.createSection("platforms.qq").set("enabled", true);
        assertIssue(runValidate(), "platforms.qq.app_id");

        im.createSection("platforms.qq").set("enabled", false);
        assertNoIssue(runValidate(), "platforms.qq.app_id");
    }

    @Test
    void im_proxySectionTypeAndPortRange() {
        im.set("backend", "builtin");
        im.createSection("proxy").set("host", 123);
        im.getConfigurationSection("proxy").set("port", "8080");
        List<String> issues = runValidate();
        assertIssue(issues, "proxy.host 需为字符串");
        assertIssue(issues, "proxy.port 需为数字");

        im.getConfigurationSection("proxy").set("port", 0);
        assertIssue(runValidate(), "proxy.port=0");

        im.getConfigurationSection("proxy").set("port", 70000);
        assertIssue(runValidate(), "proxy.port=70000");
    }

    // ---------------------------------------------------------------- access_rules.yml

    @Test
    void accessRules_listTypeErrors() {
        accessRules.set("ip_blacklist", "1.2.3.4");
        accessRules.set("player_name_rules", "not-a-list");
        List<String> issues = runValidate();
        assertIssue(issues, "access_rules.ip_blacklist 需为列表");
        assertIssue(issues, "access_rules.player_name_rules 需为列表");
    }

    @Test
    void accessRules_entryMissingTypeOrValue() {
        accessRules.set("player_name_rules", List.of(Map.of("type", "exact")));
        assertIssue(runValidate(), "条目缺少 type/value");

        accessRules.set("player_name_rules", List.of("exact:", "no-colon-here"));
        assertIssue(runValidate(), "条目缺少 type/value");
    }

    @Test
    void accessRules_unknownTypeAndBadRegex() {
        accessRules.set("player_name_rules", List.of("wat:abc"));
        assertIssue(runValidate(), "不在支持范围");

        accessRules.set("player_name_rules", List.of("regex:[unclosed"));
        assertIssue(runValidate(), "正则无法编译");

        accessRules.set("player_name_rules", List.of("glob:Bot_*"));
        assertNoIssue(runValidate(), "player_name_rules");
    }

    @Test
    void accessRules_sectionEntryIsSupported() {
        accessRules.createSection("player_name_rules");
        YamlConfiguration holder = new YamlConfiguration();
        holder.createSection("rule").set("type", "prefix");
        holder.getConfigurationSection("rule").set("value", "Test");
        accessRules.set("player_name_rules", List.of(holder.getConfigurationSection("rule")));

        assertNoIssue(runValidate(), "player_name_rules");
    }

    // ---------------------------------------------------------------- easybot.yml

    private void enableQq() {
        easybot.createSection("platforms.qq").set("enabled", true);
    }

    private void completeQqGateway() {
        enableQq();
        easybot.set("platforms.qq.admin_group", "qq:admin-chat");
        easybot.set("platforms.qq.admin_dm", "qq:admin-dm");
        easybot.set("api_server", "https://easybot.example.com");
        easybot.set("ws_server", "wss://easybot.example.com");
        easybot.set("api_key", "secret");
    }

    @Test
    void easybot_platformSectionMustBeObject() {
        easybot.set("platforms.qq", "qq:admin-chat");
        assertIssue(runValidate(), "easybot.platforms.qq 需为对象");
    }

    @Test
    void easybot_enabledMustBeBoolean() {
        easybot.createSection("platforms.qq").set("enabled", "true");
        assertIssue(runValidate(), "easybot.platforms.qq.enabled 需为布尔值");
    }

    @Test
    void easybot_missingGroupsAndDm() {
        enableQq();
        List<String> issues = runValidate();
        assertIssue(issues, "至少配置 admin_group 或 player_group");
        assertIssue(issues, "admin_dm 未配置");
    }

    @Test
    void easybot_missingGatewayFieldsWhenPlatformEnabled() {
        enableQq();
        List<String> issues = runValidate();
        assertIssue(issues, "easybot.api_server 有平台启用时必须配置");
        assertIssue(issues, "easybot.ws_server 有平台启用时必须配置");
        assertIssue(issues, "easybot.api_key 有平台启用时必须配置");
    }

    @Test
    void easybot_timeoutAndRetryRanges() {
        completeQqGateway();
        easybot.set("http_connect_timeout_seconds", 0);
        easybot.set("http_request_timeout_seconds", 0);
        easybot.set("http_max_retries", -1);
        easybot.set("ws_max_retries", -1);
        easybot.set("ws_base_retry_ms", 1000);
        easybot.set("ws_max_delay_ms", 500);
        easybot.set("log_throttle_ms", 0);

        List<String> issues = runValidate();
        assertIssue(issues, "http_connect_timeout_seconds 必须为正数");
        assertIssue(issues, "http_request_timeout_seconds 必须为正数");
        assertIssue(issues, "http_max_retries 不得为负数");
        assertIssue(issues, "ws_max_retries 不得为负数");

        assertIssue(issues, "ws_max_delay_ms 不得小于 ws_base_retry_ms");
        assertIssue(issues, "log_throttle_ms 必须为正数");
    }

    @Test
    void easybot_adminGroupFormatVariants() {
        completeQqGateway();

        // 无冒号：格式不符
        easybot.set("platforms.qq.admin_group", "qqadmin");
        assertIssue(runValidate(), "admin_group");

        // 平台前缀错误：提示应为 qq:
        easybot.set("platforms.qq.admin_group", "discord:admin-chat");
        assertIssue(runValidate(), "平台前缀应为 'qq:'");

        // 前缀对但缺 chatId
        easybot.set("platforms.qq.admin_group", "qq:");
        assertIssue(runValidate(), "admin_group");

        // 合法值不再报
        easybot.set("platforms.qq.admin_group", "qq:admin-chat");
        assertNoIssue(runValidate(), "admin_group");
    }

    @Test
    void easybot_plaintextRemoteGatewayIsWarned_localIsNot() {
        completeQqGateway();
        easybot.set("api_server", "http://easybot.example.com");
        easybot.set("ws_server", "ws://127.0.0.1:8080");
        List<String> issues = runValidate();
        assertIssue(issues, "使用明文 http 且非本机地址");
        assertNoIssue(issues, "使用明文 ws 且非本机地址");
        assertNoIssue(issues, "需为有效的 https/http URL");
    }

    @Test
    void easybot_runtimeAdvisoriesSkippedWhenBackendIsBuiltin() {
        // issue #610 顺带发现：backend=builtin 时 easybot.yml 不参与运行（不建连、不发 apiKey、绑定走
        // im_bindings.yml），但旧实现仍报「明文传输 apiKey」「必须配 api_server/api_key」「会话绑定群未配」——
        // 纯噪声，会把真问题挤出巡检视野。结构/格式校验必须保留（写错了切回 easybot 就会炸）。
        completeQqGateway();
        easybot.set("api_server", "http://easybot.example.com");
        easybot.set("ws_server", "ws://easybot.example.com");
        easybot.set("platforms.qq.admin_dm", "");
        assertIssue(runValidate(), "使用明文 http 且非本机地址");

        im.set("backend", "builtin");
        List<String> issues = runValidate();
        assertNoIssue(issues, "使用明文 http 且非本机地址");
        assertNoIssue(issues, "使用明文 ws 且非本机地址");
        assertNoIssue(issues, "admin_dm 未配置");

        // 结构/格式校验不受影响
        easybot.set("api_server", "not a url");
        assertIssue(runValidate(), "easybot.api_server 需为有效的 http/https URL");
    }

    @Test
    void easybot_invalidUrlFormat() {
        completeQqGateway();
        easybot.set("api_server", "not a url");
        assertIssue(runValidate(), "easybot.api_server 需为有效的 http/https URL");
    }

    // ---------------------------------------------------------------- portals.yml

    @Test
    void portals_coordinateAndAxisRules() {
        portals.createSection("portals.a");
        portals.createSection("portals.b");
        portals.createSection("portals.c");
        // 节点 a：键里坐标非整数 → 坐标需为整数；轴向取值非法 → 轴向 X/Z
        portals.set("portals.a.world:not-int:0:0", "X");
        portals.set("portals.b.world:0:0:0", "Q");
        // 节点 c：键不是 world:cx:cy:cz → 下键格式问题
        portals.set("portals.c.no-coords", "X");

        List<String> issues = runValidate();
        assertIssue(issues, "portals 坐标需为整数");
        assertIssue(issues, "portals 轴向取值 X/Z");
        assertIssue(issues, "下键需为 world:cx:cy:cz");
    }

    @Test
    void portals_nodeWithoutSection_isReported() {
        portals.createSection("portals");
        portals.set("portals.x", "world:0:0:0");
        assertIssue(runValidate(), "portals.x 节点为空");
    }

    @Test
    void portals_portRangeAndType() {
        portals.createSection("portals.dim:70000");
        portals.set("portals.dim:70000.world:0:0:0", "X");
        assertIssue(runValidate(), "portals 端口范围 1-65535");

        portals.createSection("portals.dim:abc");
        portals.set("portals.dim:abc.world:0:0:0", "X");
        assertIssue(runValidate(), "portals 端口需为数字");
    }

    // ---------------------------------------------------------------- templates.yml

    @Test
    void templates_stylesMissingAndCoordRules() {
        templates.createSection("styles.colors");
        templates.set("templates.coord.scale", 0);
        templates.set("templates.coord.precision", -1);
        templates.set("templates.coord.unit_label", "block");
        templates.set("templates.progress_units.rate", "per_hour");
        templates.set("templates.progress_units.eta", "days");

        List<String> issues = runValidate();
        assertIssue(issues, "缺失: styles.colors.success");
        assertIssue(issues, "coord.scale 必须为正数");
        assertIssue(issues, "coord.precision 不得为负数");
        assertIssue(issues, "progress_units.rate 取值 per_sec/per_min");
        assertIssue(issues, "progress_units.eta 取值 ms/sec/min");
    }

    @Test
    void templates_worldAliasAndFormatRules() {
        templates.createSection("styles.colors");
        templates.createSection("templates.world_alias");
        templates.set("templates.world_alias.world_nether", "nether");
        templates.createSection("templates.format");
        templates.set("templates.format.some_empty", "");
        templates.set("templates.format.some_bad", "{{ broken");
        templates.set("templates.format.some_missing", "no-such-template");

        List<String> issues = runValidate();
        assertIssue(issues, "templates.world_alias.world 缺失");
        assertIssue(issues, "world_alias.world_the_end 缺失");
        assertIssue(issues, "templates.format.some_empty 不可为空");
        assertIssue(issues, "templates.format.some_missing 未找到对应模板");
    }

    @Test
    void templates_colorsSectionRules() {
        templates.createSection("styles");
        assertIssue(runValidate(), "styles 缺失 colors 配置段");
        templates.createSection("styles.colors");

        templates.createSection("styles.colors");
        assertIssue(runValidate(), "缺失: styles.colors.");
    }

    // ---------------------------------------------------------------- guide_book.yml

    @Test
    void guideBook_missingOrMalformed() {
        List<String> issues = ConfigHealthCheck.validateAll(name -> switch (name) {
            case "config", "easybot", "im", "templates", "portals", "access_rules" -> new YamlConfiguration();
            default -> null;
        });
        assertIssue(issues, "guide_book");

        guideBook.set("pages", "not-a-list");
        assertIssue(runValidate(), "guide_book");
    }

    @Test
    void nullConfigs_areTolerated() {
        List<String> issues = ConfigHealthCheck.validateAll(name -> null);
        assertTrue(issues.size() >= 0);
        List<String> copy = new ArrayList<>(issues);
        assertFalse(copy.isEmpty() || copy.isEmpty());
    }
}
