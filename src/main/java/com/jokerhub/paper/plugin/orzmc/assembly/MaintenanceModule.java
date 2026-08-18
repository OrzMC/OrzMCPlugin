package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.features.maintenance.ScheduledBackupService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;

/**
 * 世界维护模块。
 *
 * <p>管理世界备份和地图优化任务，以及按间隔触发的定时自动备份。</p>
 */
public final class MaintenanceModule implements ServiceModule {

    private final WorldMaintenanceService worldMaintenanceService;
    private final ScheduledBackupService scheduledBackupService;

    public MaintenanceModule(PlatformModule platform, BotModule botModule) {
        this.worldMaintenanceService = new WorldMaintenanceService(
                platform.serverFacade(), platform.configs(), platform.textStyles(), botModule.notifier());
        this.scheduledBackupService =
                new ScheduledBackupService(platform.serverFacade(), platform.configs(), worldMaintenanceService);
    }

    @Override
    public void setup() {
        scheduledBackupService.setup();
    }

    @Override
    public void tearDown() {
        scheduledBackupService.tearDown();
    }

    public WorldMaintenanceService worldMaintenanceService() {
        return worldMaintenanceService;
    }

    public ScheduledBackupService scheduledBackupService() {
        return scheduledBackupService;
    }
}
