package com.jokerhub.paper.plugin.orzmc.features.teleport;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Tameable;

public final class EntityTeleportPolicyService {
    public boolean shouldCancel(Entity entity) {
        if (entity instanceof Tameable) {
            return false;
        }
        if (entity instanceof Enderman || entity instanceof ArmorStand || entity instanceof Shulker) {
            return false;
        }
        return true;
    }
}
