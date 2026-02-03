package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.util.function.Consumer;

public final class BotInboundDispatcher {
    private BotInboundDispatcher() {}

    public static void dispatch(
            BotInboundHandler handler, String content, boolean isAdmin, Consumer<MessageEnvelope> sink) {
        handler.handleMessage(content, isAdmin, env -> {
            if (env != null) {
                sink.accept(env);
            }
        });
    }
}
