package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public record WhitelistConfig(boolean forceWhitelist, int cleanupInactiveDays, int paginationDelayTicks) {

    public static WhitelistConfig from(ConfigurationSection cfg) {
        if (cfg == null) return new WhitelistConfig(true, 90, 5);
        boolean forceWhitelist = cfg.getBoolean("force_whitelist", true);
        int cleanupInactiveDays = cfg.getInt("cleanup_inactive_days", 90);
        int paginationDelayTicks = cfg.getInt("pagination_delay_ticks", 5);
        return new WhitelistConfig(forceWhitelist, cleanupInactiveDays, paginationDelayTicks);
    }

    /** 启动健康校验（本 record 只管标量字段）：段缺失为硬缺失；踢出文案段由 {@link WhitelistKickMessage} 校验。 */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 whitelist 配置段");
            return;
        }
        Object fw = section.get("force_whitelist");
        if (!(fw instanceof Boolean)) issues.add("类型错误: whitelist.force_whitelist 需为布尔值");
        int days = section.getInt("cleanup_inactive_days", 90);
        if (days <= 0) issues.add("非法: whitelist.cleanup_inactive_days 必须为正数");
        int ticks = section.getInt("pagination_delay_ticks", 5);
        if (ticks < 0) issues.add("非法: whitelist.pagination_delay_ticks 不得为负数");
    }
}
