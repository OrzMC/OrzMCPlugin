package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.core.ports.portal.PortalPort;
import com.jokerhub.paper.plugin.orzmc.infra.portal.PortalService;
import com.jokerhub.paper.plugin.orzmc.infra.server.BukkitRegionSchedulerProvider;

/**
 * 传送门模块。
 *
 * <p>管理跨服传送门的创建、查找和移除，持久化到 portals.yml。</p>
 */
public final class PortalModule implements ServiceModule {

    private final PortalService portalService;

    public PortalModule(PlatformModule platform) {
        // Folia：方块/标签操作经 region scheduler 投递到目标 chunk 的 region 线程
        this.portalService = new PortalService(
                platform.configService(),
                new BukkitRegionSchedulerProvider(platform.serverFacade().plugin()));
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
