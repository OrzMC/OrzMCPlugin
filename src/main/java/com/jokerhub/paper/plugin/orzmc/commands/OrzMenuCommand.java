package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.features.menu.MenuCommandService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class OrzMenuCommand implements CommandExecutor {

    public static final String name = "OrzMC Menu";
    private final MenuCommandService service;

    public OrzMenuCommand(MenuCommandService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof org.bukkit.entity.Player p) {
            service.handle(p);
        }
        return false;
    }
}
