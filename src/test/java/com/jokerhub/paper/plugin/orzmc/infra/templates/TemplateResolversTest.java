package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplateResolversTest {
    @Test
    public void testWorldAliasInference() {
        Map<String, String> stage = new HashMap<>();
        Map<String, String> world = new HashMap<>();
        Map<String, String> roleAlias = new HashMap<>();
        java.util.Map<String, Map<String, String>> roleAliasLocalized = new HashMap<>();
        java.util.Map<String, Map<String, String>> worldAliasLocalized = new HashMap<>();
        Map<String, String> roleGroups = new HashMap<>();
        TemplateOptions opt = new TemplateOptions(
                stage,
                "per_sec",
                "ms",
                world,
                worldAliasLocalized,
                1.0,
                "block",
                roleAlias,
                "zh-CN",
                roleAliasLocalized,
                roleGroups,
                new HashMap<>());
        Assertions.assertEquals("主世界", TemplateResolvers.worldAlias("custom_world", "NORMAL", opt));
        Assertions.assertEquals("下界", TemplateResolvers.worldAlias("dim-1", "NETHER", opt));
        Assertions.assertEquals("末地", TemplateResolvers.worldAlias("end", "THE_END", opt));
        Assertions.assertEquals("主世界", TemplateResolvers.worldAlias("unknown_world", null, opt));
        Assertions.assertEquals("主世界", TemplateResolvers.worldAlias("unknown_world", "", opt));
        Map<String, String> m = new HashMap<>();
        m.put("my_world", "我的世界");
        opt = new TemplateOptions(
                stage,
                "per_sec",
                "ms",
                m,
                worldAliasLocalized,
                1.0,
                "block",
                roleAlias,
                "zh-CN",
                roleAliasLocalized,
                roleGroups,
                new HashMap<>());
        Assertions.assertEquals("我的世界", TemplateResolvers.worldAlias("my_world", "NORMAL", opt));
    }

    @Test
    public void testRoleAlias() {
        Map<String, String> stage = new HashMap<>();
        Map<String, String> world = new HashMap<>();
        Map<String, String> role = new HashMap<>();
        role.put("admin", "超管");
        TemplateOptions opt = getTemplateOptions(stage, world, role);
        Assertions.assertEquals("超管", TemplateResolvers.roleAlias(true, opt));
        Assertions.assertEquals("玩家", TemplateResolvers.roleAlias(false, opt));
    }

    private static TemplateOptions getTemplateOptions(
            Map<String, String> stage, Map<String, String> world, Map<String, String> role) {
        Map<String, Map<String, String>> roleAliasLocalized = new HashMap<>();
        Map<String, String> roleGroups = new HashMap<>();
        return new TemplateOptions(
                stage,
                "per_sec",
                "ms",
                world,
                new HashMap<>(),
                1.0,
                "block",
                role,
                "zh-CN",
                roleAliasLocalized,
                roleGroups,
                new HashMap<>());
    }

    @Test
    public void testRoleGroupAliasMatching() {
        Map<String, String> stage = new HashMap<>();
        Map<String, String> world = new HashMap<>();
        TemplateOptions opt = getTemplateOptions(stage, world);
        List<String> perms = Arrays.asList("foo.bar", "server.vip");
        Assertions.assertEquals("VIP", TemplateResolvers.roleGroupAliasFromPermissions(perms, opt));
        List<String> none = Collections.emptyList();
        Assertions.assertEquals("玩家", TemplateResolvers.roleGroupAliasFromPermissions(none, opt));
    }

    private static TemplateOptions getTemplateOptions(
            Map<String, String> stage, Map<String, String> world) {
        Map<String, String> role = new HashMap<>();
        Map<String, Map<String, String>> roleAliasLocalized = new HashMap<>();
        Map<String, String> roleGroups = new HashMap<>();
        roleGroups.put("server.vip", "VIP");
        roleGroups.put("default", "玩家");
        return new TemplateOptions(
                stage,
                "per_sec",
                "ms",
                world,
                new HashMap<>(),
                1.0,
                "block",
                role,
                "zh-CN",
                roleAliasLocalized,
                roleGroups,
                new HashMap<>());
    }

    @Test
    public void testStageAliasI18n() {
        Map<String, String> stage = new HashMap<>();
        Map<String, String> world = new HashMap<>();
        Map<String, String> role = new HashMap<>();
        java.util.Map<String, Map<String, String>> roleAliasLocalized = new HashMap<>();
        Map<String, String> roleGroups = new HashMap<>();
        java.util.Map<String, Map<String, String>> stageAliasLocalized = new HashMap<>();
        Map<String, String> zh = new HashMap<>();
        zh.put("Region", "区域");
        zh.put("Chunk", "区块");
        stageAliasLocalized.put("zh-CN", zh);
        TemplateOptions opt = new TemplateOptions(
                stage,
                "per_sec",
                "ms",
                world,
                new HashMap<>(),
                1.0,
                "block",
                role,
                "zh-CN",
                roleAliasLocalized,
                roleGroups,
                stageAliasLocalized);
        Assertions.assertEquals("区域", TemplateResolvers.stageAlias("Region", opt));
        Assertions.assertEquals("区块", TemplateResolvers.stageAlias("Chunk", opt));
        Assertions.assertEquals("进行中", TemplateResolvers.stageAlias("Unknown", opt));
    }
}
