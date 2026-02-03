package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.util.function.Consumer;

public interface BotInboundHandler {
    void handleMessage(String message, boolean isAdmin, Consumer<MessageEnvelope> callback);
}
