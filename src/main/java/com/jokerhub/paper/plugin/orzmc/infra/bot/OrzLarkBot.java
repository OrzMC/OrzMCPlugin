package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.google.gson.Gson;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import java.time.Duration;
import java.util.HashMap;

public class OrzLarkBot extends OrzBaseBot {
    private final MessageFormatter formatter;
    private final ThrottledLogger throttledLogger;

    public OrzLarkBot(
            ServerAccess server,
            ServerLogger logger,
            ConfigService configService,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger) {
        super(server, logger, configService);
        this.formatter = formatter;
        this.throttledLogger = throttledLogger;
    }

    @Override
    public boolean isEnable() {
        return botConfig.getBoolean("enable_lark_bot");
    }

    @Override
    public void setup() {}

    @Override
    public void teardown() {}

    @Override
    protected void sendPublic(String msg) {
        if (!this.isEnable()) {
            return;
        }
        HealthRegistry.setEnabled("lark", true);
        try {
            String larkBotWebhookUrl = botConfig.getString("lark_bot_webhook");
            for (String part : formatter.format(msg, MessageEnvelope.Format.DEFAULT)) {
                asyncHttpRequest(larkBotWebhookUrl, part);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("lark", e.toString());
            this.logger.logger().info(e.toString());
        }
    }

    @Override
    protected void sendPrivate(String message) {}

    @Override
    protected void sendChannel(String channelKey, String message) {
        if (!this.isEnable()) return;
        try {
            String url = botConfig.getString("channels." + channelKey + ".lark");
            if (url == null || url.isEmpty()) {
                sendPublic(message);
                return;
            }
            for (String part : formatter.format(message, MessageEnvelope.Format.DEFAULT)) {
                asyncHttpRequest(url, part);
            }
        } catch (Exception e) {
            HealthRegistry.setLastError("lark", e.toString());
            this.logger.logger().info(e.toString());
        }
    }

    private void asyncHttpRequest(String url, String msg) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("msg_type", "text");
        HashMap<String, String> content = new HashMap<>();
        content.put("text", msg);
        params.put("content", content);
        String postBodyJsonString = new Gson().toJson(params);
        int retries = botConfig.getInt("http_max_retries");
        long connectSec = botConfig.getLong("http_connect_timeout_seconds");
        long requestSec = botConfig.getLong("http_request_timeout_seconds");
        AsyncHttp.postJson(
                        url,
                        postBodyJsonString,
                        null,
                        Duration.ofSeconds(connectSec <= 0 ? 3 : connectSec),
                        Duration.ofSeconds(requestSec <= 0 ? 3 : requestSec),
                        retries <= 0 ? 3 : retries)
                .thenAcceptAsync(response -> {
                    throttledLogger.info("lark-http", "Response : " + response);
                    if (response.statusCode() == 200) {
                        HealthRegistry.setHttpOk("lark", true);
                        HealthRegistry.setLastError("lark", null);
                    }
                })
                .exceptionally(e -> {
                    HealthRegistry.setHttpOk("lark", false);
                    HealthRegistry.setLastError("lark", e.toString());
                    throttledLogger.error("lark-http", "Lark机器人无法连接，工作异常: " + e);
                    return null;
                });
    }
}
