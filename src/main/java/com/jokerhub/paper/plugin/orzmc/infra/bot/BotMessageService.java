package com.jokerhub.paper.plugin.orzmc.infra.bot;

public interface BotMessageService {
    void setup();

    void send(MessageEnvelope envelope);

    void tearDown();
}
