package com.jokerhub.paper.plugin.orzmc.infra.notify.sinks;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.NotifierSink;
import net.kyori.adventure.text.Component;

public class BotNotifierSink implements NotifierSink {
    @Override
    public void server(Component message) {
        OrzMC.server().sendMessage(message);
    }

    @Override
    public void botPublic(String message) {
        OrzMC.plugin().sendPublicMessage(message);
    }

    @Override
    public void botPrivate(String message) {
        OrzMC.plugin().sendPrivateMessage(message);
    }

    @Override
    public void botChannel(String channelKey, String message) {
        OrzMC.plugin().sendToChannel(channelKey, message);
    }

    @Override
    public void event(String key, String message) {
        Notifier.event(key, message);
    }
}
