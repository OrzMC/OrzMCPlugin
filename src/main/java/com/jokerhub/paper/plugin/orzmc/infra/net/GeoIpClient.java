package com.jokerhub.paper.plugin.orzmc.infra.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 国家码查询客户端（多 provider 兜底，2026-11 针对 #538「GeoIP 解析不可靠」重构）。
 *
 * <p><b>为什么要多源</b>：原实现只打一个第三方（{@code get.geojs.io}），该源超时/限流/下线时
 * 每次登录都会走「解析失败」分支——默认 fail-close 下就是<b>把正常玩家踢掉</b>
 * （线上实测：同一玩家 14:24 被拒、14:25 重试才进来）。现在按配置顺序依次尝试，
 * 首个拿到非空国家码的 provider 胜出，单源故障不再直接导致登录失败。</p>
 *
 * <p><b>时间预算</b>：passive 链路有硬上限——登录决策只等 {@code GeoIpAccessService.DECISION_TIMEOUT_MS}
 * （3s）。因此本类在<b>总预算</b>内依次尝试（每个 provider 另有单次上限，且自动收缩到剩余预算），
 * 预算耗尽立即失败，绝不让「多试几家」把决策窗口撑爆（撑爆= 玩家被 fail-close 踢掉，等于没修）。</p>
 *
 * <p><b>可观测</b>：结果里带命中来源（{@link GeoIpResult#provider()}）；全部失败时抛
 * {@link LookupException} 并携带逐条尝试原因，调用方可一次性告警/落日志，
 * 不必再猜「是超时还是限流还是空响应」。</p>
 *
 * <p><b>隐私/安全</b>：仅查询国家码，不使用其余字段；不记录完整响应到玩家可见位置。</p>
 */
public final class GeoIpClient {

    /** 默认 provider 链：国内可达性较好的三家（均 HTTPS、无需 API key）。 */
    public static final List<Provider> DEFAULT_PROVIDERS =
            List.of(Provider.GEOJS, Provider.COUNTRY_IS, Provider.IPWHOIS);

    /** 单 provider 默认超时：决策窗 3s 内要能跑完 2~3 家，故单次压到 1.2s（失败快速切换）。 */
    public static final Duration DEFAULT_PER_PROVIDER_TIMEOUT = Duration.ofMillis(1200);

    /** 单次尝试的最短有意义预算；剩余不足时直接放弃后续 provider（省一次注定超时的请求）。 */
    static final long MIN_ATTEMPT_BUDGET_MS = 250L;

    /**
     * 查询结果。
     *
     * @param countryCode ISO 3166-1 alpha-2（大写；无法定位时为空串）
     * @param rawJson 命中的原始响应（仅供日志/排查，含第三方字段）
     * @param provider 命中的 provider 标识（{@code geojs}/{@code countryis}/{@code ipwhois}）
     */
    public record GeoIpResult(String countryCode, String rawJson, String provider) {
        /** 兼容旧调用（provider 未知）。 */
        public GeoIpResult(String countryCode, String rawJson) {
            this(countryCode, rawJson, "unknown");
        }

        public GeoIpResult {
            countryCode = countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
        }
    }

    /** 支持的第三方源；{@code id} 是配置里可写的名字。 */
    public enum Provider {
        /** get.geojs.io（默认首选，字段 {@code country_code}）。 */
        GEOJS("geojs", "https://get.geojs.io/v1/ip/geo/%s.json", "country_code"),
        /** api.country.is（极简，字段 {@code country}）。 */
        COUNTRY_IS("countryis", "https://api.country.is/%s", "country"),
        /** ipwho.is（字段 {@code country_code}，失败时 {@code success=false}）。 */
        IPWHOIS("ipwhois", "https://ipwho.is/%s", "country_code");

        private final String id;
        private final String urlTemplate;
        private final String countryField;

        Provider(String id, String urlTemplate, String countryField) {
            this.id = id;
            this.urlTemplate = urlTemplate;
            this.countryField = countryField;
        }

        public String id() {
            return id;
        }

        String url(String ipAddress) {
            return String.format(urlTemplate, ipAddress);
        }

        /**
         * 从响应体解析国家码（解析失败/字段缺失/显式失败标记 → 空串，由调用方当作「本次尝试失败」）。
         *
         * <p>{@code ipwho.is} 会以 HTTP 200 + {@code success:false} 表达失败（如保留段），
         * 必须显式判掉，否则会被当成「无国家码」而误记为成功响应。</p>
         */
        String extractCountryCode(String body) {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("success") && !json.get("success").getAsBoolean()) {
                return "";
            }
            if (!json.has(countryField) || json.get(countryField).isJsonNull()) {
                return "";
            }
            return json.get(countryField).getAsString();
        }

        /** 配置名 → provider（大小写不敏感）；未知返回 {@code null}。 */
        public static Provider parse(String name) {
            if (name == null) {
                return null;
            }
            String want = name.trim().toLowerCase(Locale.ROOT);
            for (Provider p : values()) {
                if (p.id.equals(want)) {
                    return p;
                }
            }
            return null;
        }
    }

    /** 全部 provider 都失败（含预算耗尽）。{@link #attempts()} 记录逐条原因，便于一次性告警。 */
    public static final class LookupException extends RuntimeException {
        private final List<String> attempts;

        LookupException(String ipAddress, List<String> attempts) {
            super("GeoIP 查询失败 " + ipAddress + "（" + String.join("; ", attempts) + "）");
            this.attempts = List.copyOf(attempts);
        }

        public List<String> attempts() {
            return attempts;
        }
    }

    private final Duration perProviderTimeout;

    public GeoIpClient() {
        this(DEFAULT_PER_PROVIDER_TIMEOUT);
    }

    public GeoIpClient(Duration perProviderTimeout) {
        this.perProviderTimeout = perProviderTimeout == null ? DEFAULT_PER_PROVIDER_TIMEOUT : perProviderTimeout;
    }

    /** 默认链 + 默认单次超时（通用预算见 {@link #lookup(String, List, Duration, Duration)}）。 */
    public CompletableFuture<GeoIpResult> lookup(String ipAddress) {
        return lookup(ipAddress, DEFAULT_PROVIDERS, perProviderTimeout, Duration.ofSeconds(3));
    }

    /**
     * 按顺序尝试 provider，首个拿到非空国家码者返回。
     *
     * @param providers 已解析的 provider 链（空 → 视为配置错误，直接失败）
     * @param perProviderTimeoutMs 单个 provider 的超时上限（毫秒，{@code <=0} → 用默认）
     * @param totalBudget 总预算（必须小于登录决策窗口；预算耗尽立即失败）
     */
    public CompletableFuture<GeoIpResult> lookup(
            String ipAddress, List<Provider> providers, Duration perProviderTimeoutMs, Duration totalBudget) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("ipAddress must not be blank"));
        }
        List<Provider> chain = providers == null ? List.of() : List.copyOf(providers);
        Duration perProvider =
                (perProviderTimeoutMs == null || perProviderTimeoutMs.isZero() || perProviderTimeoutMs.isNegative())
                        ? perProviderTimeout
                        : perProviderTimeoutMs;
        long budgetMs = Math.max(0L, totalBudget == null ? 3000L : totalBudget.toMillis());
        long deadline = System.nanoTime() + Duration.ofMillis(budgetMs).toNanos();
        return attempt(ipAddress, chain, 0, perProvider, deadline, new ArrayList<>());
    }

    /**
     * 依次尝试（递归 + {@code thenCompose}，不阻塞任何线程）。
     *
     * @param attempts 逐条失败原因（同步收集；链是串行推进的，无并发写）
     */
    private CompletableFuture<GeoIpResult> attempt(
            String ipAddress,
            List<Provider> chain,
            int index,
            Duration perProviderTimeout,
            long deadlineNanos,
            List<String> attempts) {
        if (index >= chain.size()) {
            return CompletableFuture.failedFuture(new LookupException(ipAddress, attempts));
        }
        long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        Provider provider = chain.get(index);
        if (remainingMs < MIN_ATTEMPT_BUDGET_MS) {
            attempts.add(provider.id() + ": budget exhausted（剩余 " + Math.max(0, remainingMs) + "ms）");
            return CompletableFuture.failedFuture(new LookupException(ipAddress, attempts));
        }
        Duration timeout = Duration.ofMillis(Math.min(perProviderTimeout.toMillis(), remainingMs));
        return fetch(provider, ipAddress, timeout)
                .handle((result, ex) -> {
                    if (ex == null && result != null && !result.countryCode().isBlank()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    attempts.add(provider.id() + ": " + (ex != null ? rootMessage(ex) : "空国家码"));
                    return attempt(ipAddress, chain, index + 1, perProviderTimeout, deadlineNanos, attempts);
                })
                .thenCompose(cf -> cf);
    }

    private CompletableFuture<GeoIpResult> fetch(Provider provider, String ipAddress, Duration timeout) {
        return AsyncHttp.get(provider.url(ipAddress), Map.of(), timeout, timeout, 0)
                .thenApply(resp -> {
                    if (resp == null) {
                        throw new IllegalStateException("空响应");
                    }
                    int status = resp.statusCode();
                    String body = resp.body() == null ? "" : resp.body();
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("HTTP " + status);
                    }
                    return new GeoIpResult(provider.extractCountryCode(body), body, provider.id());
                });
    }

    /** 把链式异常压成一行可读原因（超时/连接失败/解析失败都是这里的常见值）。 */
    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while ((cur instanceof CompletionException || cur instanceof java.util.concurrent.ExecutionException)
                && cur.getCause() != null) {
            cur = cur.getCause();
        }
        String type = cur.getClass().getSimpleName();
        String message = cur.getMessage();
        return (message == null || message.isBlank()) ? type : type + "(" + clip(message) + ")";
    }

    private static String clip(String s) {
        return s.length() <= 120 ? s : s.substring(0, 120) + "…";
    }

    /**
     * 解析配置里的 provider 名字列表（未知名字忽略，去重保序）。
     *
     * <p>返回空列表时调用方应回退到 {@link #DEFAULT_PROVIDERS}——配置写错不应把地区限制彻底废掉。</p>
     */
    public static List<Provider> parseProviders(Iterable<String> names) {
        if (names == null) {
            return List.of();
        }
        LinkedHashSet<Provider> out = new LinkedHashSet<>();
        for (String name : names) {
            Provider p = Provider.parse(name);
            if (p != null) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    /** 供校验提示用：合法 provider 名清单。 */
    public static List<String> providerIds() {
        return java.util.Arrays.stream(Provider.values())
                .map(Provider::id)
                .filter(Objects::nonNull)
                .toList();
    }
}
