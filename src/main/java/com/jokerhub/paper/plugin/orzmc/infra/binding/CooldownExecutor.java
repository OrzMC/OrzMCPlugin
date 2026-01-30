package com.jokerhub.paper.plugin.orzmc.infra.binding;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class CooldownExecutor implements CommandExecutor {
    private final String commandName;
    private final CommandExecutor delegate;
    private final int seconds;

    public CooldownExecutor(String commandName, CommandExecutor delegate, int seconds) {
        this.commandName = commandName;
        this.delegate = delegate;
        this.seconds = seconds;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String key = commandName + "|" + sender.getName();
        if (CooldownRegistry.isCoolingDown(key, seconds)) {
            sender.sendMessage(Component.text("命令冷却中，请稍后再试"));
            return true;
        }
        return delegate.onCommand(sender, command, label, args);
    }
}
