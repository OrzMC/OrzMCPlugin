package com.jokerhub.paper.plugin.orzmc.core.ports.portal;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface PortalPort {
    void setup();

    void tearDown();

    PortalInfo createPortal(Player player, String host, int port);

    String findTarget(Location from);

    int removeByTarget(String target);
}
