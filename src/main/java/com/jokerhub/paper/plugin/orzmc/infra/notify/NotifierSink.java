package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import net.kyori.adventure.text.Component;

public interface NotifierSink {
    void server(Component message);

    void event(String key, MessageEnvelope envelope);
}
