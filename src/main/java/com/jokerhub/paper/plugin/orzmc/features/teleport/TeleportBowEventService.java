package com.jokerhub.paper.plugin.orzmc.features.teleport;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

public final class TeleportBowEventService {
    private final TeleportBowService service;

    public TeleportBowEventService(TeleportBowService service) {
        this.service = service;
    }

    public void handleProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player player) {
                if (!service.isTPBowArrow(arrow)) return;
                service.handleArrowHit(arrow, player);
            }
        }
    }

    public void handleShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player) {
            service.markArrow(event);
        }
    }
}
