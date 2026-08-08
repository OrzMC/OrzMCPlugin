package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplateResolversTest {

    private static TemplateOptions options(Map<String, String> stage, Map<String, String> world) {
        return new TemplateOptions(
                stage, "per_sec", "ms", world, new HashMap<>(), 1.0, "block", "zh-CN", new HashMap<>());
    }

    @Test
    public void testWorldAliasInference() {
        TemplateOptions opt = options(new HashMap<>(), new HashMap<>());
        Assertions.assertEquals("主世界", TemplateResolvers.worldAlias("custom_world", "NORMAL", opt));
        Assertions.assertEquals("下界", TemplateResolvers.worldAlias("dim-1", "NETHER", opt));
        Assertions.assertEquals("末地", TemplateResolvers.worldAlias("end", "THE_END", opt));
        Assertions.assertEquals("主世界", TemplateResolvers.worldAlias("unknown_world", null, opt));
        Assertions.assertEquals("主世界", TemplateResolvers.worldAlias("unknown_world", "", opt));
        Map<String, String> m = new HashMap<>();
        m.put("my_world", "我的世界");
        TemplateOptions opt2 = options(new HashMap<>(), m);
        Assertions.assertEquals("我的世界", TemplateResolvers.worldAlias("my_world", "NORMAL", opt2));
    }

    @Test
    public void testStageAliasI18n() {
        Map<String, String> stageAliasLocalizedMap = new HashMap<>();
        stageAliasLocalizedMap.put("Region", "区域");
        stageAliasLocalizedMap.put("Chunk", "区块");
        Map<String, Map<String, String>> stageAliasLocalized = new HashMap<>();
        stageAliasLocalized.put("zh-CN", stageAliasLocalizedMap);
        TemplateOptions opt = new TemplateOptions(
                new HashMap<>(),
                "per_sec",
                "ms",
                new HashMap<>(),
                new HashMap<>(),
                1.0,
                "block",
                "zh-CN",
                stageAliasLocalized);
        Assertions.assertEquals("区域", TemplateResolvers.stageAlias("Region", opt));
        Assertions.assertEquals("区块", TemplateResolvers.stageAlias("Chunk", opt));
        Assertions.assertEquals("进行中", TemplateResolvers.stageAlias("Unknown", opt));
    }
}
