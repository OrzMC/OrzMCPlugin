package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.features.maintenance.ScheduledBackupService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import java.io.File;

/**
 * 世界维护模块。
 *
 * <p>管理世界备份和地图优化任务，以及按间隔触发的定时自动备份。</p>
 */
public final class MaintenanceModule implements ServiceModule {

    private final WorldMaintenanceService worldMaintenanceService;
    private final ScheduledBackupService scheduledBackupService;
    private final PlatformModule platform;

    public MaintenanceModule(PlatformModule platform, BotModule botModule) {
        this.platform = platform;
        this.worldMaintenanceService = new WorldMaintenanceService(
                platform.serverFacade(), platform.configs(), platform.textStyles(), botModule.notifier());
        this.scheduledBackupService =
                new ScheduledBackupService(platform.serverFacade(), platform.configs(), worldMaintenanceService);
    }

    @Override
    public void setup() {
        scheduledBackupService.setup();
        // 启动清理：崩溃/断电可能导致 backup/tempDir 残留，删除防占用磁盘与污染下次备份。
        // 异步执行避免大残留阻塞服务器启动。
        org.bukkit.Server server = platform.serverFacade().server();
        if (server != null && server.getWorldContainer() != null) {
            File backupDir = new File(server.getWorldContainer(), "backup");
            java.util.logging.Logger logger = platform.serverFacade().logger();
            platform.serverFacade().runAsync(() -> WorldMaintenanceService.cleanupStaleBackupTemp(backupDir, logger));
        }
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
