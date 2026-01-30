package com.jokerhub.paper.plugin.orzmc.infra.binding;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class InterceptorExecutor implements CommandExecutor {
    private final String commandName;
    private final CommandExecutor delegate;
    private final List<CommandInterceptor> interceptors;

    public InterceptorExecutor(String commandName, CommandExecutor delegate, List<CommandInterceptor> interceptors) {
        this.commandName = commandName;
        this.delegate = delegate;
        this.interceptors = interceptors;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        for (CommandInterceptor ci : interceptors) {
            Component res = ci.preHandle(sender, commandName);
            if (res != null) {
                sender.sendMessage(res);
                return true;
            }
        }
        return delegate.onCommand(sender, command, label, args);
    }
}
