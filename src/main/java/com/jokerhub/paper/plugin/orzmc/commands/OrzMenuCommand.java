package com.jokerhub.paper.plugin.orzmc.commands;

import net.kyori.adventure.text.Component;
import com.jokerhub.paper.plugin.orzmc.utils.OrzTextStyles;
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
        if(sender instanceof Player p) {

            Component title = OrzTextStyles.info(OrzMenuCommand.name);
            Inventory menu = Bukkit.createInventory(p, InventoryType.CHEST, title);

            ItemStack item1 = new ItemStack(Material.CHIPPED_ANVIL);
            menu.addItem(item1);

            p.openInventory(menu);
            p.sendMessage(OrzTextStyles.info("打开 " + OrzMenuCommand.name));
        }
        return false;
    }
}
