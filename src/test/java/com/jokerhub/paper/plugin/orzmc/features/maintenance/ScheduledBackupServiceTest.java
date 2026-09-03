package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduledBackupServiceTest {

    private ServerFacade server;
    private TypedConfigProvider configs;
    private ScheduledTask task;

    @BeforeEach
    void setUp() {
        server = mock(ServerFacade.class);
        configs = mock(TypedConfigProvider.class);
        task = mock(ScheduledTask.class);
        when(server.runTaskTimer(any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
    }

    private static MaintenanceConfig config(long intervalHours) {
        return new MaintenanceConfig(false, 300L, 5, intervalHours);
    }

    @Test
    void setup_alwaysRegistersMinuteChecker() {
        // 无论开关状态都注册常驻检查器（每分钟 1200 ticks），以支持 /config reload 热重载即时生效
        when(configs.maintenance()).thenReturn(config(0L));
        ScheduledBackupService service =
                new ScheduledBackupService(server, configs, mock(WorldMaintenanceService.class));

        service.setup();

        verify(server).runTaskTimer(any(Runnable.class), eq(1200L), eq(1200L));
    }

    @Test
    void tick_disabled_neverFiresBackup() {
        when(configs.maintenance()).thenReturn(config(0L));
        WorldMaintenanceService maintenance = mock(WorldMaintenanceService.class);
        ScheduledBackupService service = new ScheduledBackupService(server, configs, maintenance);

        for (int i = 0; i < 200; i++) {
            service.tick();
        }

        verifyNoInteractions(maintenance);
    }

    @Test
    void tick_enabled_firesAfterIntervalHours() {
        when(configs.maintenance()).thenReturn(config(1L));
        WorldMaintenanceService maintenance = mock(WorldMaintenanceService.class);
        ScheduledBackupService service = new ScheduledBackupService(server, configs, maintenance);

        for (int i = 0; i < 59; i++) {
            service.tick();
        }
        verifyNoInteractions(maintenance); // 第 59 分钟仍未到点

        service.tick(); // 第 60 分钟 → 触发一次备份

        verify(maintenance).backup(eq(300L), eq(5), any());
    }

    @Test
    void tick_firesBackupOncePerInterval() {
        when(configs.maintenance()).thenReturn(config(1L));
        WorldMaintenanceService maintenance = mock(WorldMaintenanceService.class);
        ScheduledBackupService service = new ScheduledBackupService(server, configs, maintenance);

        for (int i = 0; i < 120; i++) {
            service.tick();
        }

        verify(maintenance, times(2)).backup(eq(300L), eq(5), any());
    }

    @Test
    void tick_intervalChangedViaReload_reschedulesFromNow() {
        // 热重载：2 小时 → 1 小时，配置变化后的下一个检查点即按新间隔重排倒计时
        when(configs.maintenance()).thenReturn(config(2L));
        WorldMaintenanceService maintenance = mock(WorldMaintenanceService.class);
        ScheduledBackupService service = new ScheduledBackupService(server, configs, maintenance);

        for (int i = 0; i < 30; i++) {
            service.tick(); // 2 小时计划下累计 30 分钟，未到点
        }
        verifyNoInteractions(maintenance);

        when(configs.maintenance()).thenReturn(config(1L)); // 模拟 /config reload 把间隔改为 1 小时
        for (int i = 0; i < 60; i++) {
            service.tick(); // 按新间隔重新累计
        }

        verify(maintenance, times(1)).backup(eq(300L), eq(5), any());
    }

    @Test
    void tick_disabledMidRun_stopsFiring() {
        when(configs.maintenance()).thenReturn(config(1L));
        WorldMaintenanceService maintenance = mock(WorldMaintenanceService.class);
        ScheduledBackupService service = new ScheduledBackupService(server, configs, maintenance);

        for (int i = 0; i < 30; i++) {
            service.tick();
        }
        when(configs.maintenance()).thenReturn(config(0L)); // 模拟 /config reload 关闭
        for (int i = 0; i < 200; i++) {
            service.tick();
        }

        verifyNoInteractions(maintenance);
    }

    @Test
    void repeatedTick_backupRunsExclusive_doesNotStack() {
        // 真实 WorldMaintenanceService：runExclusive 的 AtomicBoolean 互斥，
        // 前一次备份进行中时再次触发直接跳过（不叠加第二次踢人/save-off）。
        when(configs.maintenance()).thenReturn(config(1L));
        when(configs.templates()).thenReturn(Templates.from(new YamlConfiguration()));
        OrzTextStyles styles = mock(OrzTextStyles.class);
        WorldMaintenanceService maintenance = new WorldMaintenanceService(
                server, configs, styles, mock(Notifier.class), new MaintenanceModeService());
        ScheduledBackupService service = new ScheduledBackupService(server, configs, maintenance);

        for (int i = 0; i < 60; i++) { // 第一个周期到点 → 备份启动（异步，进行中）
            service.tick();
        }
        assertTrue(maintenance.isRunning());

        for (int i = 0; i < 60; i++) { // 第二个周期到点 → runExclusive 互斥，跳过
            service.tick();
        }

        verify(server, times(1)).runSync(any(Runnable.class));
        assertTrue(maintenance.isRunning());
    }

    @Test
    void tearDown_cancelsScheduledTask() {
        when(configs.maintenance()).thenReturn(config(1L));
        ScheduledBackupService service =
                new ScheduledBackupService(server, configs, mock(WorldMaintenanceService.class));
        service.setup();

        service.tearDown();

        verify(task).cancel();
    }
}
