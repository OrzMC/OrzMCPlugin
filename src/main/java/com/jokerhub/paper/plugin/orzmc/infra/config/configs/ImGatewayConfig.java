package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/**
 * IM 网关通道配置（im.yml）。
 *
 * <p>决定插件走外部 EasyBot 网关还是内置直连（方案：docs/dev/im-gateway-inhouse.md，D1/D2）。
 * 默认 {@code easybot}（现状兜底）；{@code builtin} 在尚未实现时由装配层按 D3 停用群功能并告警。</p>
 *
 * @param backend 消息通道：{@code easybot} 或 {@code builtin}（其他值视为 easybot）
 */
public record ImGatewayConfig(String backend) {

    /** 外部 EasyBot 网关通道（默认）。 */
    public static final String BACKEND_EASY = "easybot";

    /** 插件内置直连通道（内建 IM，按方案逐平台落地）。 */
    public static final String BACKEND_BUILTIN = "builtin";

    public ImGatewayConfig {
        backend = normalize(backend);
    }

    /** 是否选择内置直连通道。 */
    public boolean isBuiltin() {
        return BACKEND_BUILTIN.equals(backend);
    }

    private static String normalize(String value) {
        if (value == null) {
            return BACKEND_EASY;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return BACKEND_BUILTIN.equals(normalized) ? BACKEND_BUILTIN : BACKEND_EASY;
    }

    /** 从 im.yml 根配置段创建（null/缺失 → 全默认 easybot）。 */
    public static ImGatewayConfig from(ConfigurationSection cfg) {
        if (cfg == null) {
            return new ImGatewayConfig(BACKEND_EASY);
        }
        return new ImGatewayConfig(cfg.getString("backend", BACKEND_EASY));
    }
}
