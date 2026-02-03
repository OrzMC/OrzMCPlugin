package com.jokerhub.paper.plugin.orzmc.infra.bot;

public interface BotAdapter {
    boolean isEnable();

    void setup();

    void teardown();

    void send(MessageEnvelope envelope);
}
