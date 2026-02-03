package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.tnt.TntEventService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

public class OrzTNTEvent extends OrzBaseListener {
    private final TntEventService service;

    public OrzTNTEvent(
            OrzMC plugin,
            ConfigService configService,
            OrzTextStyles styles,
            Notifier notifier,
            ThrottledNotifier throttledNotifier) {
        super(plugin);
        this.service = new TntEventService(configService, styles, notifier, throttledNotifier);
    }

    @EventHandler
    public void onTNTPrime(@NotNull TNTPrimeEvent event) {
        service.onTNTPrime(event);
    }

    @EventHandler
    public void onPlaceBlock(@NotNull BlockPlaceEvent event) {
        service.onPlaceBlock(event);
    }

    @EventHandler
    public void onBlockPreDispense(@NotNull BlockPreDispenseEvent event) {
        service.onBlockPreDispense(event);
    }

    @EventHandler
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        service.onBlockExplode(event);
    }

    @EventHandler
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        service.onEntityExplode(event);
    }
}
