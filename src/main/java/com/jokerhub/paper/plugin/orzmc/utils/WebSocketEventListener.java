package com.jokerhub.paper.plugin.orzmc.utils;

public interface WebSocketEventListener {
    void onOpen();

    void onClose(int code, String reason, boolean remote);

    void onError(Exception ex);
}
