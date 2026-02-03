package com.jokerhub.paper.plugin.orzmc.infra.notify.sinks;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import net.kyori.adventure.text.Component;

public class BotNotifierSink implements NotifierSink {
    private final Notifier notifier;

    public BotNotifierSink(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void server(Component message) {
        OrzMC.server().sendMessage(message);
    }

    @Override
    public void event(String key, MessageEnvelope envelope) {
        notifier.routeEvent(key, envelope);
    }
}
