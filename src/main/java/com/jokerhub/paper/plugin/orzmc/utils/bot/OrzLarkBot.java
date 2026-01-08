package com.jokerhub.paper.plugin.orzmc.utils.bot;

import com.google.gson.Gson;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.AsyncHttp;
import com.jokerhub.paper.plugin.orzmc.utils.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.utils.ThrottledLogger;

import java.time.Duration;
import java.util.HashMap;

public class OrzLarkBot extends OrzBaseBot {
    public OrzLarkBot(OrzMC plugin) {
        super(plugin);
    }

    @Override
    public boolean isEnable() {
        return botConfig.getBoolean("enable_lark_bot");
    }

    @Override
    public void setup() {

    }

    @Override
    public void teardown() {

    }

    public void sendMessage(String msg) {
        if (!this.isEnable()) {
            OrzMC.debugInfo("Lark Bot Disabled!");
            return;
        }
        HealthRegistry.setEnabled("lark", true);
        try {
            String larkBotWebhookUrl = botConfig.getString("lark_bot_webhook");
            asyncHttpRequest(larkBotWebhookUrl, msg);
        } catch (Exception e) {
            HealthRegistry.setLastError("lark", e.toString());
            OrzMC.logger().info(e.toString());
        }
    }

    @Override
    public void sendPrivateMessage(String message) {

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
        long throttleMs = botConfig.getLong("log_throttle_ms");
        AsyncHttp.postJson(url, postBodyJsonString, null, Duration.ofSeconds(connectSec <= 0 ? 3 : connectSec), Duration.ofSeconds(requestSec <= 0 ? 3 : requestSec), retries <= 0 ? 3 : retries)
                .thenAcceptAsync(response -> OrzMC.debugInfo("Response : " + response.toString()))
                .exceptionally(e -> {
                    HealthRegistry.setHttpOk("lark", false);
                    HealthRegistry.setLastError("lark", e.toString());
                    ThrottledLogger.error("lark-http", "Lark机器人无法连接，工作异常: " + e, throttleMs <= 0 ? 5000 : throttleMs);
                    return null;
                });
    }
}
