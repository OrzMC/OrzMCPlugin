package com.jokerhub.paper.plugin.orzmc.infra.notify;

import net.kyori.adventure.text.Component;

public interface NotifierSink {
    void server(Component message);

    void botPublic(String message);

    void botPrivate(String message);

    void botChannel(String channelKey, String message);

    void event(String key, String message);
}
