package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import java.util.Map;

public final class TemplateResolvers {
    private TemplateResolvers() {}

    public static String worldAlias(String worldName, String environment, TemplateOptions opt) {
        Map<String, String> localized =
                opt.worldAliasLocalized().getOrDefault(opt.locale() == null ? "" : opt.locale(), null);
        String alias = localized != null ? localized.getOrDefault(worldName, null) : null;
        if (alias == null) {
            alias = opt.worldAlias().getOrDefault(worldName, null);
        }
        if (alias != null) return alias;
        String env = environment == null ? "" : environment.toUpperCase();
        if ("NETHER".equals(env)) return opt.worldAlias().getOrDefault("world_nether", "下界");
        if ("THE_END".equals(env)) return opt.worldAlias().getOrDefault("world_the_end", "末地");
        return opt.worldAlias().getOrDefault("world", "主世界");
    }

    public static String roleAlias(boolean isAdmin, TemplateOptions opt) {
        Map<String, String> localized =
                opt.roleAliasLocalized().getOrDefault(opt.locale() == null ? "" : opt.locale(), null);
        if (localized != null) {
            String v = localized.getOrDefault(isAdmin ? "admin" : "member", null);
            if (v != null) return v;
        }
        return opt.roleAlias().getOrDefault(isAdmin ? "admin" : "member", isAdmin ? "管理员" : "玩家");
    }

    public static String roleGroupAliasFromPermissions(java.util.Collection<String> permKeys, TemplateOptions opt) {
        for (java.util.Map.Entry<String, String> e : opt.roleGroupAliases().entrySet()) {
            String key = e.getKey();
            if (!"default".equalsIgnoreCase(key) && permKeys.contains(key)) {
                return e.getValue();
            }
        }
        return opt.roleGroupAliases().getOrDefault("default", roleAlias(false, opt));
    }

    public static String stageAlias(String stageName, TemplateOptions opt) {
        if (stageName == null) return opt.stageCnMap().getOrDefault("Running", "进行中");
        Map<String, String> localized =
                opt.stageAliasLocalized().getOrDefault(opt.locale() == null ? "" : opt.locale(), null);
        if (localized != null) {
            String v = localized.getOrDefault(stageName, null);
            if (v != null) return v;
        }
        String cn = opt.stageCnMap().getOrDefault(stageName, null);
        if (cn != null) return cn;
        if ("Region".equalsIgnoreCase(stageName)) return "区域";
        if ("Chunk".equalsIgnoreCase(stageName)) return "区块";
        if ("File".equalsIgnoreCase(stageName)) return "文件";
        if ("Done".equalsIgnoreCase(stageName)) return "完成";
        return "进行中";
    }
}
