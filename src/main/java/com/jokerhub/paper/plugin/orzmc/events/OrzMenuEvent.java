package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.commands.OrzMenuCommand;
import com.jokerhub.paper.plugin.orzmc.utils.OrzTextStyles;
import org.bukkit.Material;
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
        String title = event.getView().getTitle();
        if (title != null && title.equals(OrzMenuCommand.name)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                p.sendMessage(OrzTextStyles.info("功能开发中"));
            }
        }
    }
}
