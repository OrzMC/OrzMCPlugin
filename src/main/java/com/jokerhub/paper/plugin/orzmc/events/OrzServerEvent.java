package com.jokerhub.paper.plugin.orzmc.events;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerEventService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;

public class OrzServerEvent extends OrzBaseListener {
    private final ServerEventService service;

    public OrzServerEvent(OrzMC plugin, ServerEventService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler
    public void onException(ServerExceptionEvent event) {
        service.handleException(event.getException());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        service.handleServerLoad(event);
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        service.applyMaintenanceMotd(event);
    }
}
