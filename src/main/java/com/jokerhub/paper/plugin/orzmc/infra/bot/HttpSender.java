package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EasyBotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * EasyBot 出站 HTTP 发送 + 健康状态机（从 OrzEasyBot 抽离）。
 *
 * <p>持有 HTTP 背压（{@link Semaphore}）与并发计数/聚合错误状态，负责单发/批量发送、
 * 批量结果解析与投递健康字段更新。无 WebSocket / 入站状态。</p>
 */
final class HttpSender {

    private static final String HEALTH_KEY = "easybot";
    private static final Gson GSON = new Gson();
    private static final int MAX_HTTP_IN_FLIGHT = 32;

    private final ServerLogger logger;
    private final ThrottledLogger throttledLogger;
    private final HealthRegistry healthRegistry;
    private final Semaphore httpPermits = new Semaphore(MAX_HTTP_IN_FLIGHT);
    private final Object httpHealthLock = new Object();
    private int httpRequestsInFlight;
    private String pendingHttpError;

    HttpSender(ServerLogger logger, ThrottledLogger throttledLogger, HealthRegistry healthRegistry) {
        this.logger = logger;
        this.throttledLogger = throttledLogger;
        this.healthRegistry = healthRegistry;
    }

    private void sendToTarget(EasyBotConfig cfg, String target, String message) {
        if (!httpPermits.tryAcquire()) {
            String error = "HTTP send queue is full";
            throttledLogger.warning("easybot-http-backpressure", "EasyBot 发送队列已满，丢弃消息: target=" + target);
            healthRegistry.setHttpOk(HEALTH_KEY, false);
            healthRegistry.setApiReady(HEALTH_KEY, false);
            healthRegistry.setLastError(HEALTH_KEY, error);
            healthRegistry.setDelivery(HEALTH_KEY, 0, 0, null);
            return;
        }
        beginHttpRequest();
        try {
            String url = cfg.apiServer() + "/api/v1/messages/send";
            Map<String, Object> body = new HashMap<>();
            body.put("target", target);
            body.put("text", message);
            String json = GSON.toJson(body);

            Map<String, String> headers = new HashMap<>();
            if (cfg.apiKey() != null && !cfg.apiKey().isEmpty()) {
                headers.put("Authorization", "Bearer " + cfg.apiKey());
            }
            headers.put("Idempotency-Key", idempotencyKey(target + "|" + message));

            AsyncHttp.postJson(
                            url,
                            json,
                            headers,
                            Duration.ofSeconds(cfg.httpConnectTimeoutSec() <= 0 ? 3 : cfg.httpConnectTimeoutSec()),
                            Duration.ofSeconds(cfg.httpRequestTimeoutSec() <= 0 ? 3 : cfg.httpRequestTimeoutSec()),
                            Math.max(0, cfg.httpMaxRetries()))
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            // 单发成功：清除上一次批量投递的失败状态，避免 /bot 展示陈旧失败
                            healthRegistry.setDelivery(HEALTH_KEY, 0, 0, List.of());
                            completeHttpRequest(null);
                        } else {
                            String error = "HTTP " + response.statusCode() + ": " + limitError(response.body());
                            throttledLogger.error(
                                    "easybot-http",
                                    "EasyBot 发送失败, target=" + target + ", status=" + response.statusCode());
                            completeHttpRequest(error);
                        }
                    })
                    .exceptionally(e -> {
                        throttledLogger.error("easybot-http", "EasyBot 发送异常: " + e);
                        completeHttpRequest(e.toString());
                        return null;
                    });
        } catch (Exception e) {
            completeHttpRequest(e.toString());
            logger.logger().warning("EasyBot sendToTarget error: " + e);
        }
    }

    /**
     * 批量发送同一消息到多个 target，单次 HTTP 请求完成多平台广播。
     *
     * <p>使用 EasyBot {@code POST /api/v1/messages/batch-send} 端点（最多 100 个 target）。
     */
    void sendBatch(EasyBotConfig cfg, List<String> targets, List<String> parts) {
        for (String part : parts) {
            if (!httpPermits.tryAcquire()) {
                String error = "HTTP send queue is full";
                throttledLogger.warning(
                        "easybot-http-backpressure", "EasyBot 批量发送队列已满，丢弃消息: targets=" + targets.size());
                healthRegistry.setHttpOk(HEALTH_KEY, false);
                healthRegistry.setApiReady(HEALTH_KEY, false);
                healthRegistry.setLastError(HEALTH_KEY, error);
                healthRegistry.setDelivery(HEALTH_KEY, 0, 0, null);
                return;
            }
            beginHttpRequest();
            try {
                String url = cfg.apiServer() + "/api/v1/messages/batch-send";
                Map<String, Object> body = new HashMap<>();
                body.put("targets", targets);
                body.put("text", part);
                String json = GSON.toJson(body);

                Map<String, String> headers = new HashMap<>();
                if (cfg.apiKey() != null && !cfg.apiKey().isEmpty()) {
                    headers.put("Authorization", "Bearer " + cfg.apiKey());
                }
                headers.put("Idempotency-Key", idempotencyKey(String.join(",", targets) + "|" + part));

                AsyncHttp.postJson(
                                url,
                                json,
                                headers,
                                Duration.ofSeconds(cfg.httpConnectTimeoutSec() <= 0 ? 3 : cfg.httpConnectTimeoutSec()),
                                Duration.ofSeconds(cfg.httpRequestTimeoutSec() <= 0 ? 3 : cfg.httpRequestTimeoutSec()),
                                Math.max(0, cfg.httpMaxRetries()))
                        .thenAccept(response -> {
                            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                recordBatchResultFailures(response.body());
                                completeHttpRequest(null);
                            } else {
                                String error = "HTTP " + response.statusCode() + ": " + limitError(response.body());
                                throttledLogger.error(
                                        "easybot-http",
                                        "EasyBot 批量发送失败, targets=" + targets.size() + ", status="
                                                + response.statusCode());
                                completeHttpRequest(error);
                            }
                        })
                        .exceptionally(e -> {
                            throttledLogger.error("easybot-http", "EasyBot 批量发送异常: " + e);
                            completeHttpRequest(e.toString());
                            return null;
                        });
            } catch (Exception e) {
                completeHttpRequest(e.toString());
                logger.logger().warning("EasyBot sendBatch error: " + e);
            }
        }
    }

    /** 单条消息发往单个 target（供入站 sink 回复用）。 */
    void sendMessage(EasyBotConfig cfg, String target, String message) {
        sendToTarget(cfg, target, message);
    }

    /**
     * 解析 batch-send 的 2xx 响应体 {@code {total, results: {target: {status,...}}}}，
     * 把失败/结果不确定的目标记录到日志，并结构化更新健康状态的投递字段。
     */
    private void recordBatchResultFailures(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(responseBody).getAsJsonObject();
        } catch (Exception e) {
            // 网关可能返回纯文本或非 JSON，忽略
            return;
        }
        if (!root.has("results") || !root.get("results").isJsonObject()) {
            return;
        }
        try {
            JsonObject results = root.getAsJsonObject("results");
            int total = root.has("total") && root.get("total").isJsonPrimitive()
                    ? root.get("total").getAsInt()
                    : -1;
            // 未知 total（置 0）时渲染层不判定为「全部失败」，按部分失败（黄色警告）处理
            int deliveryTotal = total > 0 ? total : 0;
            List<String> failures = new ArrayList<>();
            List<String> failedTargets = new ArrayList<>();
            for (var entry : results.entrySet()) {
                JsonObject item = entry.getValue().getAsJsonObject();
                String status = item.has("status") && item.get("status").isJsonPrimitive()
                        ? item.get("status").getAsString()
                        : null;
                if ("sent".equals(status)) {
                    continue;
                }
                failedTargets.add(entry.getKey());
                failures.add(
                        entry.getKey() + " -> " + (status == null ? "unknown" : status) + batchFailureReason(item));
            }
            if (failures.isEmpty()) {
                // 全部成功：清除上一次的投递失败状态
                healthRegistry.setDelivery(HEALTH_KEY, 0, 0, List.of());
                return;
            }
            // 结构化记录投递失败：失败数 / 总数 / 失败目标列表
            healthRegistry.setDelivery(HEALTH_KEY, failures.size(), deliveryTotal, failedTargets);
            String detail = buildBatchDetail(total, failures);
            if (total > 0 && failures.size() >= total) {
                throttledLogger.error("easybot-batch-fail", detail);
            } else {
                throttledLogger.warning("easybot-batch-partial", detail);
            }
        } catch (Exception e) {
            // 畸形 results（目标值非对象、status 为 null 等）不当作失败上报，避免误标健康
            logger.logger().warning("EasyBot 批量结果解析异常: " + e);
        }
    }

    /** 生成投递失败的完整明细日志（含每个失败目标及原因），供排障。 */
    private static String buildBatchDetail(int total, List<String> failures) {
        return "EasyBot 批量发送存在失败目标"
                + (total >= 0 ? " (" + failures.size() + "/" + total + ")" : "")
                + ": " + String.join("; ", failures);
    }

    /** 从单个目标的 results 条目中提取失败原因（error / errorCode），无则返回空串。 */
    private static String batchFailureReason(JsonObject item) {
        if (item.has("error")
                && item.get("error").isJsonPrimitive()
                && !item.get("error").getAsString().isEmpty()) {
            return ": " + item.get("error").getAsString();
        }
        if (item.has("errorCode")
                && item.get("errorCode").isJsonPrimitive()
                && !item.get("errorCode").getAsString().isEmpty()) {
            return ": code=" + item.get("errorCode").getAsString();
        }
        return "";
    }

    /** 生成幂等键，基于种子内容的 SHA-256 哈希前缀，确保重试不重复投递。 */
    private static String idempotencyKey(String seed) {
        long nanos = System.nanoTime();
        String raw = seed + "|" + nanos;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void beginHttpRequest() {
        synchronized (httpHealthLock) {
            if (httpRequestsInFlight++ == 0) {
                pendingHttpError = null;
            }
        }
    }

    private void completeHttpRequest(String error) {
        String aggregateError = null;
        boolean batchComplete;
        synchronized (httpHealthLock) {
            if (error != null && pendingHttpError == null) {
                pendingHttpError = limitError(error);
            }
            batchComplete = --httpRequestsInFlight == 0;
            if (batchComplete) {
                aggregateError = pendingHttpError;
                pendingHttpError = null;
            }
        }
        httpPermits.release();
        if (batchComplete) {
            healthRegistry.setHttpOk(HEALTH_KEY, aggregateError == null);
            healthRegistry.setApiReady(HEALTH_KEY, aggregateError == null);
            healthRegistry.setLastError(HEALTH_KEY, aggregateError);
        }
        if (error != null) {
            // 非投递类错误（HTTP 状态码 / 异常）发生时清除上次投递结果，避免展示陈旧状态
            healthRegistry.setDelivery(HEALTH_KEY, 0, 0, null);
        }
    }

    private static String limitError(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= 512 ? value : value.substring(0, 512) + "...";
    }
}
