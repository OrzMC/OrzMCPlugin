package com.jokerhub.paper.plugin.orzmc.features.portal;

import com.jokerhub.paper.plugin.orzmc.infra.portal.IPortalService;
import com.jokerhub.paper.plugin.orzmc.infra.server.OrzUtil;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerPortalEvent;

public final class PortalEventService {
    private final IPortalService portalService;

    public PortalEventService(IPortalService portalService) {
        this.portalService = portalService;
    }

    public void handle(PlayerPortalEvent event) {
        Location from = event.getFrom();
        String target = portalService.findTarget(from);
        if (target == null) return;
        event.setCancelled(true);
        String[] parts = target.split(":");
        String host = parts[0];
        String port = parts.length > 1 ? parts[1] : "25565";
        String cmd = "transfer " + host + " " + port + " " + event.getPlayer().getName();
        OrzUtil.executeConsoleCmd(() -> {}, cmd);
    }
}
