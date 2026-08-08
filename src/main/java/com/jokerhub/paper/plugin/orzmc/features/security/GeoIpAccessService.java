package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.net.GeoIpClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GeoIpAccessService {
    /**
     * 单次登录时 GeoIP 查询的阻塞等待上限（毫秒）。
     *
     * <p>阻塞发生在异步的 AsyncPlayerPreLoginEvent 处理器线程（netty 线程），不会阻塞主线程。
     * 超时未拿到结果则 fail-open 放行，并告警到日志与群。</p>
     */
    public static final long DECISION_TIMEOUT_MS = 3_000L;

    public record Decision(boolean allowed, String countryCode, List<String> allowList, String rawJson) {}

    private final GeoIpClient client;
    private final TypedConfigProvider configs;

    public GeoIpAccessService(TypedConfigProvider configs) {
        this(new GeoIpClient(), configs);
    }

    GeoIpAccessService(GeoIpClient client, TypedConfigProvider configs) {
        this.client = client;
        this.configs = configs;
    }

    public CompletableFuture<Decision> decide(String ipAddress) {
        List<String> allow = configs.ipWhitelist().allowCountryCode();
        if (allow.isEmpty()) {
            return CompletableFuture.completedFuture(new Decision(true, "", allow, ""));
        }
        if (isPrivateIp(ipAddress)) {
            // 内网/环回/CGNAT 地址直接放行，不触发 GeoIP 查询（geojs.io 无法解析私有段，
            // 会返回未知国家码导致白名单误拦截内网用户，2026-08-06 MCSM 线上问题）
            return CompletableFuture.completedFuture(new Decision(true, "", allow, ""));
        }
        return client.lookup(ipAddress).handle((res, ex) -> {
            if (ex != null || res == null) {
                return new Decision(true, "", allow, "");
            }
            String cc = res.countryCode() == null ? "" : res.countryCode();
            boolean ok = allow.contains(cc);
            return new Decision(ok, cc, allow, res.rawJson());
        });
    }

    /** 判断是否为内网/私有/特殊用途 IPv4 地址（RFC1918 + 环回 + CGNAT + 链路本地）。 */
    static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // IPv6 链路本地/唯一本地直接放行
        String lower = ip.toLowerCase();
        if (lower.startsWith("fc")
                || lower.startsWith("fd")
                || lower.startsWith("fe8")
                || lower.startsWith("fe9")
                || lower.startsWith("fea")
                || lower.startsWith("feb")
                || lower.startsWith("::1")
                || "::".equals(lower)) {
            return true;
        }
        // 仅处理 IPv4
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 10) {
                return true; // 10.0.0.0/8
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true; // 172.16.0.0/12
            }
            if (a == 192 && b == 168) {
                return true; // 192.168.0.0/16
            }
            if (a == 127) {
                return true; // 127.0.0.0/8 环回
            }
            if (a == 100 && b >= 64 && b <= 127) {
                return true; // 100.64.0.0/10 CGNAT（运营商大内网）
            }
            if (a == 169 && b == 254) {
                return true; // 169.254.0.0/16 链路本地
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }
}
