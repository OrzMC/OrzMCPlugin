package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.google.gson.Gson;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EasyBotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketClientFactory;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketEventListener;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WsClient;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * EasyBot WebSocket 生命周期管理（从 OrzEasyBot 抽离）。
 *
 * <p>持有连接状态（当前 {@link WsClient} 与连接指纹）、生命周期锁，负责按配置对账连接
 * （连接配置变更时重建）、监听器回调（认证/关闭/错误）与健康状态更新。入站消息经
 * {@code messageSink} 路由回 {@link OrzEasyBot}（或 {@link InboundEventParser}）。</p>
 */
final class WebSocketLifecycle {

    private static final String HEALTH_KEY = "easybot";
    private static final Gson GSON = new Gson();

    private final ServerLogger logger;
    private final ThrottledLogger throttledLogger;
    private final HealthRegistry healthRegistry;
    private final WebSocketClientFactory wsFactory;
    private final Consumer<String> messageSink;

    private final Object lifecycleLock = new Object();
    private volatile WsClient webSocketClient;
    private volatile String activeConnectionFingerprint;

    WebSocketLifecycle(
            ServerLogger logger,
            ThrottledLogger throttledLogger,
            HealthRegistry healthRegistry,
            WebSocketClientFactory wsFactory,
            Consumer<String> messageSink) {
        this.logger = logger;
        this.throttledLogger = throttledLogger;
        this.healthRegistry = healthRegistry;
        this.wsFactory = wsFactory;
        this.messageSink = messageSink;
    }

    /** 按最新配置对账连接状态：连接配置变更时重建 WS 客户端，未变更则跳过。 */
    void reconcile(EasyBotConfig cfg) {
        synchronized (lifecycleLock) {
            reconcileLocked(cfg);
        }
    }

    /** 尝试重连：仅在未连接（webSocketClient == null）时触发对账，避免频繁重建。 */
    void tryReconnect(EasyBotConfig cfg) {
        synchronized (lifecycleLock) {
            if (!cfg.enabled()) {
                reconcileLocked(cfg);
                return;
            }
            if (webSocketClient == null) {
                healthRegistry.setLastError(HEALTH_KEY, "reconnecting...");
                reconcileLocked(cfg);
            }
        }
    }

    /** 关闭当前 WS 连接并清空连接指纹（不更新健康状态，供 tearDown/auth_failed 调用）。 */
    void shutdown() {
        synchronized (lifecycleLock) {
            shutdownLocked();
        }
    }

    /** 若给定 client 仍是当前连接，则清空引用（WS 重连耗尽时由 listener 触发）。 */
    void detach(WsClient client) {
        synchronized (lifecycleLock) {
            if (webSocketClient == client) {
                webSocketClient = null;
                activeConnectionFingerprint = null;
            }
        }
    }

    /** 当前 WS 客户端（供入站 ping/pong 回包），无连接时返回 null。 */
    WsClient currentClient() {
        return webSocketClient;
    }

    private void reconcileLocked(EasyBotConfig cfg) {
        healthRegistry.setEnabled(HEALTH_KEY, cfg.enabled());
        if (!cfg.enabled()) {
            shutdownLocked();
            healthRegistry.setWsConnected(HEALTH_KEY, false);
            healthRegistry.setHttpChecked(HEALTH_KEY, false);
            healthRegistry.setLastError(HEALTH_KEY, null);
            return;
        }
        if (cfg.apiServer().isBlank()
                || cfg.wsServer().isBlank()
                || cfg.apiKey().isBlank()) {
            shutdownLocked();
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
        shutdownLocked();
        healthRegistry.setHttpChecked(HEALTH_KEY, false);
        setupClientLocked(cfg, fingerprint);
    }

    private void setupClientLocked(EasyBotConfig cfg, String fingerprint) {
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
                        detach(currentClient());
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
                            messageSink.accept(message);
                        }
                    });
            clientRef.set(client);
            webSocketClient = client;
            activeConnectionFingerprint = fingerprint;
            client.connect();
        } catch (Exception e) {
            healthRegistry.setLastError(HEALTH_KEY, e.toString());
            logger.logger().warning("EasyBot WS setup failed: " + e);
        }
    }

    private void shutdownLocked() {
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
}
