package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.infra.portal.PortalService;

/**
 * 传送门模块。
 *
 * <p>管理跨服传送门的创建、查找和移除，持久化到 portals.yml。</p>
 */
public final class PortalModule implements ServiceModule {

    private final PortalService portalService;

    public PortalModule(PlatformModule platform) {
        this.portalService = new PortalService(platform.configService());
    }

    @Override
    public void setup() {
        portalService.setup();
    }

    @Override
    public void tearDown() {
        portalService.tearDown();
    }

    public PortalService portalService() {
        return portalService;
    }

    public PortalPort portalPort() {
        return portalService;
    }
}
