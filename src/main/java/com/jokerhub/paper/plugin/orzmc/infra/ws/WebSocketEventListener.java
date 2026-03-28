package com.jokerhub.paper.plugin.orzmc.infra.ws;

public interface WebSocketEventListener {
    void onOpen();

    void onClose(int code, String reason, boolean remote);

    void onError(Exception ex);
}
