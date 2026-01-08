package com.jokerhub.paper.plugin.orzmc.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AsyncHttp {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 500;

    private static HttpClient client(Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout).build();
    }

    private static CompletableFuture<HttpResponse<String>> sendWithRetry(HttpClient c, HttpRequest request, int retries) {
        return c.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((resp, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(resp);
            }
            if (retries <= 0) {
                return CompletableFuture.<HttpResponse<String>>failedFuture(ex);
            }
            int nextRetries = retries - 1;
            long delay = (long) (BASE_BACKOFF_MS * Math.pow(2, (DEFAULT_MAX_RETRIES - nextRetries)));
            java.util.concurrent.Executor delayed = CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS);
            return CompletableFuture.supplyAsync(() -> null, delayed).thenCompose(v -> sendWithRetry(c, request, nextRetries));
        }).thenCompose(f -> f);
    }

    public static CompletableFuture<HttpResponse<String>> get(String url, Map<String, String> headers, Duration connectTimeout, Duration requestTimeout, Integer maxRetries) {
        HttpClient c = client(connectTimeout);
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url)).timeout(requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout);
        if (headers != null) headers.forEach(b::setHeader);
        HttpRequest req = b.GET().build();
        return sendWithRetry(c, req, maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries);
    }

    public static CompletableFuture<HttpResponse<String>> postJson(String url, String json, Map<String, String> headers, Duration connectTimeout, Duration requestTimeout, Integer maxRetries) {
        HttpClient c = client(connectTimeout);
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url)).timeout(requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout).header("Content-Type", "application/json");
        if (headers != null) headers.forEach(b::setHeader);
        HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json)).build();
        return sendWithRetry(c, req, maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries);
    }
}
