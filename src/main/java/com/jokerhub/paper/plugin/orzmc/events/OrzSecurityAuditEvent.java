package com.jokerhub.paper.plugin.orzmc.events;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.server.StartupSecurityAuditService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerLoadEvent;

/** 启动安全自检（安全加固 P1-2）：{@link ServerLoadEvent} 时触发体检报告。 */
public class OrzSecurityAuditEvent extends OrzBaseListener {

    private final StartupSecurityAuditService service;

    public OrzSecurityAuditEvent(OrzMC plugin, StartupSecurityAuditService service) {
        super(plugin);
        this.service = service;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        service.run(event);
    }
}
