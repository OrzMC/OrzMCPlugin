package com.jokerhub.paper.plugin.orzmc.infra.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class AsyncHttp {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 500;
    private static final ConcurrentMap<Duration, HttpClient> CLIENTS = new ConcurrentHashMap<>();

    private static HttpClient client(Duration connectTimeout) {
        Duration timeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        return CLIENTS.computeIfAbsent(
                timeout, value -> HttpClient.newBuilder().connectTimeout(value).build());
    }

    private static CompletableFuture<HttpResponse<String>> sendWithRetry(
            HttpClient c, HttpRequest request, int retries) {
        int normalizedRetries = Math.max(0, retries);
        long requestTimeoutMs =
                request.timeout().orElse(DEFAULT_REQUEST_TIMEOUT).toMillis();
        long backoffBudget = 0L;
        for (int i = 0; i < normalizedRetries; i++) {
            backoffBudget = saturatingAdd(backoffBudget, BASE_BACKOFF_MS * (1L << Math.min(i, 10)));
        }
        long requestBudget = saturatingMultiply(requestTimeoutMs, normalizedRetries + 1L);
        long totalBudget = saturatingAdd(requestBudget, backoffBudget);
        return sendWithRetry(c, request, normalizedRetries, 0)
                .orTimeout(Math.max(1L, totalBudget), TimeUnit.MILLISECONDS);
    }

    private static CompletableFuture<HttpResponse<String>> sendWithRetry(
            HttpClient c, HttpRequest request, int retriesRemaining, int attempt) {
        return c.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    boolean retryableStatus = resp != null
                            && (resp.statusCode() == 408
                                    || resp.statusCode() == 429
                                    || (resp.statusCode() >= 500 && resp.statusCode() <= 599));
                    if (ex == null && !retryableStatus) {
                        return CompletableFuture.completedFuture(resp);
                    }
                    if (retriesRemaining <= 0) {
                        return ex == null
                                ? CompletableFuture.completedFuture(resp)
                                : CompletableFuture.<HttpResponse<String>>failedFuture(ex);
                    }
                    long delay = retryAfterMillis(resp).orElse(BASE_BACKOFF_MS * (1L << Math.min(attempt, 10)));
                    java.util.concurrent.Executor delayed =
                            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS);
                    return CompletableFuture.supplyAsync(() -> null, delayed)
                            .thenCompose(v -> sendWithRetry(c, request, retriesRemaining - 1, attempt + 1));
                })
                .thenCompose(f -> f);
    }

    public static CompletableFuture<HttpResponse<String>> get(
            String url,
            Map<String, String> headers,
            Duration connectTimeout,
            Duration requestTimeout,
            Integer maxRetries) {
        HttpClient c = client(connectTimeout);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout)
                .header("User-Agent", "OrzMC-EasyBot/1");
        if (headers != null) headers.forEach(b::setHeader);
        HttpRequest req = b.GET().build();
        return sendWithRetry(c, req, maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries);
    }

    public static CompletableFuture<HttpResponse<String>> postJson(
            String url,
            String json,
            Map<String, String> headers,
            Duration connectTimeout,
            Duration requestTimeout,
            Integer maxRetries) {
        HttpClient c = client(connectTimeout);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", "OrzMC-EasyBot/1");
        if (headers != null) headers.forEach(b::setHeader);
        HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json))
                .build();
        return sendWithRetry(c, req, maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries);
    }

    private static java.util.Optional<Long> retryAfterMillis(HttpResponse<String> response) {
        if (response == null || response.statusCode() != 429) {
            return java.util.Optional.empty();
        }
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds < 0) return java.util.Optional.empty();
                return java.util.Optional.of(seconds >= 60 ? 60_000L : seconds * 1000L);
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        });
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    /**
     * 关闭所有缓存的 {@link HttpClient}（插件卸载/重载时回收其线程池，防泄漏）。
     *
     * <p>幂等：关闭后 {@code CLIENTS} 置空，下次请求经 {@link #client} 的 {@code computeIfAbsent}
     * 重建新客户端。</p>
     */
    public static void shutdown() {
        for (HttpClient client : CLIENTS.values()) {
            client.close();
        }
        CLIENTS.clear();
    }

    /** 当前缓存的 HttpClient 数量（测试用：验证 shutdown 清空缓存）。 */
    static int clientCount() {
        return CLIENTS.size();
    }
}
