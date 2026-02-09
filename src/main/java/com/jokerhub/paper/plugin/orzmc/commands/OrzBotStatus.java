package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.features.bot.BotStatusService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class OrzBotStatus implements CommandExecutor {
    private final BotStatusService statusService;

    public OrzBotStatus(BotStatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        sender.sendMessage(statusService.buildStatusMessage());
        return true;
    }
}
