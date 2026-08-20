package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.net.GeoIpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class GeoIpAccessService {
    /**
     * 单次登录时 GeoIP 查询的阻塞等待上限（毫秒）。
     *
     * <p>阻塞发生在异步的 AsyncPlayerPreLoginEvent 处理器线程（netty 线程），不会阻塞主线程。
     * 超时未拿到结果则 fail-open 放行，并告警到日志与群。</p>
     */
    public static final long DECISION_TIMEOUT_MS = 3_000L;

    /** 单条 IP 的国家码缓存时长。国家码基本不变，12h 足够，同时大幅减少对 geojs.io 的重复查询。 */
    private static final long CACHE_TTL_MS = Duration.ofHours(12).toMillis();

    /**
     * 缓存条目软上限；超过时在下一次写入后触发一次过期清理。
     *
     * <p>配合 12h TTL，缓存规模受「滚动 12h 内见过的唯一公网 IP 数」约束；
     * 即使全为未过期条目也只是暂时超出软上限，会在条目逐批过期后被回收。</p>
     */
    private static final int MAX_CACHE_ENTRIES = 4096;

    private record CacheEntry(GeoIpClient.GeoIpResult result, long expiresAtMillis) {}

    public record Decision(
            boolean allowed, String countryCode, List<String> allowList, String rawJson, boolean lookupFailed) {
        /** 兼容旧调用：默认查询未失败。 */
        public Decision(boolean allowed, String countryCode, List<String> allowList, String rawJson) {
            this(allowed, countryCode, allowList, rawJson, false);
        }
    }

    private final GeoIpClient client;
    private final TypedConfigProvider configs;
    private final long cacheTtlMillis;
    private final int maxCacheEntries;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public GeoIpAccessService(TypedConfigProvider configs) {
        this(new GeoIpClient(), configs, CACHE_TTL_MS, MAX_CACHE_ENTRIES);
    }

    GeoIpAccessService(GeoIpClient client, TypedConfigProvider configs) {
        this(client, configs, CACHE_TTL_MS, MAX_CACHE_ENTRIES);
    }

    GeoIpAccessService(GeoIpClient client, TypedConfigProvider configs, long cacheTtlMillis) {
        this(client, configs, cacheTtlMillis, MAX_CACHE_ENTRIES);
    }

    GeoIpAccessService(GeoIpClient client, TypedConfigProvider configs, long cacheTtlMillis, int maxCacheEntries) {
        this.client = client;
        this.configs = configs;
        this.cacheTtlMillis = cacheTtlMillis;
        this.maxCacheEntries = maxCacheEntries;
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
        GeoIpClient.GeoIpResult cached = cacheGet(ipAddress);
        if (cached != null) {
            return CompletableFuture.completedFuture(toDecision(cached, allow));
        }
        return client.lookup(ipAddress).handle((res, ex) -> {
            if (ex != null || res == null) {
                // 查询失败 fail-open 放行（可用性优先），不缓存，避免把临时故障误固定；
                // 置 lookupFailed=true，由调用方（PlayerEventService）私信告警管理员
                return new Decision(true, "", allow, "", true);
            }
            String cc = res.countryCode() == null ? "" : res.countryCode();
            if (cc.isEmpty()) {
                // geojs.io 返回了可解析但无国家码的响应（无法定位的保留段/云段 IP，
                // 或 429/5xx 的错误体恰好是可解析 JSON）。与超时/异常一致按 fail-open 放行，
                // 延续 2026-08-06 内网误拦教训：未知国家码不应静默误拦合法玩家；
                // 不缓存（避免误锁 12h），置 lookupFailed 告警管理员。
                return new Decision(true, "", allow, res.rawJson(), true);
            }
            cachePut(ipAddress, res);
            return toDecision(res, allow);
        });
    }

    private Decision toDecision(GeoIpClient.GeoIpResult res, List<String> allow) {
        String cc = res.countryCode() == null ? "" : res.countryCode();
        boolean ok = allow.contains(cc);
        return new Decision(ok, cc, allow, res.rawJson());
    }

    private GeoIpClient.GeoIpResult cacheGet(String ip) {
        CacheEntry entry = cache.get(ip);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis()) {
            cache.remove(ip, entry);
            return null;
        }
        return entry.result();
    }

    private void cachePut(String ip, GeoIpClient.GeoIpResult result) {
        cache.put(ip, new CacheEntry(result, System.currentTimeMillis() + cacheTtlMillis));
        if (cache.size() > maxCacheEntries) {
            evictExpired();
        }
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now > e.getValue().expiresAtMillis());
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
        // IPv4-mapped IPv6（::ffff:x.x.x.x）：剥离前缀后按 IPv4 判断，避免对注定失败的内网地址发起查询
        String candidate = lower.startsWith("::ffff:") ? ip.substring("::ffff:".length()) : ip;
        // 仅处理 IPv4
        String[] parts = candidate.split("\\.");
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
