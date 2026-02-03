package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.portal.PortalEventService;
import com.jokerhub.paper.plugin.orzmc.infra.portal.IPortalService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerPortalEvent;

public class OrzPortalEvent extends OrzBaseListener {
    private final PortalEventService service;

    public OrzPortalEvent(OrzMC plugin, IPortalService portalService) {
        super(plugin);
        this.service = new PortalEventService(portalService);
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        service.handle(event);
    }
}
