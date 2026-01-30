package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.core.ServiceRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.server.OrzUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerPortalEvent;

public class OrzPortalEvent extends OrzBaseListener {
    public OrzPortalEvent(OrzMC plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        org.bukkit.Location from = event.getFrom();
        String target = ServiceRegistry.portal().findTarget(from);
        if (target == null) return;
        event.setCancelled(true);
        String[] parts = target.split(":");
        String host = parts[0];
        String port = parts.length > 1 ? parts[1] : "25565";
        String cmd = "transfer " + host + " " + port + " " + event.getPlayer().getName();
        OrzUtil.executeConsoleCmd(() -> {}, cmd);
    }
}
