package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.utils.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class OrzMenuCommand implements CommandExecutor {

    public static final String name = "OrzMC Menu";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p) {

            Component title = OrzTextStyles.info(OrzMenuCommand.name);
            OrzMenuHolder holder = new OrzMenuHolder();
            Inventory menu = Bukkit.createInventory(holder, InventoryType.CHEST, title);
            holder.setInventory(menu);

            ItemStack item1 = new ItemStack(Material.CHIPPED_ANVIL);
            menu.addItem(item1);

            p.openInventory(menu);
        }
        return false;
    }
}
