package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;

public interface BotMessageService {
    void setup();

    void send(MessageEnvelope envelope);

    void tearDown();
}
