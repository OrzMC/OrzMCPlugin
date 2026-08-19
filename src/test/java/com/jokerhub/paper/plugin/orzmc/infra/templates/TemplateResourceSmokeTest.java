package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplateResourceSmokeTest extends ServiceTestBase {
    private YamlConfiguration load(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            Assertions.assertNotNull(in, name);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testCommandTemplatesResolve() throws Exception {
        YamlConfiguration cfg = load("templates.yml");
        List<String> keys = List.of(
                "command_players",
                "command_whitelist_header",
                "command_whitelist_cleanup",
                "command_whitelist_page",
                "command_help",
                "command_whitelist_add_result",
                "command_whitelist_remove_result",
                "command_backup",
                "command_optimize",
                "command_optimize_disabled",
                "command_blacklist_list",
                "command_blacklist_add",
                "command_blacklist_remove",
                "command_blacklist_error",
                "command_admin_required",
                "command_usage");
        for (String key : keys) {
            String resolved = TemplateRenderer.resolveTemplate(key, cfg, "fallback");
            Assertions.assertFalse(resolved.isEmpty());
        }
    }

    @Test
    public void testReviewTemplatesResolve() throws Exception {
        YamlConfiguration cfg = load("templates.yml");
        List<String> keys = List.of(
                "review_submitted",
                "review_cancelled",
                "review_approved",
                "review_rejected",
                "rank_status",
                "command_review_list",
                "command_review_list_empty",
                "command_review_result",
                "command_review_error");
        for (String key : keys) {
            String resolved = TemplateRenderer.resolveTemplate(key, cfg, "fallback-" + key);
            Assertions.assertFalse(resolved.isEmpty(), key + " 模板缺失");
        }
    }

    @Test
    public void testReviewGroupEventsRenderRealText_notLiteralMessage() throws Exception {
        // 防回归：群通知模板必须渲染出真实中文文案，而非字面 "{message}"
        // （ReviewNotifierAdapter 传 vars = player/type/summary/reviewer，不含 message 键）
        YamlConfiguration cfg = load("templates.yml");
        var vars = java.util.Map.of(
                "player", "TestNewbie",
                "type", "晋升建造者",
                "summary", "申请晋升 builder：想用WorldEdit",
                "reviewer", "群管理员");

        String submitted = TemplateRenderer.render(TemplateRenderer.resolveTemplate("review_submitted", cfg, ""), vars);
        Assertions.assertTrue(submitted.contains("TestNewbie"), "submitted 应含玩家名: " + submitted);
        Assertions.assertFalse(submitted.contains("{message}"), "submitted 不得为字面 {message}: " + submitted);

        String approved = TemplateRenderer.render(TemplateRenderer.resolveTemplate("review_approved", cfg, ""), vars);
        Assertions.assertTrue(
                approved.contains("TestNewbie") && approved.contains("群管理员"), "approved 渲染异常: " + approved);
        Assertions.assertFalse(approved.contains("{message}"), "approved 不得为字面 {message}: " + approved);

        String rejected = TemplateRenderer.render(TemplateRenderer.resolveTemplate("review_rejected", cfg, ""), vars);
        Assertions.assertFalse(rejected.contains("{message}"), "rejected 不得为字面 {message}: " + rejected);
    }

    @Test
    public void testWhitelistBlockRendersEmojiStyle() throws Exception {
        // 群消息样式统一（2026-08-19）：白名单拦截 = 表情 + 原文
        YamlConfiguration cfg = load("templates.yml");
        String rendered = TemplateRenderer.render(
                TemplateRenderer.resolveTemplate("whitelist_block", cfg, ""),
                java.util.Map.of("message", "RameshChoudary 尝试加入服务器，被白名单拦截"));
        Assertions.assertTrue(rendered.startsWith("🙅🏻‍♂️ "), "白名单拦截应以表情开头: " + rendered);
        Assertions.assertTrue(rendered.contains("RameshChoudary 尝试加入服务器，被白名单拦截"), "got: " + rendered);
    }

    @Test
    public void testExceptionAlertRendersServerAbnormalBlock() throws Exception {
        // 异常消息 = 「⚠️ 服务器异常」外壳 + 分割线 + 异常项（支持多项）
        YamlConfiguration cfg = load("templates.yml");
        String rendered = TemplateRenderer.render(
                TemplateRenderer.resolveTemplate("exception_alert", cfg, ""),
                java.util.Map.of("message", "白名单关闭\n其它异常项"));
        Assertions.assertTrue(rendered.startsWith("⚠️ 服务器异常\n"), "异常消息应以服务器异常外壳开头: " + rendered);
        Assertions.assertTrue(rendered.contains("\n---------------------------------\n"), "应含分割线: " + rendered);
        Assertions.assertTrue(rendered.endsWith("白名单关闭\n其它异常项"), "多项异常应逐行显示: " + rendered);
    }

    @Test
    public void testPlayerJoinRendersUnifiedBlockStyle() throws Exception {
        // 上下线统一样式：🎮 当前玩家头 + 分割线 + 版块头 + 玩家行
        YamlConfiguration cfg = load("templates.yml");
        String rendered = TemplateRenderer.render(
                TemplateRenderer.resolveTemplate("player_join", cfg, ""),
                java.util.Map.of(
                        "online_count", "1",
                        "max_count", "150",
                        "name", "StyleApp 生存模式 建造者"));
        Assertions.assertTrue(rendered.startsWith("🎮 当前玩家(1/150)\n"), "got: " + rendered);
        Assertions.assertTrue(rendered.contains("\n---------------------------------\n🥰 上线：\n"), "got: " + rendered);
        Assertions.assertTrue(rendered.endsWith("StyleApp 生存模式 建造者"), "got: " + rendered);
    }

    @Test
    public void testAllBlockStyleTemplates_useUnifiedShortDivider() throws Exception {
        // 群消息统一样式（2026-08-19）：所有带分割线的模板必须用统一的 33 连字符分割线，
        // 且不得残留旧的 41 连字符长分割线（digest 版块与单发模板分割线须一致）
        YamlConfiguration cfg = load("templates.yml");
        List<String> keys = List.of(
                "player_join",
                "player_quit",
                "player_kick",
                "exception_alert",
                "whitelist_toggle_alert",
                "review_submitted",
                "review_cancelled",
                "review_approved",
                "review_rejected");
        for (String key : keys) {
            String tpl = cfg.getString("templates." + key, "");
            Assertions.assertFalse(tpl.isEmpty(), key + " 模板缺失");
            // 分割线行必须恰好 33 连字符（整行匹配，防 34~41 连字符的宽松子串误放行）
            boolean hasDivider = false;
            for (String line : tpl.split("\n")) {
                if (line.matches("-+")) {
                    hasDivider = true;
                    Assertions.assertEquals(33, line.length(), key + " 分割线必须恰好 33 连字符: " + tpl);
                }
            }
            Assertions.assertTrue(hasDivider, key + " 应含分割线行: " + tpl);
        }
        // player_digest 的分割线由 Java 侧 buildSection 动态注入（不在模板字面中），
        // 单独校验其不含任何纯连字符行即可（33 连字符一致性由 PlayerEventAggregatorTest 覆盖）
        String digest = cfg.getString("templates.player_digest", "");
        for (String line : digest.split("\n")) {
            Assertions.assertFalse(line.matches("-+"), "player_digest 不应含字面分割线（由 Java 动态注入）: " + digest);
        }
    }

    @Test
    public void testSecurityAuditTemplateResolvesWithRealText() throws Exception {
        // 安全加固 P1-2：启动自检报告模板必须渲染真实中文文案，而非字面 "{online_mode}" 等占位符
        YamlConfiguration cfg = load("templates.yml");
        String resolved = TemplateRenderer.resolveTemplate("security_audit", cfg, "");
        Assertions.assertFalse(resolved.isEmpty(), "security_audit 模板缺失");

        var vars = java.util.Map.of(
                "online_mode", "正版验证开启",
                "command_block", "禁用",
                "rcon", "未启用",
                "whitelist", "开启（强制）",
                "ops", "2 个: steve, alex",
                "plugins", "LuckPerms、Grim");
        String rendered = TemplateRenderer.render(resolved, vars);

        Assertions.assertTrue(rendered.contains("正版验证开启"), "应含在线模式文案: " + rendered);
        Assertions.assertTrue(rendered.contains("LuckPerms、Grim"), "应含插件列表: " + rendered);
        Assertions.assertFalse(
                rendered.contains("{online_mode}") || rendered.contains("{plugins}"), "不得残留字面占位符: " + rendered);
    }

    @Test
    public void testLoginRateLimitAlertTemplateResolvesWithRealText() throws Exception {
        // 安全加固 P2-2：进服限流告警模板必须渲染真实中文文案，而非字面 "{ip}" 等占位符
        YamlConfiguration cfg = load("templates.yml");
        String resolved = TemplateRenderer.resolveTemplate("login_rate_limit_alert", cfg, "");
        Assertions.assertFalse(resolved.isEmpty(), "login_rate_limit_alert 模板缺失");

        var vars = java.util.Map.of(
                "ip", "1.2.3.4",
                "player", "alice",
                "reason", "频率超限（5 次/分钟）");
        String rendered = TemplateRenderer.render(resolved, vars);

        Assertions.assertTrue(rendered.contains("1.2.3.4"), "应含 IP: " + rendered);
        Assertions.assertTrue(rendered.contains("alice"), "应含玩家名: " + rendered);
        Assertions.assertFalse(
                rendered.contains("{ip}") || rendered.contains("{player}") || rendered.contains("{reason}"),
                "不得残留字面占位符: " + rendered);
    }

    @Test
    public void testExploitBlockedTemplateResolvesWithRealText() throws Exception {
        // 安全加固 P2-3：漏洞利用拦截模板必须渲染真实中文文案，而非字面 "{player}" 等占位符
        YamlConfiguration cfg = load("templates.yml");
        String resolved = TemplateRenderer.resolveTemplate("exploit_blocked", cfg, "");
        Assertions.assertFalse(resolved.isEmpty(), "exploit_blocked 模板缺失");

        var vars = java.util.Map.of(
                "player", "alice",
                "reason", "书页超限（150 页）");
        String rendered = TemplateRenderer.render(resolved, vars);

        Assertions.assertTrue(rendered.contains("alice"), "应含玩家名: " + rendered);
        Assertions.assertTrue(rendered.contains("书页超限"), "应含原因: " + rendered);
        Assertions.assertFalse(
                rendered.contains("{player}") || rendered.contains("{reason}"), "不得残留字面占位符: " + rendered);
    }

    @Test
    public void testIpBlacklistBlockTemplateResolvesWithRealText() throws Exception {
        // 安全加固 P2-4：封禁命中告警模板必须渲染真实中文文案，而非字面 "{ip}" 等占位符
        YamlConfiguration cfg = load("templates.yml");
        String resolved = TemplateRenderer.resolveTemplate("ip_blacklist_block", cfg, "");
        Assertions.assertFalse(resolved.isEmpty(), "ip_blacklist_block 模板缺失");

        var vars = java.util.Map.of(
                "player", "alice",
                "ip", "2001:db8::1",
                "pattern", "2001:db8::/32");
        String rendered = TemplateRenderer.render(resolved, vars);

        Assertions.assertTrue(rendered.contains("alice"), "应含玩家名: " + rendered);
        Assertions.assertTrue(rendered.contains("2001:db8::/32"), "应含命中规则: " + rendered);
        Assertions.assertFalse(
                rendered.contains("{player}") || rendered.contains("{ip}") || rendered.contains("{pattern}"),
                "不得残留字面占位符: " + rendered);
    }
}
