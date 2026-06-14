package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplateOptionsTest extends ServiceTestBase {
    @Test
    public void testOptionsMapping() {
        YamlConfiguration cfg = getYamlConfiguration();
        TemplateOptions opt = TemplateOptions.from(cfg);
        Assertions.assertEquals("per_min", opt.rateUnit());
        Assertions.assertEquals("sec", opt.etaUnit());
        Assertions.assertEquals("主世界", opt.worldAlias().get("world"));
        Assertions.assertEquals("下界", opt.worldAlias().get("world_nether"));
        Assertions.assertEquals("末地", opt.worldAlias().get("world_the_end"));
        Assertions.assertEquals("管理员", opt.roleAlias().get("admin"));
        Assertions.assertEquals("玩家", opt.roleAlias().get("member"));
        Assertions.assertEquals(2.0, opt.coordScale());
        Assertions.assertEquals("meter", opt.coordUnitLabel());
        Assertions.assertEquals("区域", opt.stageCnMap().get("Region"));
        Assertions.assertTrue(opt.stageCnMap().containsKey("Chunk"));
    }

    private static @NotNull YamlConfiguration getYamlConfiguration() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("templates.stage_cn.Region", "区域");
        cfg.set("templates.stage_cn.Chunk", "区块");
        cfg.set("templates.progress_units.rate", "per_min");
        cfg.set("templates.progress_units.eta", "sec");
        cfg.set("templates.world_alias.world", "主世界");
        cfg.set("templates.role_alias.admin", "管理员");
        cfg.set("templates.role_alias.member", "玩家");
        cfg.set("templates.coord.scale", 2.0);
        cfg.set("templates.coord.unit_label", "meter");
        return cfg;
    }
}
