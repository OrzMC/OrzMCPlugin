package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.net.GeoIpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * GeoIP 地区访问判定（登录前）。
 *
 * <p><b>2026-11 重构（#538「解析不可靠」）</b>：</p>
 * <ol>
 *   <li><b>多源兜底</b>：查询交给 {@link GeoIpClient} 的 provider 链（geojs → countryis → ipwhois），
 *       单源超时/限流不再直接落到「解析失败」；</li>
 *   <li><b>失败短缓存</b>：解析失败只缓存很短时间（{@code geoip.failure_cache_seconds}，默认 30s），
 *       防止上游抖动期同一 IP 反复打第三方把配额打光，同时不延长误拦（成功结果仍缓存 12h）；</li>
 *   <li><b>可观测</b>：成功/失败都打一行带「命中源 + 耗时」或「逐条失败原因」的日志，
 *       排查时不必再猜是超时、限流还是空响应。</li>
 * </ol>
 *
 * <p>失败策略保持原语义：{@code geoip.fail_open} 决定「无法验证地区」时放行（可用性优先）还是拒绝
 * （安全优先，默认）。</p>
 */
public final class GeoIpAccessService {
    /**
     * 单次登录时 GeoIP 查询的阻塞等待上限（毫秒）。
     *
     * <p>阻塞发生在异步的 AsyncPlayerPreLoginEvent 处理器线程（netty 线程），不会阻塞主线程。
     * 超时未拿到结果则按 {@code fail_open} 策略处理，并告警到日志与群。</p>
     */
    public static final long DECISION_TIMEOUT_MS = 3_000L;

    /** 留给「判定 + 调度」的余量：查询总预算 = 决策窗 - 该余量，确保结果在窗口内返回。 */
    private static final long DECISION_BUDGET_MARGIN_MS = 400L;

    /** 单条 IP 的国家码缓存时长。国家码基本不变，12h 足够，同时大幅减少对第三方源的重复查询。 */
    private static final long CACHE_TTL_MS = Duration.ofHours(12).toMillis();

    /**
     * 缓存条目软上限；超过时在下一次写入后触发一次过期清理。
     *
     * <p>配合 12h TTL，缓存规模受「滚动 12h 内见过的唯一公网 IP 数」约束；
     * 即使全为未过期条目也只是暂时超出软上限，会在条目逐批过期后被回收。</p>
     */
    private static final int MAX_CACHE_ENTRIES = 4096;

    /** 缓存条目：成功（长 TTL）与失败（短 TTL）分开计时，避免把临时故障固定成 12h 误拦。 */
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
    /** 失败短缓存（独立计时；{@code failureCacheSeconds<=0} 时不写）。 */
    private final ConcurrentMap<String, CacheEntry> failureCache = new ConcurrentHashMap<>();

    private final Logger logger;

    public GeoIpAccessService(TypedConfigProvider configs) {
        this(new GeoIpClient(), configs, CACHE_TTL_MS, MAX_CACHE_ENTRIES, null);
    }

    public GeoIpAccessService(TypedConfigProvider configs, Logger logger) {
        this(new GeoIpClient(), configs, CACHE_TTL_MS, MAX_CACHE_ENTRIES, logger);
    }

    GeoIpAccessService(GeoIpClient client, TypedConfigProvider configs) {
        this(client, configs, CACHE_TTL_MS, MAX_CACHE_ENTRIES, null);
    }

    GeoIpAccessService(GeoIpClient client, TypedConfigProvider configs, long cacheTtlMillis) {
        this(client, configs, cacheTtlMillis, MAX_CACHE_ENTRIES, null);
    }

    GeoIpAccessService(GeoIpClient client, TypedConfigProvider configs, long cacheTtlMillis, int maxCacheEntries) {
        this(client, configs, cacheTtlMillis, maxCacheEntries, null);
    }

    GeoIpAccessService(
            GeoIpClient client, TypedConfigProvider configs, long cacheTtlMillis, int maxCacheEntries, Logger logger) {
        this.client = client;
        this.configs = configs;
        this.cacheTtlMillis = cacheTtlMillis;
        this.maxCacheEntries = maxCacheEntries;
        this.logger = logger;
    }

    public CompletableFuture<Decision> decide(String ipAddress) {
        IpWhitelist whitelist = configs.ipWhitelist();
        List<String> allow = whitelist.allowCountryCode();
        boolean failOpen = whitelist.failOpen();
        if (allow.isEmpty()) {
            return CompletableFuture.completedFuture(new Decision(true, "", allow, ""));
        }
        if (isPrivateIp(ipAddress)) {
            // 内网/环回/CGNAT 地址直接放行，不触发 GeoIP 查询（第三方无法解析私有段，
            // 会返回未知国家码导致白名单误拦截内网用户，2026-08-06 MCSM 线上问题）
            return CompletableFuture.completedFuture(new Decision(true, "", allow, ""));
        }
        GeoIpClient.GeoIpResult cached = cacheGet(ipAddress);
        if (cached != null) {
            return CompletableFuture.completedFuture(toDecision(cached, allow));
        }
        CacheEntry failedEntry = failureCacheGet(ipAddress);
        if (failedEntry != null) {
            // 失败短缓存命中：不再打第三方（上游抖动时保护配额），直接按 fail_open 策略处置
            return CompletableFuture.completedFuture(new Decision(failOpen, "", allow, "", true));
        }
        long startNanos = System.nanoTime();
        Duration perProvider = Duration.ofMillis(whitelist.perProviderTimeoutMs());
        Duration totalBudget = Duration.ofMillis(Math.max(200L, DECISION_TIMEOUT_MS - DECISION_BUDGET_MARGIN_MS));
        return client.lookup(ipAddress, whitelist.resolvedProviders(), perProvider, totalBudget)
                .handle((res, ex) -> {
                    if (ex != null || res == null) {
                        // 查询失败：短缓存 + 一次性告警（带逐条 provider 失败原因）
                        cacheFailure(ipAddress, whitelist.failureCacheSeconds());
                        logWarning("[geoip] " + ipAddress + " 解析失败（按 fail-" + (failOpen ? "open 放行" : "close 拒绝")
                                + "）: " + describe(ex));
                        return new Decision(failOpen, "", allow, "", true);
                    }
                    String cc = res.countryCode() == null ? "" : res.countryCode();
                    if (cc.isEmpty()) {
                        // 拿到了响应但没有国家码（保留段/云段/第三方错误体）：同样按 fail_open 处置
                        cacheFailure(ipAddress, whitelist.failureCacheSeconds());
                        logWarning("[geoip] " + ipAddress + " 无国家码（源 " + res.provider() + "，按 fail-"
                                + (failOpen ? "open 放行" : "close 拒绝") + "）");
                        return new Decision(failOpen, "", allow, res.rawJson(), true);
                    }
                    cachePut(ipAddress, res);
                    logLookupSuccess(ipAddress, res, startNanos);
                    return toDecision(res, allow);
                });
    }

    /** 成功查询的一行观测：IP → 国家码，命中源与耗时（跨源兜底时尤其需要）。 */
    private void logLookupSuccess(String ipAddress, GeoIpClient.GeoIpResult res, long startNanos) {
        if (logger == null) {
            return;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        boolean fellBack = !GeoIpClient.DEFAULT_PROVIDERS.isEmpty()
                && !GeoIpClient.DEFAULT_PROVIDERS.get(0).id().equals(res.provider());
        // 主源命中走 FINE（正常路径，避免刷屏）；兜底命中走 INFO（值得关注的降级信号）
        if (fellBack) {
            logger.info("[geoip] " + ipAddress + " → " + res.countryCode() + "（主源失败，兜底源 " + res.provider() + "，"
                    + elapsedMs + "ms）");
        } else if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine("[geoip] " + ipAddress + " → " + res.countryCode() + "（" + res.provider() + "，" + elapsedMs
                    + "ms）");
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    /** 失败原因摘要（{@link GeoIpClient.LookupException} 带逐条尝试，其他异常退化为类型+消息）。 */
    private static String describe(Throwable ex) {
        if (ex == null) {
            return "无结果";
        }
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur && !(cur instanceof GeoIpClient.LookupException)) {
            cur = cur.getCause();
        }
        if (cur instanceof GeoIpClient.LookupException lookupException) {
            return String.join("; ", lookupException.attempts());
        }
        String message = cur.getMessage();
        return cur.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : "(" + message + ")");
    }

    private Decision toDecision(GeoIpClient.GeoIpResult res, List<String> allow) {
        String cc = res.countryCode() == null ? "" : res.countryCode();
        // 配置侧在 IpWhitelist.from 已归一化为大写，比对仍用 equalsIgnoreCase 兜底：
        // 上游返回小写国家码时也不会因大小写分叉而误拦全服（fail-close 下硬锁服）。
        boolean ok = allow.stream().anyMatch(a -> a != null && a.equalsIgnoreCase(cc));
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

    private CacheEntry failureCacheGet(String ip) {
        CacheEntry entry = failureCache.get(ip);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis()) {
            failureCache.remove(ip, entry);
            return null;
        }
        return entry;
    }

    /** 失败短缓存（{@code seconds<=0} 时不缓存，保持「每次重试都问一次」的旧行为）。 */
    private void cacheFailure(String ip, int seconds) {
        if (seconds <= 0) {
            return;
        }
        failureCache.put(
                ip, new CacheEntry(new GeoIpClient.GeoIpResult("", ""), System.currentTimeMillis() + seconds * 1000L));
        if (failureCache.size() > maxCacheEntries) {
            long now = System.currentTimeMillis();
            failureCache.entrySet().removeIf(e -> now > e.getValue().expiresAtMillis());
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
        // IPv4-mapped IPv6（::ffff:x.x.x.x)：剥离前缀后按 IPv4 判断，避免对注定失败的内网地址发起查询
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
