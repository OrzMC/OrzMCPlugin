package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OrzTPBow implements CommandExecutor {

    public static final String name = "传送弓";
    private final TeleportBowService service;
    private final ServerFacade server;

    public OrzTPBow(ServerFacade server, TeleportBowService service) {
        this.server = server;
        this.service = service;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            service.giveAndEquip(player);
        } else {
            server.logger().info("不是玩家，此命令无效！");
        }
        return false;
    }
}
