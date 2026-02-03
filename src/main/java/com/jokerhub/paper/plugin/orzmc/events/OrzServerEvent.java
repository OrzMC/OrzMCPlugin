package com.jokerhub.paper.plugin.orzmc.events;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.server.ServerEventService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;

public class OrzServerEvent extends OrzBaseListener {
    private final ServerEventService service;

    public OrzServerEvent(OrzMC plugin, ConfigService configService, OrzTextStyles styles, Notifier notifier) {
        super(plugin);
        this.service = new ServerEventService(configService, styles, notifier);
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
