package com.jokerhub.paper.plugin.orzmc.utils.bot;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.ThrottledLogger;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OrzBotManager {
    private final OrzMC plugin;
    private Map<String, OrzBaseBot> bots;
    private final ConcurrentLinkedQueue<PendingMessage> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean initialized = false;

    private record PendingMessage(String message, boolean isPrivate) {
    }

    public OrzBotManager(OrzMC plugin) {
        this.plugin = plugin;
        this.bots = Collections.emptyMap();
    }

    public void setup() {
        bots = Map.of("qq", new OrzQQBot(plugin), "discord", new OrzDiscordBot(plugin), "lark", new OrzLarkBot(plugin));
        bots.values().forEach(OrzBaseBot::setup);
        initialized = true;
        flushPending();
    }

    public void sendMessage(String message, boolean isPrivate) {
        if (!initialized) {
            long throttleMs = plugin.configManager.getConfig("bot").getLong("log_throttle_ms");
            com.jokerhub.paper.plugin.orzmc.utils.ThrottledLogger.info("bots-init", "机器人尚未就绪，消息已缓存", throttleMs <= 0 ? 5000 : throttleMs);
            pending.add(new PendingMessage(message, isPrivate));
            return;
        }
        long throttleMs = plugin.configManager.getConfig("bot").getLong("log_throttle_ms");
        bots.values().forEach(bot -> {
            try {
                if (isPrivate) {
                    bot.sendPrivateMessage(message);
                } else {
                    bot.sendMessage(message);
                }
            } catch (Exception e) {
                ThrottledLogger.warning("bot-send", "消息发送失败: " + bot.getClass().getSimpleName() + " - " + e, throttleMs <= 0 ? 5000 : throttleMs);
            }
        });
    }

    public void tearDown() {
        initialized = false;
        bots.values().forEach(OrzBaseBot::teardown);
        bots = Collections.emptyMap();
        pending.clear();
    }

    private void flushPending() {
        PendingMessage pm;
        while ((pm = pending.poll()) != null) {
            sendMessage(pm.message, pm.isPrivate);
        }
    }
}
