package com.jokerhub.paper.plugin.orzmc.infra.notify.sinks;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import net.kyori.adventure.text.Component;

public class BotNotifierSink implements NotifierSink {
    private final ServerAccess server;
    private final Notifier notifier;

    public BotNotifierSink(ServerAccess server, Notifier notifier) {
        this.server = server;
        this.notifier = notifier;
    }

    @Override
    public void server(Component message) {
        server.server().sendMessage(message);
    }

    @Override
    public void event(String key, MessageEnvelope envelope) {
        notifier.routeEvent(key, envelope);
    }
}
