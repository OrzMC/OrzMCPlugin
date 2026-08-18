package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.security.CommandGuardEventService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 危险命令拦截监听器（安全加固 P0-3）。
 *
 * <p>{@link EventPriority#LOWEST} 最早介入：BLOCK 取消后事件链上后续监听器不再触发，
 * 避免被拦截命令的副作用仍被执行。</p>
 */
public class OrzCommandGuardEvent extends OrzBaseListener {
    private final CommandGuardEventService service;

    public OrzCommandGuardEvent(OrzMC plugin, CommandGuardEventService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(@NotNull PlayerCommandPreprocessEvent event) {
        service.onPlayerCommand(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(@NotNull ServerCommandEvent event) {
        service.onServerCommand(event);
    }
}
