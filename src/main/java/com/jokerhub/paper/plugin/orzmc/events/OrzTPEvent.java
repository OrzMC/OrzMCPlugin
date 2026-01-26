package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTeleportEvent;

public class OrzTPEvent extends OrzBaseListener {
    public OrzTPEvent(OrzMC plugin) {
        super(plugin);
    }
    @EventHandler
    public void onEntityTeleport(EntityTeleportEvent event) {

        if(event.getEntity() instanceof Tameable) {
            return;
        }

        if(event.getEntity() instanceof Enderman || event.getEntity() instanceof ArmorStand || event.getEntity() instanceof Shulker) {
            return;
        }

        // 禁用tp实体
        event.setCancelled(true);
        OrzMC.logger().info("实体传送被禁用:" + event.getEntity().getName());
    }
}
