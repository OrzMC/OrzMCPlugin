package com.jokerhub.paper.plugin.orzmc.commands;

import com.jokerhub.paper.plugin.orzmc.features.bot.BotStatusService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class OrzBotStatus implements CommandExecutor {
    private final BotStatusService statusService;
    private final BotMessageService botMessageService;

    public OrzBotStatus(BotStatusService statusService, BotMessageService botMessageService) {
        this.statusService = statusService;
        this.botMessageService = botMessageService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        botMessageService.tryReconnectQqWsIfDisconnected();
        sender.sendMessage(statusService.buildStatusMessage());
        return true;
    }
}
