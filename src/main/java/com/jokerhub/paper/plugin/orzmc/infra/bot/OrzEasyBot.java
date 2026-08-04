package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EasyBotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import com.jokerhub.paper.plugin.orzmc.infra.ws.DefaultWebSocketClientFactory;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketClientFactory;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketEventListener;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * EasyBot IM Gateway 适配器。
 *
 * <p>单一适配器处理所有平台（QQ / Telegram / Discord / 飞书 / 微信），
 * EasyBot 已屏蔽各平台协议差异，业务层只需感知 {@code platform}、{@code text}、{@code sender.role}、{@code chat_id}。
 *
 * <p>入站：单一 WebSocket 连接接收所有平台的事件。
 * 出站：根据 {@link MessageEnvelope.TargetType} 和 {@link EasyBotConfig} 的路由规则确定目标。
 *
 * <p>路由规则：
 * <ul>
 *   <li>PUBLIC → 遍历所有平台的 {@code player_group}（空则降级 {@code admin_group}）</li>
 *   <li>PRIVATE → 遍历所有平台的 {@code admin_dm}</li>
 * </ul>
 */
public class OrzEasyBot implements BotMessageService {

    private static final String HEALTH_KEY = "easybot";
    private static final Gson GSON = new Gson();
    private static final int MAX_HTTP_IN_FLIGHT = 32;
    private static final int MAX_INBOUND_PAYLOAD_CHARS = 64 * 1024;
    private static final int MAX_INBOUND_TEXT_CHARS = 8 * 1024;
    private static final int MAX_INBOUND_TARGET_CHARS = 512;
    private static final int MAX_INBOUND_EVENTS_PER_SECOND = 100;

    private final ServerLogger logger;
    private final ConfigService configService;
    private final BotInboundHandler inboundHandler;
    private final MessageFormatter formatter;
    private final ThrottledLogger throttledLogger;
    private final HealthRegistry healthRegistry;
    private final WebSocketClientFactory wsFactory;
    private final Object lifecycleLock = new Object();
    private final Object httpHealthLock = new Object();
    private final Semaphore httpPermits = new Semaphore(MAX_HTTP_IN_FLIGHT);
    private final AtomicLong inboundWindowStart = new AtomicLong();
    private final AtomicInteger inboundWindowCount = new AtomicInteger();

    private volatile WsClient webSocketClient;
    private volatile String activeConnectionFingerprint;
    private int httpRequestsInFlight;
    private String pendingHttpError;

    // ---- 构造器 -----------------------------------------------------------

    public OrzEasyBot(
            ServerLogger logger,
            ConfigService configService,
            BotInboundHandler inboundHandler,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger,
            HealthRegistry healthRegistry) {
        this.logger = logger;
        this.configService = configService;
        this.inboundHandler = inboundHandler;
        this.formatter = formatter;
        this.throttledLogger = throttledLogger;
        this.healthRegistry = healthRegistry;
        this.wsFactory = new DefaultWebSocketClientFactory();
    }

    /** 测试用构造器，允许注入模拟的 {@link WebSocketClientFactory}。 */
    OrzEasyBot(
            ServerLogger logger,
            ConfigService configService,
            BotInboundHandler inboundHandler,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger,
            HealthRegistry healthRegistry,
            WebSocketClientFactory wsFactory) {
        this.logger = logger;
        this.configService = configService;
        this.inboundHandler = inboundHandler;
        this.formatter = formatter;
        this.throttledLogger = throttledLogger;
        this.healthRegistry = healthRegistry;
        this.wsFactory = wsFactory == null ? new DefaultWebSocketClientFactory() : wsFactory;
    }

    public boolean isEnable() {
        EasyBotConfig cfg = loadConfig();
        return cfg.enabled();
    }

    @Override
    public void setup() {
        reloadConfig();
    }

    @Override
    public void tearDown() {
        synchronized (lifecycleLock) {
            shutdownWebSocketClientLocked();
            healthRegistry.setEnabled(HEALTH_KEY, false);
            healthRegistry.setWsConnected(HEALTH_KEY, false);
            healthRegistry.setHttpChecked(HEALTH_KEY, false);
            healthRegistry.setLastError(HEALTH_KEY, null);
            healthRegistry.setDelivery(HEALTH_KEY, 0, 0, List.of());
        }
    }

    @Override
    public void tryReconnectIfDisconnected() {
        synchronized (lifecycleLock) {
            EasyBotConfig cfg = loadConfig();
            if (!cfg.enabled()) {
                reconcileConfigLocked(cfg);
                return;
            }
            if (webSocketClient == null) {
                healthRegistry.setLastError(HEALTH_KEY, "reconnecting...");
                reconcileConfigLocked(cfg);
            }
        }
    }

    @Override
    public void reloadConfig() {
        synchronized (lifecycleLock) {
            reconcileConfigLocked(loadConfig());
        }
    }

    /**
     * 出站消息路由。
     *
     * <p>根据 {@link MessageEnvelope.TargetType} 确定目标并发送：
     * <ul>
     *   <li>PUBLIC → 各平台 {@code player_group}（空则降级 {@code admin_group}）</li>
     *   <li>PRIVATE → 各平台 {@code admin_dm}（空则跳过）</li>
     * </ul>
     */
    @Override
    public void send(MessageEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        EasyBotConfig cfg = loadConfig();
        if (!cfg.enabled()) {
            return;
        }
        MessageEnvelope.Format fmt = envelope.format() == null ? MessageEnvelope.Format.DEFAULT : envelope.format();
        List<String> parts = formatter.format(envelope.message(), fmt);

        if (envelope.targetType() == null) {
            return;
        }
        switch (envelope.targetType()) {
            case PUBLIC -> sendPublic(cfg, parts);
            case PRIVATE -> sendPrivate(cfg, parts);
        }
    }

    // ---- 出站路由 ----------------------------------------------------------

    private void sendPublic(EasyBotConfig cfg, List<String> parts) {
        List<String> targets = new ArrayList<>();
        for (var entry : cfg.platforms().entrySet()) {
            if (!entry.getValue().enabled()) {
                continue;
            }
            String target = resolvePublicTarget(entry.getValue());
            if (target != null && !target.isEmpty()) {
                targets.add(target);
            }
        }
        if (!targets.isEmpty()) {
            sendBatch(cfg, targets, parts);
        }
    }

    private void sendPrivate(EasyBotConfig cfg, List<String> parts) {
        List<String> targets = new ArrayList<>();
        for (var entry : cfg.platforms().entrySet()) {
            if (!entry.getValue().enabled()) {
                continue;
            }
            String target = entry.getValue().adminDm();
            if (target != null && !target.isEmpty()) {
                targets.add(target);
            }
        }
        if (!targets.isEmpty()) {
            sendBatch(cfg, targets, parts);
        }
    }

    /**
     * 解析 PUBLIC 消息的目标 target。
     * 优先使用 {@code playerGroup}，空则降级为 {@code adminGroup}。
     */
    private static String resolvePublicTarget(EasyBotConfig.PlatformEntry entry) {
        String target = entry.playerGroup();
        if (target == null || target.isEmpty()) {
            target = entry.adminGroup();
        }
        return target;
    }

    // ---- HTTP 发送 ---------------------------------------------------------

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
            logger.logger().info("EasyBot sendToTarget error: " + e);
        }
    }

    /**
     * 批量发送同一消息到多个 target，单次 HTTP 请求完成多平台广播。
     *
     * <p>使用 EasyBot {@code POST /api/v1/messages/batch-send} 端点（最多 100 个 target）。
     */
    private void sendBatch(EasyBotConfig cfg, List<String> targets, List<String> parts) {
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
                logger.logger().info("EasyBot sendBatch error: " + e);
            }
        }
    }

    /**
     * 解析 batch-send 的 2xx 响应体 {@code {total, results: {target: {status,...}}}}，
     * 把失败/结果不确定的目标记录到日志，并结构化更新健康状态的投递字段。
     *
     * <ul>
     *   <li>全部成功：清除投递失败字段，健康保持绿</li>
     *   <li>部分失败：记 warning 日志（完整明细），设置投递字段 {@code (failed, total, target)}，{@code httpOk} 保持 true（渲染为黄色）</li>
     *   <li>全部目标失败：记 error 日志（完整明细），设置投递字段（渲染为红色）；不改变 {@code httpOk}</li>
     * </ul>
     *
     * 完整失败明细只进 {@link ThrottledLogger}，健康状态只保留结构化投递字段
     * （失败数 / 总数 / 首个失败目标），由渲染层组成简短展示。
     * 响应体非 JSON（如纯文本 "accepted"）或缺少 {@code results} 时静默忽略。
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
            logger.logger().info("EasyBot 批量结果解析异常: " + e);
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

    // ---- WebSocket 生命周期 ------------------------------------------------

    void setupWebSocketClient() {
        synchronized (lifecycleLock) {
            reconcileConfigLocked(loadConfig());
        }
    }

    void shutdownWebSocketClient() {
        synchronized (lifecycleLock) {
            shutdownWebSocketClientLocked();
        }
    }

    private void reconcileConfigLocked(EasyBotConfig cfg) {
        healthRegistry.setEnabled(HEALTH_KEY, cfg.enabled());
        if (!cfg.enabled()) {
            shutdownWebSocketClientLocked();
            healthRegistry.setWsConnected(HEALTH_KEY, false);
            healthRegistry.setHttpChecked(HEALTH_KEY, false);
            healthRegistry.setLastError(HEALTH_KEY, null);
            return;
        }
        if (cfg.apiServer().isBlank()
                || cfg.wsServer().isBlank()
                || cfg.apiKey().isBlank()) {
            shutdownWebSocketClientLocked();
            healthRegistry.setWsConnected(HEALTH_KEY, false);
            healthRegistry.setHttpChecked(HEALTH_KEY, false);
            healthRegistry.setApiReady(HEALTH_KEY, false);
            healthRegistry.setLastError(HEALTH_KEY, "EasyBot 连接配置不完整: api_server/ws_server/api_key");
            return;
        }

        String fingerprint = cfg.connectionFingerprint();
        if (webSocketClient != null && fingerprint.equals(activeConnectionFingerprint)) {
            return;
        }
        shutdownWebSocketClientLocked();
        healthRegistry.setHttpChecked(HEALTH_KEY, false);
        setupWebSocketClientLocked(cfg, fingerprint);
    }

    private void setupWebSocketClientLocked(EasyBotConfig cfg, String fingerprint) {
        try {
            String wsUrl = cfg.wsServer() + "/api/v1/ws";
            // EasyBot 使用 WebSocket PING/PONG 检测存活，无需应用层心跳
            String heartbeatPayload = "";
            String authApiKey = cfg.apiKey();
            AtomicReference<WsClient> clientRef = new AtomicReference<>();
            WebSocketEventListener listener = new WebSocketEventListener() {
                private WsClient currentClient() {
                    return clientRef.get();
                }

                private boolean isCurrent() {
                    WsClient current = currentClient();
                    return current != null && webSocketClient == current;
                }

                @Override
                public void onOpen() {
                    if (!isCurrent()) return;
                    healthRegistry.setWsConnected(HEALTH_KEY, false);
                    if (!authApiKey.isBlank()) {
                        currentClient().send(GSON.toJson(Map.of("token", authApiKey)));
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (isCurrent()) {
                        healthRegistry.setWsConnected(HEALTH_KEY, false);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    if (!isCurrent()) return;
                    healthRegistry.setWsConnected(HEALTH_KEY, false);
                    healthRegistry.setLastError(HEALTH_KEY, ex.toString());
                    throttledLogger.error("easybot-ws", "EasyBot WebSocket 异常: " + ex);
                    if (ex.getMessage() != null && ex.getMessage().contains("WS reconnect exhausted")) {
                        detachCurrentClient(currentClient());
                    }
                }
            };
            WsClient client = wsFactory.create(
                    logger,
                    wsUrl,
                    throttledLogger,
                    Math.max(0, cfg.wsMaxRetries()),
                    cfg.wsBaseRetryMs() <= 0 ? 5000 : cfg.wsBaseRetryMs(),
                    cfg.wsMaxDelayMs() <= 0 ? 60000 : cfg.wsMaxDelayMs(),
                    Math.max(0, cfg.wsJitterPercent()),
                    cfg.wsStableResetMs() <= 0 ? 20000 : cfg.wsStableResetMs(),
                    cfg.wsMessageLogEnabled(),
                    cfg.wsMessageLogThrottleMs() <= 0 ? 60000 : cfg.wsMessageLogThrottleMs(),
                    Collections.emptyMap(),
                    heartbeatPayload,
                    listener,
                    message -> {
                        WsClient current = clientRef.get();
                        if (current != null && webSocketClient == current) {
                            processInboundEvent(message);
                        }
                    });
            clientRef.set(client);
            webSocketClient = client;
            activeConnectionFingerprint = fingerprint;
            client.connect();
        } catch (Exception e) {
            healthRegistry.setLastError(HEALTH_KEY, e.toString());
            logger.logger().info("EasyBot WS setup failed: " + e);
        }
    }

    private void shutdownWebSocketClientLocked() {
        WsClient current = webSocketClient;
        webSocketClient = null;
        activeConnectionFingerprint = null;
        if (current != null) {
            try {
                current.disconnect();
            } catch (Exception e) {
                throttledLogger.warning("easybot-ws-shutdown", "EasyBot WebSocket 关闭异常: " + e);
            }
        }
    }

    private void detachCurrentClient(WsClient client) {
        synchronized (lifecycleLock) {
            if (webSocketClient == client) {
                webSocketClient = null;
                activeConnectionFingerprint = null;
            }
        }
    }

    // ---- 入站消息处理 -------------------------------------------------------

    /**
     * 处理来自 EasyBot WebSocket 的入站事件。
     *
     * <p>所有平台的消息都通过此单一方法处理。EasyBot 已屏蔽协议差异，
     * 统一为 {platform, text, sender.role, chat_id} 格式。
     *
     * <p>系统帧（auth_ok / auth_failed / lagged）在此直接处理，不会传递到业务层。
     */
    void processInboundEvent(String jsonString) {
        if (jsonString == null || jsonString.isEmpty() || jsonString.length() > MAX_INBOUND_PAYLOAD_CHARS) {
            if (jsonString != null && jsonString.length() > MAX_INBOUND_PAYLOAD_CHARS) {
                throttledLogger.warning("easybot-inbound-size", "EasyBot 入站消息超过大小限制，已丢弃");
            }
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(jsonString);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            String type = stringValue(root, "type");
            if (type == null) {
                return;
            }

            // ---- 系统帧处理 ----
            if ("auth_ok".equals(type)) {
                healthRegistry.setWsConnected(HEALTH_KEY, true);
                healthRegistry.setLastError(HEALTH_KEY, null);
                throttledLogger.info("easybot-ws-auth", "EasyBot WebSocket 认证成功");
                return;
            }
            if ("auth_failed".equals(type)) {
                healthRegistry.setWsConnected(HEALTH_KEY, false);
                String msg = stringValue(root, "message");
                if (msg == null) msg = "unknown";
                healthRegistry.setLastError(HEALTH_KEY, "WS auth failed: " + msg);
                throttledLogger.error("easybot-ws-auth", "EasyBot WebSocket 认证失败: " + msg);
                shutdownWebSocketClient();
                return;
            }
            if ("lagged".equals(type)) {
                int dropped = root.has("dropped") && root.get("dropped").isJsonPrimitive()
                        ? root.get("dropped").getAsInt()
                        : 0;
                throttledLogger.warning("easybot-ws-lag", "EasyBot WS 事件丢失: " + dropped);
                return;
            }
            if ("ping".equals(type)) {
                WsClient current = webSocketClient;
                if (current != null) {
                    current.send("{\"type\":\"pong\"}");
                }
                return;
            }

            // ---- 只处理事件帧 ----
            if (!"event".equals(type)) {
                return;
            }
            if (!root.has("event")) {
                return;
            }
            String eventType = stringValue(root, "event");
            if (eventType == null) {
                return;
            }
            if (!"message.inbound".equals(eventType)) {
                return;
            }
            if (!allowInboundEvent()) {
                throttledLogger.warning("easybot-inbound-rate", "EasyBot 入站消息超过速率限制，已丢弃");
                return;
            }

            // ---- 解析消息数据 ----
            if (!root.has("data") || !root.get("data").isJsonObject()) {
                return;
            }
            JsonObject data = root.getAsJsonObject("data");

            // platform: 标识来源平台，如 "qq", "discord", "telegram"
            if (!data.has("platform")) {
                return;
            }
            String platformValue = stringValue(data, "platform");
            if (platformValue == null) {
                return;
            }
            String platform = platformValue.trim().toLowerCase(Locale.ROOT);
            if (platform.isEmpty() || platform.length() > 64) {
                return;
            }
            EasyBotConfig cfg = loadConfig();

            // 跳过已禁用平台的消息
            if (!isPlatformEnabled(cfg, platform)) {
                return;
            }

            // text: 消息内容
            String textValue = stringValue(data, "text");
            String text = textValue == null ? "" : textValue.trim();
            if (text.isEmpty() || text.length() > MAX_INBOUND_TEXT_CHARS) {
                if (text.length() > MAX_INBOUND_TEXT_CHARS) {
                    throttledLogger.warning("easybot-inbound-text-size", "EasyBot 入站文本超过大小限制，已丢弃");
                }
                return;
            }

            // chat_id: 来源会话标识
            String chatIdValue = stringValue(data, "chat_id");
            String chatId = chatIdValue == null ? "" : chatIdValue;
            if (chatId.isEmpty() || chatId.length() > MAX_INBOUND_TARGET_CHARS) {
                return;
            }
            String replyTarget = normalizeTarget(platform, chatId);
            if (!isInboundTargetAllowed(cfg, platform, replyTarget)) {
                throttledLogger.warning(
                        "easybot-inbound-target",
                        "EasyBot 忽略未授权会话消息: platform=" + platform + ", target=" + replyTarget);
                return;
            }

            // sender.role: 发送者角色（EasyBot 已各平台标准化）
            boolean isAdmin = false;
            if (data.has("sender") && data.get("sender").isJsonObject()) {
                JsonObject sender = data.getAsJsonObject("sender");
                String role = stringValue(sender, "role");
                if (role != null) {
                    isAdmin = "Owner".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role);
                }
            }

            // 关键：sink 捕获来源平台和会话，确保回复定向到正确的位置
            Consumer<MessageEnvelope> sink = env -> {
                if (env != null) {
                    MessageEnvelope.Format replyFormat =
                            env.format() == null ? MessageEnvelope.Format.DEFAULT : env.format();
                    for (String part : formatter.format(env.message(), replyFormat)) {
                        sendToTarget(cfg, replyTarget, part);
                    }
                }
            };

            BotInboundDispatcher.dispatch(inboundHandler, text, isAdmin, sink);
        } catch (Exception e) {
            healthRegistry.setLastError(HEALTH_KEY, e.toString());
            logger.logger().info("EasyBot inbound parse error: " + e);
        }
    }

    // ---- 辅助方法 ----------------------------------------------------------

    private EasyBotConfig loadConfig() {
        return EasyBotConfig.from(configService.getConfig("easybot"));
    }

    /**
     * 检查指定平台是否已在配置中启用。
     * 未找到配置的平台（如未注册的测试平台）视为禁用。
     */
    private boolean isPlatformEnabled(EasyBotConfig cfg, String platform) {
        EasyBotConfig.PlatformEntry entry = cfg.platforms().get(platform);
        return entry != null && entry.enabled();
    }

    private boolean isInboundTargetAllowed(EasyBotConfig cfg, String platform, String target) {
        EasyBotConfig.PlatformEntry entry = cfg.platforms().get(platform);
        return entry != null
                && entry.enabled()
                && (target.equals(entry.adminGroup())
                        || target.equals(entry.playerGroup())
                        || target.equals(entry.adminDm()));
    }

    private static String normalizeTarget(String platform, String chatId) {
        chatId = chatId.trim();
        String prefix = platform + ":";
        return chatId.startsWith(prefix) ? chatId : prefix + chatId;
    }

    private boolean allowInboundEvent() {
        long now = System.currentTimeMillis();
        long windowStart = inboundWindowStart.get();
        if (windowStart == 0L || now - windowStart >= 1000L) {
            if (inboundWindowStart.compareAndSet(windowStart, now)) {
                inboundWindowCount.set(0);
            }
        }
        return inboundWindowCount.incrementAndGet() <= MAX_INBOUND_EVENTS_PER_SECOND;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && !value.isJsonNull() ? value.getAsString() : null;
    }
}
