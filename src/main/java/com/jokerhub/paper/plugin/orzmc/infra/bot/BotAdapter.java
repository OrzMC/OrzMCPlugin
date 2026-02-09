package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;

public interface BotAdapter {
    boolean isEnable();

    void setup();

    void teardown();

    void send(MessageEnvelope envelope);
}
