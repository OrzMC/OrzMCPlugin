package com.jokerhub.paper.plugin.orzmc.infra.portal;

import com.jokerhub.paper.plugin.orzmc.features.portal.PortalService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface IPortalService {
    PortalService.PortalInfo createPortal(Player player, String host, int port);

    String findTarget(Location from);

    int removeByTarget(String target);
}
