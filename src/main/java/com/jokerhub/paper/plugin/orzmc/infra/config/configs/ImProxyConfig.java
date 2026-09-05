package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import org.bukkit.configuration.ConfigurationSection;

/**
 * builtin 平台网络代理配置（im.yml，方案 D13，批次 5 起通用能力）。
 *
 * <p>四平台（QQ/飞书/Telegram/Discord）可选走 HTTP 代理：顶层全局兜底 + 平台级
 * {@code platforms.<id>.proxy} 覆盖。两者均以 {@code enabled} 门控——<b>不配置 proxy 段或
 * {@code enabled: false} 一律直连</b>。用途：Telegram/Discord 国内服务器出墙必需；QQ/飞书
 * 海外部署（访问国内 API 不稳/被挡）可配代理回国。平台级覆盖规则：平台段配了 proxy 段
 * （无论 enabled 与否）→ 用平台段；否则用全局段。</p>
 *
 * <p>凭据安全（R5）：本类不打印任何值。仅支持 HTTP/HTTPS 代理（{@code type} 预留，默认 http）。</p>
 *
 * @param enabled 是否启用代理（false/缺失 → 直连）
 * @param type 代理类型（{@code http} 默认；预留 socks 未落地）
 * @param host 代理主机
 * @param port 代理端口
 */
public record ImProxyConfig(boolean enabled, String type, String host, int port) {

    /** 直连（不启用代理）。 */
    public static final ImProxyConfig DIRECT = new ImProxyConfig(false, "", "", 0);

    /** 是否真正生效（enabled 且 host/port 齐备）。 */
    public boolean effective() {
        return enabled && !isBlank(host) && port > 0 && port < 65536;
    }

    /** 转为 {@link java.net.Proxy}（{@link #effective()} 为 false → {@link Proxy#NO_PROXY}）。 */
    public Proxy toProxy() {
        if (!effective()) {
            return Proxy.NO_PROXY;
        }
        return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
    }

    /** 转为 JDK {@link ProxySelector}（{@link #effective()} 为 false → null 等价直连）。 */
    public ProxySelector toProxySelector() {
        if (!effective()) {
            return null;
        }
        return ProxySelector.of(InetSocketAddress.createUnresolved(host, port));
    }

    /**
     * 解析「全局 proxy 段 + 平台级覆盖段」→ 生效代理配置。
     *
     * @param globalSection im.yml 顶层 {@code proxy} 段（可为 null）
     * @param platformSection 平台 {@code platforms.<id>.proxy} 段（可为 null）
     * @return 平台段存在 → 平台段解析；否则全局段；两者均缺 → {@link #DIRECT}
     */
    public static ImProxyConfig resolve(ConfigurationSection globalSection, ConfigurationSection platformSection) {
        if (platformSection != null) {
            return from(platformSection);
        }
        return from(globalSection);
    }

    /** 从 {@code proxy} 配置段创建；null/缺失 → 直连。 */
    public static ImProxyConfig from(ConfigurationSection section) {
        if (section == null) {
            return DIRECT;
        }
        String type = section.getString("type", "http");
        String host = section.getString("host", "");
        int port = section.getInt("port", 0);
        return new ImProxyConfig(
                section.getBoolean("enabled", false), type == null ? "" : type, host == null ? "" : host, port);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
