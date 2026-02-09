package com.jokerhub.paper.plugin.orzmc.features.server;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;

public final class ServerLifecycleService {
    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final Notifier notifier;

    public ServerLifecycleService(ServerFacade server, TypedConfigProvider configs, Notifier notifier) {
        this.server = server;
        this.configs = configs;
        this.notifier = notifier;
    }

    public void notifyServerStop() {
        String minecraftVersion = server.server().getMinecraftVersion();
        String msg = "Minecraft " + minecraftVersion + "\n" + "------" + "\n" + "服务停止" + "\n\n" + "停止状态无法响应命令消息";
        MessageEnvelope env = configs.renderEvent("server_stop", java.util.Map.of("message", msg));
        notifier.event("server_stop", env);
    }
}
