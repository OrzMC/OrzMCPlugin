package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BotRouter {
    private final ThrottledLogger throttledLogger;
    private List<BotAdapter> adapters = Collections.emptyList();
    private final ConcurrentLinkedQueue<MessageEnvelope> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean initialized = false;

    public BotRouter(ThrottledLogger throttledLogger) {
        this.throttledLogger = throttledLogger;
    }

    public void setAdapters(List<BotAdapter> adapters) {
        this.adapters = adapters == null ? Collections.emptyList() : adapters;
    }

    public void setup() {
        adapters.forEach(BotAdapter::setup);
        initialized = true;
        flushPending();
    }

    public void teardown() {
        initialized = false;
        adapters.forEach(BotAdapter::teardown);
        adapters = Collections.emptyList();
        pending.clear();
    }

    public void route(MessageEnvelope envelope) {
        if (!initialized) {
            throttledLogger.info("bots-init", "机器人尚未就绪，消息已缓存");
            pending.add(envelope);
            return;
        }
        for (BotAdapter adapter : adapters) {
            try {
                adapter.send(envelope);
            } catch (Exception e) {
                throttledLogger.warning(
                        "bot-send", "消息发送失败: " + adapter.getClass().getSimpleName() + " - " + e);
            }
        }
    }

    private void flushPending() {
        MessageEnvelope env;
        while ((env = pending.poll()) != null) {
            route(env);
        }
    }
}
