package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuEventService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

public class OrzMenuEvent extends OrzBaseListener {
    private final MenuEventService service;

    public OrzMenuEvent(OrzMC plugin, OrzTextStyles styles) {
        super(plugin);
        this.service = new MenuEventService(styles);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        service.handleClick(event);
    }
}
