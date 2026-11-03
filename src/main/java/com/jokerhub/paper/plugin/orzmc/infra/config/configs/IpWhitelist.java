package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import com.jokerhub.paper.plugin.orzmc.infra.net.GeoIpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * GeoIP 地区白名单配置（config.yml {@code geoip:} 段）。
 *
 * <p>{@code failOpen} 决定 GeoIP 查询失败（所有 provider 异常/超时/空国家码）时的登录策略：</p>
 * <ul>
 *   <li>{@code false}（默认，fail-close）：无法验证地区 → 拒绝进入（安全优先，防绕过地区白名单）；</li>
 *   <li>{@code true}（fail-open）：无法验证地区 → 放行并告警管理员（可用性优先，防误拦）。</li>
 * </ul>
 *
 * <p><b>多源兜底（2026-11，#538）</b>：{@code providers} 是依次尝试的第三方源（见
 * {@link GeoIpClient.Provider}），首个拿到国家码的胜出——单源抖动不再直接导致登录失败。
 * {@code perProviderTimeoutMs} 限制单个源的单次耗时，{@code failureCacheSeconds} 是**解析失败**的短缓存
 * （避免上游抖动期同一 IP 反复重试、把配额打光）。</p>
 */
public record IpWhitelist(
        List<String> allowCountryCode,
        boolean failOpen,
        List<String> providers,
        long perProviderTimeoutMs,
        int failureCacheSeconds) {

    /** 单 provider 超时默认值（毫秒）：登录决策窗 3s 内要能切 2~3 家。 */
    public static final long DEFAULT_PER_PROVIDER_TIMEOUT_MS = 1200L;

    /** 失败短缓存默认值（秒）：只挡住「同一 IP 在抖动期反复重试」，不延长误拦。 */
    public static final int DEFAULT_FAILURE_CACHE_SECONDS = 30;

    /** 默认 provider 链（配置留空时使用）。 */
    public static final List<String> DEFAULT_PROVIDERS =
            GeoIpClient.DEFAULT_PROVIDERS.stream().map(GeoIpClient.Provider::id).toList();

    /** 兼容两参构造：provider 链/超时/失败缓存取默认值。 */
    public IpWhitelist(List<String> allowCountryCode, boolean failOpen) {
        this(
                allowCountryCode,
                failOpen,
                DEFAULT_PROVIDERS,
                DEFAULT_PER_PROVIDER_TIMEOUT_MS,
                DEFAULT_FAILURE_CACHE_SECONDS);
    }

    /** 兼容单参构造：默认 fail-close（安全优先）。 */
    public IpWhitelist(List<String> allowCountryCode) {
        this(allowCountryCode, false);
    }

    /** 解析后的 provider 链（未知名字已忽略；为空 → 回退默认链）。 */
    public List<GeoIpClient.Provider> resolvedProviders() {
        List<GeoIpClient.Provider> parsed = GeoIpClient.parseProviders(providers);
        return parsed.isEmpty() ? GeoIpClient.DEFAULT_PROVIDERS : parsed;
    }

    public static IpWhitelist from(ConfigurationSection cfg) {
        List<String> list = new ArrayList<>();
        List<String> providerNames = new ArrayList<>(DEFAULT_PROVIDERS);
        long perProviderTimeoutMs = DEFAULT_PER_PROVIDER_TIMEOUT_MS;
        int failureCacheSeconds = DEFAULT_FAILURE_CACHE_SECONDS;
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
            // providers：显式配置才覆盖默认（留空 = 用默认链，不清空兜底能力）
            Object providersRaw = cfg.get("providers");
            if (providersRaw instanceof List<?> pl && !pl.isEmpty()) {
                providerNames.clear();
                for (Object o : pl) {
                    if (o == null) continue;
                    String name = String.valueOf(o).trim();
                    if (!name.isEmpty()) providerNames.add(name);
                }
            }
            long timeout = cfg.getLong("per_provider_timeout_ms", DEFAULT_PER_PROVIDER_TIMEOUT_MS);
            perProviderTimeoutMs = timeout > 0 ? timeout : DEFAULT_PER_PROVIDER_TIMEOUT_MS;
            int failureCache = cfg.getInt("failure_cache_seconds", DEFAULT_FAILURE_CACHE_SECONDS);
            failureCacheSeconds = Math.max(0, failureCache);
        }
        return new IpWhitelist(list, failOpen, List.copyOf(providerNames), perProviderTimeoutMs, failureCacheSeconds);
    }

    /**
     * 启动健康校验：段缺失为硬缺失；国家码按「配置应整洁」口径要求大写两位码
     * （{@code from} 会宽容 trim+大写兜底，校验仍提示原始写法——避免「运行时生效、配置却脏」）。
     *
     * <p>多源相关键同样校验：未知 provider 名/超时越界会在启动报告里点出来，
     * 避免「写错了 provider 名却静默只用默认链」导致排障时看不出问题。</p>
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

        Object providersRaw = section.get("providers");
        if (providersRaw != null) {
            if (!(providersRaw instanceof List<?> providers)) {
                issues.add("类型错误: geoip.providers 需为字符串列表，例如 [geojs, countryis, ipwhois]");
            } else {
                for (Object o : providers) {
                    String name = o == null ? "" : String.valueOf(o).trim();
                    if (GeoIpClient.Provider.parse(name) == null) {
                        issues.add("非法: geoip.providers 含未知源 '" + name + "'（可选: "
                                + String.join(", ", GeoIpClient.providerIds()) + "）");
                    }
                }
            }
        }

        long timeout = section.getLong("per_provider_timeout_ms", DEFAULT_PER_PROVIDER_TIMEOUT_MS);
        if (timeout <= 0 || timeout > 3_000L) {
            issues.add("非法: geoip.per_provider_timeout_ms 需在 1~3000 之间（登录决策窗只有 3s），当前 " + timeout);
        }
        int failureCache = section.getInt("failure_cache_seconds", DEFAULT_FAILURE_CACHE_SECONDS);
        if (failureCache < 0 || failureCache > 600) {
            issues.add("非法: geoip.failure_cache_seconds 需在 0~600 之间（0 = 关闭失败缓存），当前 " + failureCache);
        }
    }
}
