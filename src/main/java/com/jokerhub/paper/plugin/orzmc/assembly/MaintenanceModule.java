package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;

/**
 * 世界维护模块。
 *
 * <p>管理世界备份和地图优化任务。</p>
 */
public final class MaintenanceModule implements ServiceModule {

    private final WorldMaintenanceService worldMaintenanceService;

    public MaintenanceModule(PlatformModule platform, BotModule botModule) {
        this.worldMaintenanceService = new WorldMaintenanceService(
                platform.serverFacade(), platform.configs(), platform.textStyles(), botModule.notifier());
    }

    public WorldMaintenanceService worldMaintenanceService() {
        return worldMaintenanceService;
    }
}
