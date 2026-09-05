package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * GeoIP 地区白名单配置（config.yml {@code geoip:} 段）。
 *
 * <p>{@code failOpen} 决定 GeoIP 查询失败（上游异常/超时/空国家码）时的登录策略：</p>
 * <ul>
 *   <li>{@code false}（默认，fail-close）：无法验证地区 → 拒绝进入（安全优先，防绕过地区白名单）；</li>
 *   <li>{@code true}（fail-open）：无法验证地区 → 放行并告警管理员（可用性优先，防误拦）。</li>
 * </ul>
 */
public record IpWhitelist(List<String> allowCountryCode, boolean failOpen) {

    /** 兼容单参构造：默认 fail-close（安全优先）。 */
    public IpWhitelist(List<String> allowCountryCode) {
        this(allowCountryCode, false);
    }

    public static IpWhitelist from(ConfigurationSection cfg) {
        List<String> list = new ArrayList<>();
        boolean failOpen = false;
        if (cfg != null) {
            Object raw = cfg.get("allow_country_code");
            if (raw instanceof List<?> l) {
                for (Object o : l) {
                    if (o == null) continue;
                    String cc = String.valueOf(o).trim().toUpperCase(Locale.ROOT);
                    if (!cc.isEmpty()) list.add(cc);
                }
            }
            failOpen = cfg.getBoolean("fail_open", false);
        }
        return new IpWhitelist(list, failOpen);
    }

    /**
     * 启动健康校验：段缺失为硬缺失；国家码按「配置应整洁」口径要求大写两位码
     * （{@code from} 会宽容 trim+大写兜底，校验仍提示原始写法——避免「运行时生效、配置却脏」）。
     */
    public static void validate(ConfigurationSection section, List<String> issues) {
        if (section == null) {
            issues.add("config.yml 缺失 geoip 配置段");
            return;
        }
        Object raw = section.get("allow_country_code");
        if (raw == null) {
            issues.add("建议: geoip.allow_country_code 未配置，默认允许所有地区");
        } else if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    issues.add("非法: geoip.allow_country_code 不允许空项");
                } else {
                    String code = String.valueOf(o);
                    if (!code.matches("^[A-Z]{2}$")) {
                        issues.add("非法: geoip.allow_country_code '" + code + "' 必须为大写两位国家码");
                    }
                }
            }
        } else {
            issues.add("类型错误: geoip.allow_country_code 需为列表");
        }
    }
}
