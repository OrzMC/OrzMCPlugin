package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

/**
 * builtin 飞书平台配置（im.yml {@code platforms.feishu}，方案 §4.1）。
 *
 * <p>backend=builtin 时以此判断飞书平台是否可启用：{@code enabled} 且 app_id / app_secret 齐备
 * （凭据为空 = 不可用，D3 语义——无任何可用平台时整体停群）。凭据安全（R5）：本类不打印任何值。</p>
 *
 * @param enabled 是否启用飞书平台
 * @param appId 飞书开放平台 app_id（cli_ 前缀）
 * @param appSecret 飞书开放平台 app_secret
 * @param proxy 生效代理（平台级覆盖优先，否则全局段；DIRECT = 直连）。从 {@link #from} 解析时已合并。
 */
public record FeishuPlatformConfig(boolean enabled, String appId, String appSecret, ImProxyConfig proxy) {

    /** 便捷：无代理（直连；老调用兼容）。 */
    public FeishuPlatformConfig(boolean enabled, String appId, String appSecret) {
        this(enabled, appId, appSecret, ImProxyConfig.DIRECT);
    }

    /** 默认（禁用，无凭据，直连）。 */
    public static final FeishuPlatformConfig DISABLED = new FeishuPlatformConfig(false, "", "");

    /** 是否「已启用且凭据齐备」（可作为 builtin 可用平台）。 */
    public boolean usable() {
        return enabled && !isBlank(appId) && !isBlank(appSecret);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 从 {@code platforms.feishu} 段创建（无全局段，代理仅看平台级）；null/缺失 → 禁用。 */
    public static FeishuPlatformConfig from(ConfigurationSection section) {
        return from(section, null);
    }

    /** 从 {@code platforms.feishu} 段创建（全局 proxy 段由调用方传入合并）；null/缺失 → 禁用。 */
    public static FeishuPlatformConfig from(ConfigurationSection section, ConfigurationSection globalProxySection) {
        if (section == null) {
            return DISABLED;
        }
        String appId = section.getString("app_id", "");
        String secret = section.getString("app_secret", "");
        ConfigurationSection proxySection = section.getConfigurationSection("proxy");
        // 平台级代理段覆盖全局（平台段存在则用平台段，否则全局段；两者均无 → 直连）
        ImProxyConfig proxy;
        if (proxySection != null) {
            proxy = ImProxyConfig.from(proxySection);
        } else if (globalProxySection != null) {
            proxy = ImProxyConfig.from(globalProxySection);
        } else {
            proxy = ImProxyConfig.DIRECT;
        }
        return new FeishuPlatformConfig(
                section.getBoolean("enabled", false), appId == null ? "" : appId, secret == null ? "" : secret, proxy);
    }
}
