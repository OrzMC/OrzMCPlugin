package com.jokerhub.paper.plugin.orzmc.features.menu;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public final class MenuEventService {
    private final MenuService service;

    public MenuEventService(OrzTextStyles styles) {
        this.service = new MenuService(styles);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.CHEST) return;
        if (event.getView().getTopInventory().getHolder() instanceof OrzMenuHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            service.onClick(p, clicked);
        }
    }
}
