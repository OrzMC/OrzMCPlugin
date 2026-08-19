package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService.MaintenanceStage;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorldMaintenanceServiceTest extends ServiceTestBase {

    private ServerFacade server;
    private Server bukkitServer;
    private TypedConfigProvider configs;
    private WorldMaintenanceService service;
    private ServerFacade heldServer; // serviceWithHeldAsync 创建的 held mock（互斥测试用）
    private java.util.concurrent.atomic.AtomicInteger heldRunSyncCount; // held.runSync 调用计数
    private File worldDir;
    private File dataDir;

    @BeforeEach
    public void setUpMaintenance() {
        server = mock(ServerFacade.class);
        bukkitServer = mock(Server.class);
        configs = mock(TypedConfigProvider.class);
        // runSync/runAsync 立即执行（模拟主线程/异步直接跑）
        doAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                })
                .when(server)
                .runSync(any(Runnable.class));
        doAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                })
                .when(server)
                .runAsync(any(Runnable.class));

        when(server.server()).thenReturn(bukkitServer);
        when(bukkitServer.getOnlinePlayers()).thenReturn(List.of());
        when(bukkitServer.getConsoleSender()).thenReturn(mock(ConsoleCommandSender.class));
        when(server.logger()).thenReturn(java.util.logging.Logger.getLogger("wm-test"));

        // 临时目录
        worldDir = new File(System.getProperty("java.io.tmpdir"), "wm-world-" + System.nanoTime());
        dataDir = new File(System.getProperty("java.io.tmpdir"), "wm-data-" + System.nanoTime());
        worldDir.mkdirs();
        dataDir.mkdirs();
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(server.plugin()).thenReturn(plugin);
        when(plugin.getDataFolder()).thenReturn(dataDir);
        when(bukkitServer.getWorldContainer()).thenReturn(worldDir);

        service = new WorldMaintenanceService(server, configs, mock(OrzTextStyles.class), mock(Notifier.class));
    }

    // ===== 静态方法（原有） =====

    @Test
    public void testPruneOldZips() throws Exception {
        File tmp = Files.createTempDirectory("wm-prune").toFile();
        tmp.deleteOnExit();
        for (int i = 0; i < 5; i++) {
            File f = new File(tmp, "b" + i + ".zip");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(("x" + i).getBytes());
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        Assertions.assertEquals(5, Objects.requireNonNull(tmp.listFiles((d, n) -> n.endsWith(".zip"))).length);
        WorldMaintenanceService.pruneOldZips(tmp, 2);
        File[] left = tmp.listFiles((d, n) -> n.endsWith(".zip"));
        Assertions.assertTrue(Objects.requireNonNull(left).length <= 2);
    }

    @Test
    public void formatDuration_milliseconds() {
        Assertions.assertEquals("0毫秒", WorldMaintenanceService.formatDuration(0));
        Assertions.assertEquals("854毫秒", WorldMaintenanceService.formatDuration(854));
        Assertions.assertEquals("999毫秒", WorldMaintenanceService.formatDuration(999));
        Assertions.assertEquals("0毫秒", WorldMaintenanceService.formatDuration(-5));
        Assertions.assertEquals("0毫秒", WorldMaintenanceService.formatDuration(-1_000));
    }

    @Test
    public void formatDuration_seconds() {
        Assertions.assertEquals("1秒", WorldMaintenanceService.formatDuration(1000));
        Assertions.assertEquals("2秒", WorldMaintenanceService.formatDuration(1500));
        Assertions.assertEquals("59秒", WorldMaintenanceService.formatDuration(59_000));
        Assertions.assertEquals("1分", WorldMaintenanceService.formatDuration(59_600));
    }

    @Test
    public void formatDuration_minutes() {
        Assertions.assertEquals("1分", WorldMaintenanceService.formatDuration(60_000));
        Assertions.assertEquals("2分35秒", WorldMaintenanceService.formatDuration(154_901));
        Assertions.assertEquals("59分59秒", WorldMaintenanceService.formatDuration(3_599_000));
    }

    @Test
    public void formatDuration_hours() {
        Assertions.assertEquals("1小时", WorldMaintenanceService.formatDuration(3_600_000));
        Assertions.assertEquals("1小时1分1秒", WorldMaintenanceService.formatDuration(3_661_000));
        Assertions.assertEquals("2小时3分", WorldMaintenanceService.formatDuration(7_380_000));
        Assertions.assertEquals("1小时0分5秒", WorldMaintenanceService.formatDuration(3_605_000));
        Assertions.assertEquals("1小时", WorldMaintenanceService.formatDuration(3_599_500));
    }

    @Test
    public void mapProgressStage_mapping() {
        Assertions.assertEquals(MaintenanceStage.Running, WorldMaintenanceService.mapProgressStage(null));
        Assertions.assertEquals(
                MaintenanceStage.Done,
                WorldMaintenanceService.mapProgressStage(com.jokerhub.orzmc.world.ProgressStage.Done));
        // 非 Done 阶段统一归为 Running
        Assertions.assertEquals(
                MaintenanceStage.Running,
                WorldMaintenanceService.mapProgressStage(com.jokerhub.orzmc.world.ProgressStage.CopyMisc));
    }

    // ===== 编排逻辑（新增补测，maintenance 覆盖率 34%→目标 75%+） =====

    @Test
    public void runExclusive_executesWorkAndResetsState() {
        AtomicBoolean asyncRan = new AtomicBoolean(false);
        AtomicBoolean finallyRan = new AtomicBoolean(false);

        service.runExclusive("维护中", () -> asyncRan.set(true), () -> finallyRan.set(true));

        Assertions.assertTrue(asyncRan.get(), "asyncWork 应执行");
        Assertions.assertTrue(finallyRan.get(), "finallyWork 应执行");
        Assertions.assertFalse(service.isRunning(), "结束后 running 应复位");
    }

    @Test
    public void runExclusive_secondCallSkippedWhileRunning() {
        // 专用 mock：runAsync 不执行 → 模拟异步任务进行中（running 保持 true）
        WorldMaintenanceService runningSvc = serviceWithHeldAsync();

        runningSvc.runExclusive("维护中", () -> {}, null);
        Assertions.assertTrue(runningSvc.isRunning(), "异步进行中 isRunning=true");
        int afterFirst = heldRunSyncCount.get();
        Assertions.assertTrue(afterFirst >= 2, "第一次进入：外层 runSync + save-off 至少 2 次，实际 " + afterFirst);

        // 第二次调用应被互斥跳过：runSync 计数不增长
        runningSvc.runExclusive("维护中", () -> {}, null);
        Assertions.assertEquals(afterFirst, heldRunSyncCount.get(), "互斥：第二次不触发 runSync");
    }

    /** 构造 runAsync 不执行（模拟进行中）的 service 实例 */
    private WorldMaintenanceService serviceWithHeldAsync() {
        ServerFacade held = mock(ServerFacade.class);
        heldRunSyncCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> {
                    heldRunSyncCount.incrementAndGet();
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                })
                .when(held)
                .runSync(any(Runnable.class));
        org.mockito.Mockito.doNothing().when(held).runAsync(any(Runnable.class)); // 不执行 = 进行中
        when(held.server()).thenReturn(bukkitServer);
        when(held.logger()).thenReturn(java.util.logging.Logger.getLogger("wm-test"));
        JavaPlugin heldPlugin = server.plugin(); // 先取值，避免 stubbing 中调用 mock
        when(held.plugin()).thenReturn(heldPlugin);
        heldServer = held;
        return new WorldMaintenanceService(held, configs, mock(OrzTextStyles.class), mock(Notifier.class));
    }

    @Test
    public void runExclusive_asyncWorkThrows_stillResetsState() {
        AtomicBoolean finallyRan = new AtomicBoolean(false);

        service.runExclusive(
                "维护中",
                () -> {
                    throw new IllegalStateException("模拟异步任务异常");
                },
                () -> finallyRan.set(true));

        Assertions.assertTrue(finallyRan.get(), "异常后 finallyWork 仍执行");
        Assertions.assertFalse(service.isRunning(), "异常后 running 复位");
    }

    @Test
    public void runExclusive_isRunningFlagDuringExecution() {
        AtomicBoolean observedDuring = new AtomicBoolean(false);

        service.runExclusive("维护中", () -> observedDuring.set(service.isRunning()), null);

        Assertions.assertTrue(observedDuring.get(), "asyncWork 执行期间 isRunning=true");
        Assertions.assertFalse(service.isRunning());
    }

    @Test
    public void backup_createsDirAndReportsProgress() {
        AtomicBoolean sawStartMsg = new AtomicBoolean(false);
        AtomicBoolean sawDirMsg = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) sawStartMsg.set(true);
            if (msg.contains("地图备份目录")) sawDirMsg.set(true);
        });

        Assertions.assertTrue(sawStartMsg.get(), "应报告服务器地图目录");
        Assertions.assertTrue(sawDirMsg.get(), "应报告地图备份目录");
        Assertions.assertFalse(service.isRunning());
        // 备份目录应被创建
        File backupDir = new File(dataDir, "backup");
        Assertions.assertTrue(backupDir.exists(), "备份目录应被创建");
    }

    @Test
    public void backup_isExclusiveWithMaintenance() {
        // backup 内部走 runExclusive：进行中时第二次 backup 不叠加执行
        WorldMaintenanceService runningSvc = serviceWithHeldAsync();

        runningSvc.runExclusive("维护中", () -> {}, null);
        Assertions.assertTrue(runningSvc.isRunning());
        int afterFirst = heldRunSyncCount.get();

        // 进行中再触发 backup → 被互斥跳过（runSync 计数不增长）
        runningSvc.backup(300L, 5, msg -> {});
        Assertions.assertEquals(afterFirst, heldRunSyncCount.get(), "互斥：backup 被跳过");
    }
}
