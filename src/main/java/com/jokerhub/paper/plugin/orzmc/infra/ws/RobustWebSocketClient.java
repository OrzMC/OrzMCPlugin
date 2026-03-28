package com.jokerhub.paper.plugin.orzmc.infra.ws;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class RobustWebSocketClient implements WsClient {
    private final ServerLogger server;
    private final ThrottledLogger throttledLogger;
    private final URI serverUri;
    private final ScheduledExecutorService executor;
    private final int maxRetries;
    private final long baseRetryInterval;
    private final long maxRetryInterval;
    private final int jitterPercent;
    private final long stableResetMs;
    private final boolean logMessageEnabled;
    private final long logMessageThrottleMs;
    private final Map<String, String> httpHeaders;
    private final WebSocketEventListener listener;
    private WebSocketClient client;
    private volatile boolean shouldReconnect = true;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private int retryCount = 0;
    private volatile ScheduledFuture<?> heartbeatFuture;
    private final String heartbeatPayload;
    private volatile long lastMessageTs = 0L;
    private volatile long lastHeartbeatSentTs = 0L;
    private volatile int missedHeartbeatAcks = 0;
    private final int heartbeatMissThreshold = 2;

    public RobustWebSocketClient(
            ServerLogger server,
            String url,
            ThrottledLogger throttledLogger,
            int maxRetries,
            long baseRetryInterval,
            long maxRetryInterval,
            int jitterPercent,
            long stableResetMs,
            boolean logMessageEnabled,
            long logMessageThrottleMs,
            Map<String, String> httpHeaders,
            String heartbeatPayload,
            WebSocketEventListener listener)
            throws URISyntaxException {
        this.server = server;
        this.serverUri = new URI(url);
        this.throttledLogger = throttledLogger;
        this.maxRetries = maxRetries;
        this.baseRetryInterval = baseRetryInterval;
        this.maxRetryInterval = maxRetryInterval;
        this.jitterPercent = jitterPercent;
        this.stableResetMs = stableResetMs;
        this.logMessageEnabled = logMessageEnabled;
        this.logMessageThrottleMs = logMessageThrottleMs;
        this.httpHeaders = httpHeaders;
        this.listener = listener;
        this.executor = Executors.newScheduledThreadPool(3);
        this.heartbeatPayload = heartbeatPayload;
        createClient();
    }

    private void createClient() {
        client = new WebSocketClient(serverUri, httpHeaders) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                server.logger().info("WebSocket连接建立");
                startHeartbeat();
                lastMessageTs = System.currentTimeMillis();
                executor.schedule(
                        () -> {
                            if (client != null && client.isOpen()) {
                                retryCount = 0;
                            }
                        },
                        stableResetMs <= 0 ? 20000 : stableResetMs,
                        TimeUnit.MILLISECONDS);
                if (listener != null) listener.onOpen();
            }

            @Override
            public void onMessage(String message) {
                if (logMessageEnabled) {
                    String clipped = message == null ? "" : message;
                    if (clipped.length() > 256) {
                        clipped = clipped.substring(0, 256) + "...";
                    }
                    throttledLogger.info("ws-message", "接收到消息: " + clipped, logMessageThrottleMs);
                }
                handleMessage(message);
                lastMessageTs = System.currentTimeMillis();
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                server.logger().info("WebSocket连接关闭: " + "code: " + code + ", reason: " + reason);
                stopHeartbeat();
                if (listener != null) listener.onClose(code, reason, remote);
                if (shouldReconnect) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                server.logger().severe("WebSocket错误: " + ex.getMessage());
                stopHeartbeat();
                if (listener != null) listener.onError(ex);
                if (!isOpen() && shouldReconnect) {
                    scheduleReconnect();
                }
            }
        };
        client.setConnectionLostTimeout(this.disableHeartbeat() ? 30 : 0);
    }

    public void connect() {
        try {
            server.logger().info("尝试连接WebSocket...");
            client.connect();
        } catch (Exception e) {
            server.logger().severe("连接失败: " + e.getMessage());
            scheduleReconnect();
        }
    }

    public void disconnect() {
        shouldReconnect = false;
        stopHeartbeat();
        client.close();
        executor.shutdown();
    }

    private void scheduleReconnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }
        if (retryCount >= maxRetries) {
            server.logger().severe("达到最大重试次数，停止重连");
            shouldReconnect = false;
            stopHeartbeat();
            try {
                if (client != null) {
                    client.close();
                }
            } catch (Exception ignored) {
            }
            try {
                executor.shutdown();
            } catch (Exception ignored) {
            }
            if (listener != null) {
                try {
                    listener.onError(new RuntimeException("WS reconnect exhausted"));
                } catch (Exception ignored) {
                }
            }
            reconnecting.set(false);
            return;
        }
        retryCount++;
        long delay = calculateBackoffDelay();
        throttledLogger.info("ws-reconnect", "第 " + retryCount + " 次重连将在 " + delay + "ms 后进行");
        executor.schedule(
                () -> {
                    if (!shouldReconnect) {
                        reconnecting.set(false);
                        return;
                    }
                    try {
                        if (client != null) {
                            client.reconnect();
                        } else {
                            createClient();
                            connect();
                        }
                        reconnecting.set(false);
                    } catch (Exception e) {
                        server.logger().severe("重连失败: " + e.getMessage());
                        reconnecting.set(false);
                        scheduleReconnect();
                    }
                },
                delay,
                TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay() {
        long base = (long) (baseRetryInterval * Math.pow(2, Math.max(0, retryCount - 1)));
        long capped = Math.min(base, maxRetryInterval > 0 ? maxRetryInterval : base);
        int jitter = Math.max(0, Math.min(100, jitterPercent));
        double factor = 1.0 + ((ThreadLocalRandom.current().nextDouble() * 2 - 1) * (jitter / 100.0));
        return (long) Math.max(0, capped * factor);
    }

    protected void handleMessage(String message) {}

    public void send(String message) {
        try {
            if (client != null && client.isOpen()) {
                client.send(message);
            }
        } catch (Exception e) {
            server.logger().warning("WebSocket发送失败: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        if (this.disableHeartbeat()) {
            return;
        }
        stopHeartbeat();
        heartbeatFuture = executor.scheduleAtFixedRate(this::doHeartbeatTick, 0, 30000, TimeUnit.MILLISECONDS);
    }

    protected void doHeartbeatTick() {
        try {
            if (client != null && client.isOpen()) {
                long prevSent = lastHeartbeatSentTs;
                long now = System.currentTimeMillis();
                if (prevSent > 0 && lastMessageTs < prevSent) {
                    missedHeartbeatAcks++;
                } else {
                    missedHeartbeatAcks = 0;
                }
                if (missedHeartbeatAcks >= heartbeatMissThreshold) {
                    try {
                        client.close();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                client.send(heartbeatPayload);
                lastHeartbeatSentTs = now;
            }
        } catch (Exception ignored) {
        }
    }

    private void stopHeartbeat() {
        if (this.disableHeartbeat()) {
            return;
        }
        if (heartbeatFuture != null) {
            try {
                heartbeatFuture.cancel(true);
            } catch (Exception ignored) {
            }
            heartbeatFuture = null;
        }
    }

    private boolean disableHeartbeat() {
        return heartbeatPayload == null || heartbeatPayload.isEmpty();
    }
}
