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
