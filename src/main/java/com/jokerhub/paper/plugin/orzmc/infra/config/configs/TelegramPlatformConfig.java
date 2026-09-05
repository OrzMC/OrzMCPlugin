package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import org.bukkit.configuration.ConfigurationSection;

/**
 * builtin Telegram 平台配置（im.yml {@code platforms.telegram}，批次 5a）。
 *
 * <p>backend=builtin 时以此判断 Telegram 平台是否可启用：{@code enabled} 且 bot token 齐备
 * （凭据为空 = 不可用，D3 语义——无任何可用平台时整体停群）。凭据安全（R5）：本类不打印任何值。</p>
 *
 * @param enabled 是否启用 Telegram 平台
 * @param token BotFather 下发的 bot token（{@code <bot_id>:<auth>}；长期有效，401=配置错误）
 * @param proxy 生效代理（平台级覆盖优先，否则全局段；DIRECT = 直连）。从 {@link #from} 解析时已合并。
 */
public record TelegramPlatformConfig(boolean enabled, String token, ImProxyConfig proxy) {

    /** 默认（禁用，无凭据，无代理覆盖）。 */
    public static final TelegramPlatformConfig DISABLED = new TelegramPlatformConfig(false, "", ImProxyConfig.DIRECT);

    /** 是否「已启用且凭据齐备」（可作为 builtin 可用平台）。 */
    public boolean usable() {
        return enabled && !isBlank(token);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 从 {@code platforms.telegram} 段创建（全局 proxy 段由调用方传入合并）；null/缺失 → 禁用。 */
    public static TelegramPlatformConfig from(ConfigurationSection section, ConfigurationSection globalProxySection) {
        if (section == null) {
            return DISABLED;
        }
        String token = section.getString("token", "");
        ConfigurationSection proxySection = section.getConfigurationSection("proxy");
        // 平台级代理段覆盖全局（resolve：平台段存在则用平台段，否则全局段；两者均无 → 直连）
        ImProxyConfig proxy;
        if (proxySection != null) {
            proxy = ImProxyConfig.from(proxySection);
        } else if (globalProxySection != null) {
            proxy = ImProxyConfig.from(globalProxySection);
        } else {
            proxy = ImProxyConfig.DIRECT;
        }
        return new TelegramPlatformConfig(section.getBoolean("enabled", false), token == null ? "" : token, proxy);
    }

    /** 从 {@code platforms.telegram} 段创建（无全局段，代理仅看平台级）；null/缺失 → 禁用。 */
    public static TelegramPlatformConfig from(ConfigurationSection section) {
        return from(section, null);
    }
}
