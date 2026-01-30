package com.jokerhub.paper.plugin.orzmc.infra.binding;

import java.util.List;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventBinder {
    private EventBinder() {}

    public static void bind(JavaPlugin plugin, List<Listener> listeners) {
        listeners.forEach(eventListener -> plugin.getServer().getPluginManager().registerEvents(eventListener, plugin));
    }
}
