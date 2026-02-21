package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import com.jokerhub.paper.plugin.orzmc.infra.ws.RobustWebSocketClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class OrzQQBot extends OrzBaseBot {
    private final BotInboundHandler inboundHandler;
    private final MessageFormatter formatter;
    private final ThrottledLogger throttledLogger;
    private RobustWebSocketClient webSocketClient;

    public OrzQQBot(
            OrzMC plugin,
            ConfigService configService,
            BotInboundHandler inboundHandler,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger) {
        super(plugin, configService);
        this.inboundHandler = inboundHandler;
        this.formatter = formatter;
        this.throttledLogger = throttledLogger;
    }

    @Override
    public boolean isEnable() {
        return botConfig.getBoolean("enable_qq_bot");
    }

    @Override
    public void setup() {
        HealthRegistry.setEnabled("qq", this.isEnable());
        this.setupWebSocketClient();
    }

    @Override
    public void teardown() {
        this.shutdownWebSocketClient();
    }

    @Override
    protected void sendPublic(String message) {
        if (!this.isEnable()) {
            return;
        }
        try {
            String groupId = botConfig.getString("qq_group_id");
            for (String part : formatter.format(message, MessageEnvelope.Format.DEFAULT)) {
                String url = botConfig.getString("qq_bot_api_server") + "/send_group_msg?group_id=" + groupId
                        + "&message=" + URLEncoder.encode(part, StandardCharsets.UTF_8);
                asyncHttpRequest(url);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("qq", e.toString());
            OrzMC.logger().info(e.toString());
        }
    }

    @Override
    protected void sendPrivate(String message) {
        if (!this.isEnable()) {
            return;
        }
        try {
            String userId = botConfig.getString("qq_admin_id");
            for (String part : formatter.format(message, MessageEnvelope.Format.DEFAULT)) {
                String url = botConfig.getString("qq_bot_api_server") + "/send_msg?user_id=" + userId + "&message="
                        + URLEncoder.encode(part, StandardCharsets.UTF_8);
                asyncHttpRequest(url);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("qq", e.toString());
            OrzMC.logger().info(e.toString());
        }
    }

    @Override
    protected void sendChannel(String channelKey, String message) {
        if (!this.isEnable()) {
            return;
        }
        try {
            String groupId = botConfig.getString("channels." + channelKey + ".qq");
            if (groupId == null || groupId.isEmpty()) {
                sendPublic(message);
                return;
            }
            for (String part : formatter.format(message, MessageEnvelope.Format.DEFAULT)) {
                String url = botConfig.getString("qq_bot_api_server") + "/send_group_msg?group_id=" + groupId
                        + "&message=" + URLEncoder.encode(part, StandardCharsets.UTF_8);
                asyncHttpRequest(url);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("qq", e.toString());
            OrzMC.logger().info(e.toString());
        }
    }

    public void processJsonStringPayload(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            if (json.get("group_id") == null || json.get("raw_message") == null) {
                return;
            }
            String groupId = json.get("group_id").getAsString();
            String message = json.get("raw_message").getAsString().trim();
            String senderRole = json.get("sender").getAsJsonObject().get("role").getAsString();
            boolean isOwner = senderRole.equals("owner");
            boolean isAdmin = senderRole.equals("admin");
            String qqGroupId = botConfig.getString("qq_group_id");
            if (groupId.equals(qqGroupId)) {
                BotInboundDispatcher.dispatch(inboundHandler, message, isAdmin || isOwner, this::send);
            }
        } catch (Exception e) {
            OrzMC.logger().info(e.toString());
        }
    }

    private void asyncHttpRequest(String url) {
        try {
            int retries = botConfig.getInt("http_max_retries");
            long connectSec = botConfig.getLong("http_connect_timeout_seconds");
            long requestSec = botConfig.getLong("http_request_timeout_seconds");
            AsyncHttp.get(
                            url,
                            this.httpServerHeaderMap(),
                            Duration.ofSeconds(connectSec <= 0 ? 3 : connectSec),
                            Duration.ofSeconds(requestSec <= 0 ? 3 : requestSec),
                            retries <= 0 ? 3 : retries)
                    .thenAcceptAsync(response -> {
                        OrzMC.debugInfo("Response Code : " + response.toString());
                        if (response.statusCode() == 200) {
                            HealthRegistry.setHttpOk("qq", true);
                            HealthRegistry.setLastError("qq", null);
                        }
                    })
                    .exceptionally(e -> {
                        HealthRegistry.setHttpOk("qq", false);
                        HealthRegistry.setLastError("qq", e.toString());
                        throttledLogger.error("qq-http", "QQ机器人无法连接，工作异常: " + e);
                        return null;
                    });
        } catch (Exception e) {
            HealthRegistry.setLastError("qq", e.toString());
            OrzMC.logger().severe(e.toString());
        }
    }

    private Map<String, String> httpServerHeaderMap() {
        Map<String, String> httpHeaders = new HashMap<>();
        String httpServerBearerToken = botConfig.getString("qq_bot_api_server_token");
        if (httpServerBearerToken != null && !httpServerBearerToken.isEmpty()) {
            httpHeaders.put("Authorization", "Bearer " + httpServerBearerToken);
        }
        return httpHeaders;
    }

    private Map<String, String> websocketServerHeaderMap() {
        Map<String, String> httpHeaders = new HashMap<>();
        String websocketServerBearerToken = botConfig.getString("qq_bot_ws_server_token");
        if (websocketServerBearerToken != null && !websocketServerBearerToken.isEmpty()) {
            httpHeaders.put("Authorization", "Bearer " + websocketServerBearerToken);
        }
        return httpHeaders;
    }

    public void setupWebSocketClient() {
        String wsServer = botConfig.getString("qq_bot_ws_server");
        if (!this.isEnable() || wsServer == null || wsServer.isEmpty()) {
            return;
        }
        try {
            int wsRetries = botConfig.getInt("ws_max_retries");
            long wsBaseMs = botConfig.getLong("ws_base_retry_ms");
            long wsMaxMs = botConfig.getLong("ws_max_delay_ms");
            int wsJitterPercent = botConfig.getInt("ws_jitter_percent");
            long wsStableResetMs = botConfig.getLong("ws_stable_reset_ms");
            String bizHeartBeatPayload = new Gson().toJson(Map.of("action", "get_status"));
            webSocketClient =
                    new RobustWebSocketClient(
                            wsServer,
                            throttledLogger,
                            wsRetries <= 0 ? 10 : wsRetries,
                            wsBaseMs <= 0 ? 5000 : wsBaseMs,
                            wsMaxMs <= 0 ? 60000 : wsMaxMs,
                            wsJitterPercent <= 0 ? 10 : wsJitterPercent,
                            wsStableResetMs <= 0 ? 20000 : wsStableResetMs,
                            this.websocketServerHeaderMap(),
                            bizHeartBeatPayload,
                            new com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketEventListener() {
                                @Override
                                public void onOpen() {
                                    HealthRegistry.setWsConnected("qq", true);
                                }

                                @Override
                                public void onClose(int code, String reason, boolean remote) {
                                    HealthRegistry.setWsConnected("qq", false);
                                }

                                @Override
                                public void onError(Exception ex) {
                                    HealthRegistry.setLastError("qq", ex.toString());
                                    throttledLogger.error("qq-ws", "QQ机器人WebSocket异常: " + ex);
                                }
                            }) {
                        @Override
                        public void handleMessage(String message) {
                            processJsonStringPayload(message);
                        }
                    };

            webSocketClient.connect();
        } catch (Exception e) {
            HealthRegistry.setLastError("qq", e.toString());
            OrzMC.logger().info(e.toString());
        }
    }

    public void shutdownWebSocketClient() {
        if (webSocketClient == null) {
            return;
        }
        webSocketClient.disconnect();
    }
}
