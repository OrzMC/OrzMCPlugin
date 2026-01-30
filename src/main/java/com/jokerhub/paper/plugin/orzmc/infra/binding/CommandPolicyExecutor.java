package com.jokerhub.paper.plugin.orzmc.infra.binding;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class CommandPolicyExecutor implements CommandExecutor {
    private final String commandName;
    private final CommandExecutor delegate;
    private final int cooldownSeconds;
    private final boolean adminOnly;

    public CommandPolicyExecutor(String commandName, CommandExecutor delegate, int cooldownSeconds, boolean adminOnly) {
        this.commandName = commandName;
        this.delegate = delegate;
        this.cooldownSeconds = cooldownSeconds;
        this.adminOnly = adminOnly;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (adminOnly && sender instanceof Player p) {
            if (!(p.isOp() || p.hasPermission("orzmc.admin"))) {
                sender.sendMessage(Component.text("需要管理员权限"));
                return true;
            }
        }
        String key = commandName + "|" + sender.getName();
        if (CooldownRegistry.isCoolingDown(key, cooldownSeconds)) {
            sender.sendMessage(Component.text("命令冷却中，请稍后再试"));
            return true;
        }
        return delegate.onCommand(sender, command, label, args);
    }
}
