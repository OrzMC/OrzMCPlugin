package com.jokerhub.paper.plugin.orzmc.infra.binding;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandBinder {
    private CommandBinder() {}

    public static void bind(JavaPlugin plugin, Map<String, CommandExecutor> handlers) {
        handlers.forEach((key, value) -> {
            // Paper 26.1: JavaPlugin#getCommand throws during startup for Paper plugins.
            // Use Bukkit#getPluginCommand instead which works in both legacy and Paper mode.
            PluginCommand cmd = Bukkit.getPluginCommand(key);
            if (cmd != null) {
                cmd.setExecutor(value);
            }
        });
    }
}
