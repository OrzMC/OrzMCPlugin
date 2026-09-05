package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record CommandPolicies(Map<String, CommandPolicy> policies) {

    public static CommandPolicies from(ConfigurationSection cfg) {
        Map<String, CommandPolicy> policies = new HashMap<>();
        if (cfg == null) return new CommandPolicies(policies);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection s = cfg.getConfigurationSection(key);
            if (s != null) {
                int cooldown = s.getInt("cooldown_secs", 0);
                boolean adminOnly = s.getBoolean("admin_only", false);
                policies.put(key, new CommandPolicy(cooldown, adminOnly));
            }
        }
        return new CommandPolicies(policies);
    }

    /** 启动健康校验：段缺失为硬缺失；逐命令校验 cooldown_secs/admin_only 类型与取值范围。 */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 command_policies 配置段");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                issues.add("非法: command_policies." + key + " 需为对象");
                continue;
            }
            Object cd = s.get("cooldown_secs");
            if (cd != null) {
                try {
                    int val = Integer.parseInt(String.valueOf(cd));
                    if (val < 0) issues.add("非法: command_policies." + key + ".cooldown_secs 不得为负数");
                } catch (Exception e) {
                    issues.add("类型错误: command_policies." + key + ".cooldown_secs 需为数字");
                }
            }
            Object ao = s.get("admin_only");
            if (ao != null && !(ao instanceof Boolean))
                issues.add("类型错误: command_policies." + key + ".admin_only 需为布尔值");
        }
    }
}
