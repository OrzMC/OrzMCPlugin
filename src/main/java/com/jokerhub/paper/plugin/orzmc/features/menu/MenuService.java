package com.jokerhub.paper.plugin.orzmc.features.menu;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MenuService {
    private final OrzTextStyles styles;

    public MenuService(OrzTextStyles styles) {
        this.styles = styles;
    }

    public Inventory buildMenu() {
        Component title = styles.info("OrzMC Menu");
        OrzMenuHolder holder = new OrzMenuHolder();
        Inventory menu = Bukkit.createInventory(holder, InventoryType.CHEST, title);
        holder.setInventory(menu);
        ItemStack item1 = new ItemStack(Material.CHIPPED_ANVIL);
        menu.addItem(item1);
        return menu;
    }

    public void openMenu(Player p) {
        Inventory menu = buildMenu();
        p.openInventory(menu);
    }

    public void onClick(Player p, ItemStack clicked) {
        if (clicked != null && clicked.getType() != Material.AIR) {
            p.sendMessage(styles.info("功能开发中"));
        }
    }
}
