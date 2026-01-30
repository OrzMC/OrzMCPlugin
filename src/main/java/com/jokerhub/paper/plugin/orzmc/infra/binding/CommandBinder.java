package com.jokerhub.paper.plugin.orzmc.infra.binding;

import java.util.Map;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandBinder {
    private CommandBinder() {}

    public static void bind(JavaPlugin plugin, Map<String, CommandExecutor> handlers) {
        handlers.forEach((key, value) -> {
            PluginCommand cmd = plugin.getCommand(key);
            if (cmd != null) {
                cmd.setExecutor(value);
            }
        });
    }
}
