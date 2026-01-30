package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzMenuHolder;
import com.jokerhub.paper.plugin.orzmc.features.menu.MenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public class OrzMenuEvent extends OrzBaseListener {
    public OrzMenuEvent(OrzMC plugin) {
        super(plugin);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.CHEST) return;
        if (event.getView().getTopInventory().getHolder() instanceof OrzMenuHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            new MenuService().onClick(p, clicked);
        }
    }
}
