package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OrzBotManager {
    private final OrzMC plugin;
    private Map<String, OrzBaseBot> bots;
    private final ConcurrentLinkedQueue<PendingMessage> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean initialized = false;

    private record PendingMessage(String message, boolean isPrivate) {}

    private record PendingChannelMessage(String channelKey, String message) {}

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
            ThrottledLogger.info("bots-init", "机器人尚未就绪，消息已缓存");
            pending.add(new PendingMessage(message, isPrivate));
            return;
        }
        bots.values().forEach(bot -> {
            try {
                if (isPrivate) {
                    bot.sendPrivateMessage(message);
                } else {
                    bot.sendMessage(message);
                }
            } catch (Exception e) {
                ThrottledLogger.warning("bot-send", "消息发送失败: " + bot.getClass().getSimpleName() + " - " + e);
            }
        });
    }

    public void sendToChannel(String channelKey, String message) {
        if (!initialized) {
            ThrottledLogger.info("bots-init", "机器人尚未就绪，消息已缓存");
            pendingChannel.add(new PendingChannelMessage(channelKey, message));
            return;
        }
        bots.values().forEach(bot -> {
            try {
                bot.sendToChannel(channelKey, message);
            } catch (Exception e) {
                ThrottledLogger.warning(
                        "bot-send", "指定频道消息发送失败: " + bot.getClass().getSimpleName() + " - " + e);
            }
        });
    }

    public void tearDown() {
        initialized = false;
        bots.values().forEach(OrzBaseBot::teardown);
        bots = Collections.emptyMap();
        pending.clear();
        pendingChannel.clear();
    }

    private void flushPending() {
        PendingMessage pm;
        while ((pm = pending.poll()) != null) {
            sendMessage(pm.message, pm.isPrivate);
        }
        PendingChannelMessage pcm;
        while ((pcm = pendingChannel.poll()) != null) {
            sendToChannel(pcm.channelKey, pcm.message);
        }
    }

    private final ConcurrentLinkedQueue<PendingChannelMessage> pendingChannel = new ConcurrentLinkedQueue<>();
}
