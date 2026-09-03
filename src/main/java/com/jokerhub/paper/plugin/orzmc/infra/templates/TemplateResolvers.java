package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;

public final class TemplateResolvers {
    private TemplateResolvers() {}

    public static String worldAlias(String worldName, String environment, TemplateOptions opt) {
        String alias = opt.worldAlias().getOrDefault(worldName, null);
        if (alias != null) return alias;
        String env = environment == null ? "" : environment.toUpperCase();
        if ("NETHER".equals(env)) return opt.worldAlias().getOrDefault("world_nether", "下界");
        if ("THE_END".equals(env)) return opt.worldAlias().getOrDefault("world_the_end", "末地");
        return opt.worldAlias().getOrDefault("world", "主世界");
    }

    public static String stageAlias(String stageName, TemplateOptions opt) {
        if (stageName == null) return opt.stageCnMap().getOrDefault("Running", "进行中");
        String cn = opt.stageCnMap().getOrDefault(stageName, null);
        if (cn != null) return cn;
        if ("Region".equalsIgnoreCase(stageName)) return "区域";
        if ("Chunk".equalsIgnoreCase(stageName)) return "区块";
        if ("File".equalsIgnoreCase(stageName)) return "文件";
        if ("Done".equalsIgnoreCase(stageName)) return "完成";
        return "进行中";
    }
}
