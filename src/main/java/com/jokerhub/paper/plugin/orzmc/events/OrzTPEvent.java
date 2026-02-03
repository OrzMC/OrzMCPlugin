package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.teleport.EntityTeleportPolicyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTeleportEvent;

public class OrzTPEvent extends OrzBaseListener {
    private final EntityTeleportPolicyService policyService = new EntityTeleportPolicyService();

    public OrzTPEvent(OrzMC plugin) {
        super(plugin);
    }

    @EventHandler
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!policyService.shouldCancel(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        OrzMC.logger().info("实体传送被禁用:" + event.getEntity().getName());
    }
}
