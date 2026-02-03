package com.jokerhub.paper.plugin.orzmc.infra.portal;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface IPortalService {
    void setup();

    void tearDown();

    PortalInfo createPortal(Player player, String host, int port);

    String findTarget(Location from);

    int removeByTarget(String target);
}
