package com.jokerhub.paper.plugin.orzmc.infra.server;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerFacade implements ServerAccess, ServerLogger, ServerScheduler {
    private final JavaPlugin plugin;

    public ServerFacade(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Server server() {
        return plugin.getServer();
    }

    public Logger logger() {
        return plugin.getLogger();
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(plugin, value);
    }

    public void runSync(Runnable task) {
        server().getScheduler().runTask(plugin, task);
    }

    public void runAsync(Runnable task) {
        server().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runLater(Runnable task, long delayTicks) {
        server().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public void executeConsoleCommands(Runnable after, String... consoleCmds) {
        ConsoleCommandSender console = server().getConsoleSender();
        runSync(() -> {
            for (String consoleCmd : consoleCmds) {
                server().dispatchCommand(console, consoleCmd);
            }
            if (after != null) {
                after.run();
            }
        });
    }
}
