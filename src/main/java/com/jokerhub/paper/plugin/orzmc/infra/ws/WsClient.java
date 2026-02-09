package com.jokerhub.paper.plugin.orzmc.infra.ws;

public interface WsClient {
    void connect();

    void disconnect();

    void send(String message);
}
