package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.teleport.EntityTeleportPolicyService;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;

public class OrzTPEvent extends OrzBaseListener {
    private final EntityTeleportPolicyService policyService;
    private final ServerFacade server;

    public OrzTPEvent(OrzMC plugin, ServerFacade server, EntityTeleportPolicyService policyService) {
        super(plugin);
        this.server = server;
        this.policyService = policyService;
    }

    @EventHandler
    public void onEntityTeleport(EntityTeleportEvent event) {
        // 下界传送门穿越（EntityPortalEvent/EntityPortalExitEvent，后者是前者子类）
        // 始终放行：本策略只限制命令/插件触发的传送，避免掉落物/矿车/船/生物过门被误拦。
        if (event instanceof EntityPortalEvent) {
            return;
        }
        if (!policyService.shouldCancel(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        server.logger().info("实体传送被禁用:" + event.getEntity().getName());
    }
}
