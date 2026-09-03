package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceReason;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceCommandServiceTest {

    private ServerFacade server;
    private TypedConfigProvider configs;
    private OrzTextStyles styles;
    private MaintenanceModeService mode;
    private WorldMaintenanceService worldMaintenance;
    private MaintenanceCommandService service;

    @BeforeEach
    void setUp() {
        server = mock(ServerFacade.class);
        configs = mock(TypedConfigProvider.class);
        styles = mock(OrzTextStyles.class);
        mode = new MaintenanceModeService();
        worldMaintenance = mock(WorldMaintenanceService.class);
        // 手动维护踢人经 renderMotdText 读 templates.yml 场景模板（PR4 迁移），默认模板即可
        when(configs.templates()).thenReturn(Templates.from(new YamlConfiguration()));
        // warn 回显入参：不桩死成固定值，踢人断言需要真实渲染文本
        when(styles.warn(anyString())).thenAnswer(i -> Component.text((String) i.getArgument(0)));
        service = new MaintenanceCommandService(server, configs, styles, mode, worldMaintenance);
    }

    @Test
    void enterManual_setsManualModeAndKicksPlayersOnRegionThread() {
        Server bukkit = mock(Server.class);
        Player p = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(p.getScheduler()).thenReturn(scheduler);
        when(server.server()).thenReturn(bukkit);
        // getOnlinePlayers() 返回 Collection<? extends Player>，直接 thenReturn(List.of(p)) 触发
        // 通配符捕获不匹配（CAP#1 vs List<Player>），改用 doReturn().when()（仓库既有模式）
        doReturn(List.of(p)).when(bukkit).getOnlinePlayers();
        when(server.plugin()).thenReturn(mock(org.bukkit.plugin.java.JavaPlugin.class));
        // runSync 立即执行（模拟 global region 线程）
        doAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                })
                .when(server)
                .runSync(any(Runnable.class));
        // Folia：踢人消费者投递到 region 线程的 scheduler.run——同步执行以捕获 p.kick 入参
        doAnswer(inv -> {
                    ((Consumer<ScheduledTask>) inv.getArgument(1)).accept(mock(ScheduledTask.class));
                    return null;
                })
                .when(scheduler)
                .run(any(), any(), any());

        assertNull(service.enterManual());

        assertTrue(mode.isActive());
        assertEquals(MaintenanceReason.MANUAL, mode.reason());
        // Folia：踢人必须投递到玩家所在 region 线程（EntityScheduler）
        verify(scheduler).run(any(), any(), any());
        // 手动维护踢人文案 = templates.yml maintenance_motd_manual 场景模板（默认值，带场景词）
        String expected = MaintenanceModeService.renderMotdText(
                MaintenanceReason.MANUAL, Templates.from(new YamlConfiguration()), null);
        assertEquals("服务器维护中，请稍后再试", expected, "手动维护场景默认文案");
        verify(p).kick(Component.text(expected));
    }

    @Test
    void enterManual_refusedWhileBackupRunning() {
        when(worldMaintenance.isRunning()).thenReturn(true);

        String result = service.enterManual();

        assertNotNull(result);
        assertTrue(result.contains("备份"), "拒绝提示应说明原因: " + result);
        assertFalse(mode.isActive(), "备份进行中不应进入手动维护");
    }

    @Test
    void enterManual_refusedWhenAlreadyManual() {
        mode.enter(MaintenanceReason.MANUAL);

        String result = service.enterManual();

        assertNotNull(result);
        assertTrue(result.contains("已处于手动维护"));
        assertTrue(mode.isActive());
    }

    @Test
    void exitManual_clearsManualMode() {
        mode.enter(MaintenanceReason.MANUAL);

        assertNull(service.exitManual());
        assertFalse(mode.isActive());
        assertNull(mode.reason());
    }

    @Test
    void exitManual_refusedWhileBackupRunning() {
        mode.enter(MaintenanceReason.MANUAL);
        when(worldMaintenance.isRunning()).thenReturn(true);

        String result = service.exitManual();

        assertNotNull(result);
        assertTrue(mode.isActive(), "备份进行中不应退出维护模式");
    }

    @Test
    void exitManual_backupResidualLeak_forceExits() {
        // 残留态：备份/优化调度失败遗留（mode active BACKUP 但 running==false）→ /maintenance off 强制退出自愈
        mode.enter(MaintenanceReason.BACKUP);

        assertNull(service.exitManual());
        assertFalse(mode.isActive(), "残留态应允许强制退出，避免登录拦截/MOTD 永久维护中");
    }

    @Test
    void exitManual_whenInactive_returnsNotInMaintenance() {
        String result = service.exitManual();
        assertNotNull(result);
        assertTrue(result.contains("未处于维护模式"));
    }

    @Test
    void status_inactive_describesNotInMaintenance() {
        assertTrue(service.status().contains("未处于维护模式"));
    }

    @Test
    void status_activeWithProgress_showsReasonAndProgress() {
        mode.enter(MaintenanceReason.BACKUP);
        mode.updateProgress("区块", 10, 5);

        String status = service.status();

        assertTrue(status.contains("地图备份中"));
        assertTrue(status.contains("10%"));
        assertTrue(status.contains("5秒"));
    }
}
