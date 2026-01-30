package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            new TeleportBowService().giveAndEquip(player);
        } else {
            OrzMC.logger().info("不是玩家，此命令无效！");
        }
        return false;
    }
}
