package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.teleport.EntityTeleportPolicyService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTeleportEvent;

public class OrzTPEvent extends OrzBaseListener {
    private final EntityTeleportPolicyService policyService = new EntityTeleportPolicyService();
    private final ServerFacade server;

    public OrzTPEvent(OrzMC plugin, ServerFacade server) {
        super(plugin);
        this.server = server;
    }

    @EventHandler
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!policyService.shouldCancel(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        server.logger().info("实体传送被禁用:" + event.getEntity().getName());
    }
}
