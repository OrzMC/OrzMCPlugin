package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowEventService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.NotNull;

public class OrzBowShootEvent extends OrzBaseListener {
    private final TeleportBowEventService service;

    public OrzBowShootEvent(OrzMC plugin, TeleportBowEventService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler
    public void onBowShoot(@NotNull ProjectileHitEvent event) {
        service.handleProjectileHit(event);
    }

    @EventHandler
    public void onEntityShootBow(@NotNull EntityShootBowEvent event) {
        service.handleShootBow(event);
    }
}
