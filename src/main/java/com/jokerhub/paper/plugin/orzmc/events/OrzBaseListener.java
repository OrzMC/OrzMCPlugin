package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import org.bukkit.event.Listener;

public class OrzBaseListener implements Listener {
    final OrzMC plugin;
    private ServerFacade serverFacade;

    public OrzBaseListener(OrzMC plugin) {
        this.plugin = plugin;
    }

    /**
     * 统一调度门面（Paper/Folia 兼容）。子类一律经 {@code serverFacade().runAsync(...)}
     * 等走门面，避免旁路直连 {@code plugin.getServer().getScheduler()}（Folia 已移除该 API）。
     */
    protected final ServerFacade serverFacade() {
        if (serverFacade == null) {
            serverFacade = new ServerFacade(plugin);
        }
        return serverFacade;
    }
}
