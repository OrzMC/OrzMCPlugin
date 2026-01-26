package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class RobustWebSocketClient {
    private final URI serverUri;
    private final ScheduledExecutorService executor;
    private final int maxRetries;
    private final long baseRetryInterval;
    private final long maxRetryInterval;
    private final int jitterPercent;
    private final long logThrottleMs;
    private final long stableResetMs;
    private final Map<String, String> httpHeaders;
    private final WebSocketEventListener listener;
    private WebSocketClient client;
    private volatile boolean shouldReconnect = true;
    private volatile boolean isReconnecting = false;
    private int retryCount = 0;

    public RobustWebSocketClient(String url, int maxRetries, long baseRetryInterval, long maxRetryInterval, int jitterPercent, long logThrottleMs, long stableResetMs, Map<String, String> httpHeaders, WebSocketEventListener listener) throws URISyntaxException {
        this.serverUri = new URI(url);
        this.maxRetries = maxRetries;
        this.baseRetryInterval = baseRetryInterval;
        this.maxRetryInterval = maxRetryInterval;
        this.jitterPercent = jitterPercent;
        this.logThrottleMs = logThrottleMs;
        this.stableResetMs = stableResetMs;
        this.httpHeaders = httpHeaders;
        this.listener = listener;
        this.executor = Executors.newScheduledThreadPool(3);
        createClient();
    }

    private void createClient() {
        client = new WebSocketClient(serverUri, httpHeaders) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                OrzMC.logger().info("WebSocket连接建立");
                executor.schedule(() -> {
                    if (client != null && client.isOpen()) {
                        retryCount = 0;
                    }
                }, stableResetMs <= 0 ? 20000 : stableResetMs, TimeUnit.MILLISECONDS);
                if (listener != null) listener.onOpen();
            }

            @Override
            public void onMessage(String message) {
                OrzMC.debugInfo("接收到消息: " + message);
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                OrzMC.logger().info("WebSocket连接关闭: " + reason);
                ThrottledNotifier.run("ws-close", logThrottleMs <= 0 ? 5000 : logThrottleMs, () -> OrzMC.server().sendMessage(OrzTextStyles.warn("WebSocket连接关闭: " + (reason == null ? "" : reason))));
                if (listener != null) listener.onClose(code, reason, remote);
                if (shouldReconnect) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                OrzMC.logger().severe("WebSocket错误: " + ex.getMessage());
                ThrottledNotifier.run("ws-error", logThrottleMs <= 0 ? 5000 : logThrottleMs, () -> OrzMC.server().sendMessage(OrzTextStyles.error("WebSocket错误: " + ex.getMessage())));
                if (listener != null) listener.onError(ex);
                if (!isOpen() && shouldReconnect) {
                    scheduleReconnect();
                }
            }
        };
        client.setConnectionLostTimeout(30);
    }

    public void connect() {
        try {
            OrzMC.logger().info("尝试连接WebSocket...");
            client.connect();
        } catch (Exception e) {
            OrzMC.logger().severe("连接失败: " + e.getMessage());
            scheduleReconnect();
        }
    }

    public void disconnect() {
        shouldReconnect = false;
        client.close();
        executor.shutdown();
    }

    private void scheduleReconnect() {
        if (isReconnecting) {
            return;
        }
        if (retryCount >= maxRetries) {
            OrzMC.logger().severe("达到最大重试次数，停止重连");
            return;
        }

        retryCount++;
        long delay = calculateBackoffDelay();

        ThrottledLogger.info("ws-reconnect", "第 " + retryCount + " 次重连将在 " + delay + "ms 后进行", logThrottleMs <= 0 ? 5000 : logThrottleMs);

        isReconnecting = true;
        executor.schedule(() -> {
            if (shouldReconnect) {
                try {
                    if (client != null) {
                        client.reconnect();
                    } else {
                        createClient();
                        connect();
                    }
                } catch (Exception e) {
                    OrzMC.logger().severe("重连失败: " + e.getMessage());
                    isReconnecting = false;
                    scheduleReconnect();
                }
                isReconnecting = false;
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay() {
        long base = (long) (baseRetryInterval * Math.pow(2, Math.max(0, retryCount - 1)));
        long capped = Math.min(base, maxRetryInterval > 0 ? maxRetryInterval : base);
        int jitter = Math.max(0, Math.min(100, jitterPercent));
        double factor = 1.0 + ((ThreadLocalRandom.current().nextDouble() * 2 - 1) * (jitter / 100.0));
        return (long) Math.max(0, capped * factor);
    }

    protected void handleMessage(String message) {
        // 消息处理逻辑
    }

    public void send(String message) {
        try {
            if (client != null && client.isOpen()) {
                client.send(message);
            }
        } catch (Exception e) {
            OrzMC.logger().warning("WebSocket发送失败: " + e.getMessage());
        }
    }
}
