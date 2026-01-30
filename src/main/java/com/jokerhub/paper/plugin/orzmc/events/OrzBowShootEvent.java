package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.teleport.TeleportBowService;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.NotNull;

public class OrzBowShootEvent extends OrzBaseListener {
    public OrzBowShootEvent(OrzMC plugin) {
        super(plugin);
    }

    @EventHandler
    public void onBowShoot(@NotNull ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player player) {
                TeleportBowService svc = new TeleportBowService();
                if (!svc.isTPBowArrow(arrow)) return;
                svc.handleArrowHit(arrow, player);
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(@NotNull EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player) {
            new TeleportBowService().markArrow(event);
        }
    }
}
