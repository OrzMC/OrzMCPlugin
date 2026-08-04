package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;

public interface BotMessageService {
    void setup();

    void send(MessageEnvelope envelope);

    default void tryReconnectIfDisconnected() {}

    /** Reconcile the active connection with the current easybot.yml configuration. */
    default void reloadConfig() {}

    void tearDown();
}
