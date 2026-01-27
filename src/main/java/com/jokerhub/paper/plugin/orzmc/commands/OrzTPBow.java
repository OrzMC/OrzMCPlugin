package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.utils.OrzConstants;
import com.jokerhub.paper.plugin.orzmc.utils.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.utils.OrzUtil;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class OrzTPBow implements CommandExecutor {

    public static final String name = "传送弓";

    public static Component logText(String content) {
        if (!content.isEmpty()) {
            return Component.text()
                    .append(OrzTextStyles.tpbowPrefix())
                    .append(Component.space())
                    .append(Component.text(content))
                    .build();
        }
        return Component.empty();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player player) {
            ItemStack teleport_bow = new ItemStack(Material.BOW);
            ItemMeta meta = teleport_bow.getItemMeta();
            meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
            TextComponent name = Component.text(OrzTPBow.name);
            meta.displayName(name);
            ArrayList<Component> loreList = new ArrayList<>();
            loreList.add(Component.text("可以把你传送到箭落地的位置"));
            meta.lore(loreList);
            NamespacedKey key = new NamespacedKey(OrzMC.plugin(), OrzConstants.TPBOW_KEY);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            teleport_bow.setItemMeta(meta);
            ItemStack prev = player.getInventory().getItemInMainHand();
            if (prev.getType() != Material.AIR) {
                player.getInventory().addItem(prev);
            }
            player.getInventory().setItemInMainHand(teleport_bow);
            ItemStack arrow = new ItemStack(Material.ARROW);
            player.getInventory().addItem(arrow);
            player.sendMessage(OrzUtil.successText("你获得了" + OrzTPBow.name));
        } else {
            OrzMC.logger().info("不是玩家，此命令无效！");
        }
        return false;
    }
}
