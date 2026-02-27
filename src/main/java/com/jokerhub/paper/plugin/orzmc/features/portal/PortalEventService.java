package com.jokerhub.paper.plugin.orzmc.features.portal;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerAuthenticationService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;

public final class PortalEventService {
    private final ServerFacade server;
    private final PortalPort portalService;
    private final PlayerAuthenticationService authService;

    public PortalEventService(ServerFacade server, PortalPort portalService) {
        this.server = server;
        this.portalService = portalService;
        this.authService = new PlayerAuthenticationService();
    }

    public void handle(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        // 检查玩家是否已认证
        if (!authService.isAuthenticated(player)) {
            return;
        }

        Location from = event.getFrom();
        String target = portalService.findTarget(from);
        if (target == null) return;
        event.setCancelled(true);
        String[] parts = target.split(":");
        String host = parts[0];
        String port = parts.length > 1 ? parts[1] : "25565";
        String cmd = "transfer " + host + " " + port + " " + player.getName();
        server.executeConsoleCommands(() -> {
        }, cmd);
    }
}
