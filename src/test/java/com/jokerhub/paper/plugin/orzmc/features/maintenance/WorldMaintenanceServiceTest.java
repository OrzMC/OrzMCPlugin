package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokerhub.orzmc.world.ProgressEvent;
import com.jokerhub.orzmc.world.ProgressStage;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService.MaintenanceStage;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
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
        // progressHandler 依赖 templateOptions/renderEvent（真实环境由配置加载，mock 需显式提供）
        when(configs.templateOptions()).thenReturn(defaultTemplateOptions());
        MessageEnvelope envMock = mock(MessageEnvelope.class);
        when(envMock.message()).thenReturn("backup progress");
        when(configs.renderEvent(anyString(), anyMap())).thenReturn(envMock);
        // runExclusive 踢人文案经 renderMotdText 读 templates.yml 场景模板（PR4 迁移），默认模板即可
        when(configs.templates()).thenReturn(defaultTemplates());

        // 临时目录
        worldDir = new File(System.getProperty("java.io.tmpdir"), "wm-world-" + System.nanoTime());
        dataDir = new File(System.getProperty("java.io.tmpdir"), "wm-data-" + System.nanoTime());
        worldDir.mkdirs();
        dataDir.mkdirs();
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(server.plugin()).thenReturn(plugin);
        when(plugin.getDataFolder()).thenReturn(dataDir);
        when(bukkitServer.getWorldContainer()).thenReturn(worldDir);
        // 世界目录（backup 的 input）：worldContainer/world，尊重 level-name
        org.bukkit.World worldMock = mock(org.bukkit.World.class);
        File worldFolder = new File(worldDir, "world");
        worldFolder.mkdirs();
        // 26.1+ 布局下 getWorldFolder()/getWorldPath() 返回维度数据目录（非世界根）——
        // #215 回归正是被它误导；mock 保持该形状以守住回归防护（旧实现会因此把
        // input 选成维度目录，本修复后仍返回世界根）
        File dimensionFolder = new File(worldFolder, "dimensions/minecraft/overworld");
        dimensionFolder.mkdirs();
        when(worldMock.getWorldFolder()).thenReturn(dimensionFolder);
        when(worldMock.getWorldPath()).thenReturn(dimensionFolder.toPath());
        when(bukkitServer.getWorlds()).thenReturn(List.of(worldMock));

        service = new WorldMaintenanceService(
                server, configs, mock(OrzTextStyles.class), mock(Notifier.class), new MaintenanceModeService());
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

        service.runExclusive(
                MaintenanceModeService.MaintenanceReason.BACKUP, () -> asyncRan.set(true), () -> finallyRan.set(true));

        Assertions.assertTrue(asyncRan.get(), "asyncWork 应执行");
        Assertions.assertTrue(finallyRan.get(), "finallyWork 应执行");
        Assertions.assertFalse(service.isRunning(), "结束后 running 应复位");
    }

    @Test
    public void runExclusive_secondCallSkippedWhileRunning() {
        // 专用 mock：runAsync 不执行 → 模拟异步任务进行中（running 保持 true）
        WorldMaintenanceService runningSvc = serviceWithHeldAsync();

        runningSvc.runExclusive(MaintenanceModeService.MaintenanceReason.BACKUP, () -> {}, null);
        Assertions.assertTrue(runningSvc.isRunning(), "异步进行中 isRunning=true");
        int afterFirst = heldRunSyncCount.get();
        Assertions.assertTrue(afterFirst >= 2, "第一次进入：外层 runSync + save-off 至少 2 次，实际 " + afterFirst);

        // 第二次调用应被互斥跳过：runSync 计数不增长
        runningSvc.runExclusive(MaintenanceModeService.MaintenanceReason.BACKUP, () -> {}, null);
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
        return new WorldMaintenanceService(
                held, configs, mock(OrzTextStyles.class), mock(Notifier.class), new MaintenanceModeService());
    }

    @Test
    public void runExclusive_asyncWorkThrows_stillResetsState() {
        AtomicBoolean finallyRan = new AtomicBoolean(false);

        service.runExclusive(
                MaintenanceModeService.MaintenanceReason.BACKUP,
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

        service.runExclusive(
                MaintenanceModeService.MaintenanceReason.BACKUP, () -> observedDuring.set(service.isRunning()), null);

        Assertions.assertTrue(observedDuring.get(), "asyncWork 执行期间 isRunning=true");
        Assertions.assertFalse(service.isRunning());
    }

    @Test
    public void runExclusive_resetsErrorCountersBetweenRuns() throws Exception {
        // bug 场景：chunkErrorCount/fatalErrorReported 跨 run 不复位——上一次 run 残留状态污染本次
        // （致命错误只报一次 + 损坏区块计数累积误报）。复位应在 runExclusive 入口发生。
        java.lang.reflect.Field chunkField = WorldMaintenanceService.class.getDeclaredField("chunkErrorCount");
        chunkField.setAccessible(true);
        java.util.concurrent.atomic.AtomicInteger chunk =
                (java.util.concurrent.atomic.AtomicInteger) chunkField.get(service);
        chunk.set(5);

        java.lang.reflect.Field fatalField = WorldMaintenanceService.class.getDeclaredField("fatalErrorReported");
        fatalField.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean fatal =
                (java.util.concurrent.atomic.AtomicBoolean) fatalField.get(service);
        fatal.set(true);

        service.runExclusive(MaintenanceModeService.MaintenanceReason.BACKUP, () -> {}, null);

        Assertions.assertEquals(0, chunk.get(), "chunkErrorCount 应在每次 run 入口复位");
        Assertions.assertFalse(fatal.get(), "fatalErrorReported 应在每次 run 入口复位");
    }

    // ===== 维护模式状态机驱动（runExclusive → enter/updateProgress/exit） =====

    @Test
    public void runExclusive_drivesMaintenanceModeEnterAndExit() {
        MaintenanceModeService mode = new MaintenanceModeService();
        WorldMaintenanceService svc =
                new WorldMaintenanceService(server, configs, mock(OrzTextStyles.class), mock(Notifier.class), mode);
        AtomicBoolean sawActiveDuring = new AtomicBoolean(false);
        MaintenanceModeService.MaintenanceReason[] reasonDuring = new MaintenanceModeService.MaintenanceReason[1];

        svc.runExclusive(
                MaintenanceModeService.MaintenanceReason.BACKUP,
                () -> {
                    sawActiveDuring.set(mode.isActive());
                    reasonDuring[0] = mode.reason();
                },
                null);

        Assertions.assertTrue(sawActiveDuring.get(), "asyncWork 期间维护模式应激活");
        Assertions.assertEquals(MaintenanceModeService.MaintenanceReason.BACKUP, reasonDuring[0]);
        Assertions.assertFalse(mode.isActive(), "任务结束后应退出维护模式");
    }

    @Test
    public void runExclusive_restoresManualAfterBackup() {
        // 手动维护期间备份照常执行：reason 被 BACKUP 覆盖，结束后恢复 MANUAL（wasManual 还原）
        MaintenanceModeService mode = new MaintenanceModeService();
        WorldMaintenanceService svc =
                new WorldMaintenanceService(server, configs, mock(OrzTextStyles.class), mock(Notifier.class), mode);
        mode.enter(MaintenanceModeService.MaintenanceReason.MANUAL);

        svc.runExclusive(MaintenanceModeService.MaintenanceReason.BACKUP, () -> {}, null);

        Assertions.assertTrue(mode.isActive(), "手动维护期间备份结束后应恢复手动维护");
        Assertions.assertEquals(MaintenanceModeService.MaintenanceReason.MANUAL, mode.reason());
    }

    @Test
    public void progressHandler_updatesMaintenanceModeProgress() throws Exception {
        MaintenanceModeService mode = new MaintenanceModeService();
        WorldMaintenanceService svc =
                new WorldMaintenanceService(server, configs, mock(OrzTextStyles.class), mock(Notifier.class), mode);
        // 反射取私有 progressHandler，用 mock ProgressEvent 驱动进度同步（真实备份链路已由 backup_* 覆盖）
        java.lang.reflect.Method m =
                WorldMaintenanceService.class.getDeclaredMethod("progressHandler", String.class, Consumer.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        kotlin.jvm.functions.Function1<ProgressEvent, kotlin.Unit> handler =
                (kotlin.jvm.functions.Function1<ProgressEvent, kotlin.Unit>)
                        m.invoke(svc, "备份", (Consumer<String>) msg -> {});
        ProgressEvent evt = mock(ProgressEvent.class);
        when(evt.getCurrent()).thenReturn(50L);
        when(evt.getTotal()).thenReturn(100L);
        when(evt.getStage()).thenReturn(ProgressStage.CopyMisc);

        handler.invoke(evt);

        MaintenanceModeService.MaintenanceProgress progress = mode.progress();
        Assertions.assertNotNull(progress, "progressHandler 应同步进度到维护模式状态机");
        Assertions.assertEquals("进行中", progress.stage());
        Assertions.assertEquals(50, progress.percent());
    }

    @Test
    public void backup_createsDirAndReportsProgress() {
        AtomicBoolean sawStartMsg = new AtomicBoolean(false);
        AtomicBoolean sawDirMsg = new AtomicBoolean(false);
        AtomicBoolean inputIsWorldRoot = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) {
                sawStartMsg.set(true);
                // 世界根 = worldContainer/level-name（默认 world/），而非 26.1+ 的维度数据目录
                inputIsWorldRoot.set(
                        msg.contains(new File(worldDir, "world").getAbsolutePath()) && !msg.contains("dimensions"));
            }
            if (msg.contains("地图备份目录")) sawDirMsg.set(true);
        });

        Assertions.assertTrue(sawStartMsg.get(), "应报告服务器地图目录");
        Assertions.assertTrue(sawDirMsg.get(), "应报告地图备份目录");
        Assertions.assertTrue(inputIsWorldRoot.get(), "input 应为 worldContainer/level-name 世界根目录");
        Assertions.assertFalse(service.isRunning());
        // 备份目录应被创建（服务器核心根目录下，非插件数据目录）
        File backupDir = new File(worldDir, "backup");
        Assertions.assertTrue(backupDir.exists(), "备份目录应被创建");
        // 备份 zip 应落盘 backup/（0.3.x zipOutput 模式空世界也产出 22B 最小 zip，backup-core 直接写入 backup/）
        File[] zips = backupDir.listFiles(f -> f.isFile() && f.getName().endsWith(".zip"));
        Assertions.assertTrue(zips != null && zips.length >= 1, "备份 zip 应落盘 backup/ 目录");
    }

    @Test
    public void backup_usesCustomLevelNameFromProperties() throws Exception {
        // server.properties 自定义 level-name → 世界根取 worldContainer/custom_world
        File customWorld = new File(worldDir, "custom_world");
        customWorld.mkdirs();
        File props = new File(worldDir, "server.properties");
        try (FileOutputStream fos = new FileOutputStream(props)) {
            fos.write("level-name=custom_world\n".getBytes());
        }
        AtomicBoolean inputIsCustom = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) {
                inputIsCustom.set(msg.contains(customWorld.getAbsolutePath()));
            }
        });
        Assertions.assertTrue(inputIsCustom.get(), "自定义 level-name 时 input 应为 worldContainer/custom_world");
    }

    @Test
    public void backup_fallsBackToDefaultWorldWhenLevelRootMissing() throws Exception {
        // level-name 目录不存在（如 server.properties 指向未生成的目录）→ 回退 worldContainer/world
        File props = new File(worldDir, "server.properties");
        try (FileOutputStream fos = new FileOutputStream(props)) {
            fos.write("level-name=ghost_world\n".getBytes());
        }
        AtomicBoolean inputIsFallback = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) {
                inputIsFallback.set(msg.contains(new File(worldDir, "world").getAbsolutePath()));
            }
        });
        Assertions.assertTrue(inputIsFallback.get(), "level-name 目录缺失应回退 worldContainer/world");
    }

    @Test
    public void backup_fallsBackOnCorruptProperties() throws Exception {
        // 非法 Unicode 转义序列 → Properties.load 抛 IllegalArgumentException → 回退默认 world
        File props = new File(worldDir, "server.properties");
        String backslash = "\\";
        try (FileOutputStream fos = new FileOutputStream(props)) {
            fos.write(("level-name=" + backslash + "uZZZZ\n").getBytes());
        }
        AtomicBoolean inputIsWorld = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) {
                inputIsWorld.set(msg.contains(new File(worldDir, "world").getAbsolutePath()));
            }
        });
        Assertions.assertTrue(inputIsWorld.get(), "损坏 server.properties 应回退默认 world");
    }

    @Test
    public void backup_blankLevelNameFallsBackToDefault() throws Exception {
        // level-name 空白值 → 回退默认 world
        File props = new File(worldDir, "server.properties");
        try (FileOutputStream fos = new FileOutputStream(props)) {
            fos.write("level-name=   \n".getBytes());
        }
        AtomicBoolean inputIsWorld = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) {
                inputIsWorld.set(msg.contains(new File(worldDir, "world").getAbsolutePath()));
            }
        });
        Assertions.assertTrue(inputIsWorld.get(), "空白 level-name 应回退默认 world");
    }

    @Test
    public void backup_rejectsPathTraversalLevelName() throws Exception {
        // level-name 含路径分隔符 → 视为非法回退默认 world（防越出容器/撞入备份目录）
        File props = new File(worldDir, "server.properties");
        try (FileOutputStream fos = new FileOutputStream(props)) {
            fos.write("level-name=../evil\n".getBytes());
        }
        AtomicBoolean inputIsWorld = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) {
                inputIsWorld.set(msg.contains(new File(worldDir, "world").getAbsolutePath()));
            }
        });
        Assertions.assertTrue(inputIsWorld.get(), "含路径分隔符的 level-name 应拒绝并回退默认 world");
    }

    @Test
    public void backup_reportsFailureWhenNoWorldRootExists() throws Exception {
        // level-name 与默认 world/ 目录都不存在：不应回退到 worldContainer（会撞 0.3.x
        // 重叠校验/把整个服务器目录扫入备份），而是交给 backup-core 明确报备份失败
        try (var stream = Files.walk(new File(worldDir, "world").toPath())) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
        File props = new File(worldDir, "server.properties");
        try (FileOutputStream fos = new FileOutputStream(props)) {
            fos.write("level-name=ghost_world\n".getBytes());
        }
        AtomicBoolean sawFail = new AtomicBoolean(false);
        AtomicBoolean inputIsContainer = new AtomicBoolean(false);
        service.backup(300L, 5, msg -> {
            if (msg.contains("服务器地图目录")) {
                inputIsContainer.set(!msg.trim().endsWith("/world"));
            }
            if (msg.contains("备份失败")) sawFail.set(true);
        });
        Assertions.assertTrue(sawFail.get(), "无世界根时应明确报备份失败");
        Assertions.assertFalse(inputIsContainer.get(), "不应把 worldContainer 本身当作 input");
    }

    @Test
    public void backup_isExclusiveWithMaintenance() {
        // backup 内部走 runExclusive：进行中时第二次 backup 不叠加执行
        WorldMaintenanceService runningSvc = serviceWithHeldAsync();

        runningSvc.runExclusive(MaintenanceModeService.MaintenanceReason.BACKUP, () -> {}, null);
        Assertions.assertTrue(runningSvc.isRunning());
        int afterFirst = heldRunSyncCount.get();

        // 进行中再触发 backup → 被互斥跳过（runSync 计数不增长）
        runningSvc.backup(300L, 5, msg -> {});
        Assertions.assertEquals(afterFirst, heldRunSyncCount.get(), "互斥：backup 被跳过");
    }

    @Test
    public void runExclusive_kickText_matchesBackupSceneTemplate() {
        // PR4 统一渲染入口 review：备份启动踢人应带场景词（templates.yml maintenance_motd_backup），
        // 而非泛化「服务器维护中」——MOTD/登录拦截/踢人三处共用 renderMotdText 后靠默认文案区分场景。
        Player p = mock(Player.class);
        EntityScheduler sched = mock(EntityScheduler.class);
        when(p.getScheduler()).thenReturn(sched);
        doReturn(List.of(p)).when(bukkitServer).getOnlinePlayers();
        // warn 回显入参，避免把 kick 文案桩死成固定值（断言需要真实渲染文本）
        OrzTextStyles echoStyles = mock(OrzTextStyles.class);
        when(echoStyles.warn(anyString())).thenAnswer(inv -> Component.text((String) inv.getArgument(0)));
        WorldMaintenanceService svc = new WorldMaintenanceService(
                server, configs, echoStyles, mock(Notifier.class), new MaintenanceModeService());
        // Folia：踢人消费者投递到 region 线程的 scheduler.run——同步执行以捕获 p.kick 入参
        doAnswer(inv -> {
                    ((Consumer<ScheduledTask>) inv.getArgument(1)).accept(mock(ScheduledTask.class));
                    return null;
                })
                .when(sched)
                .run(any(org.bukkit.plugin.Plugin.class), any(), any(Runnable.class));

        svc.runExclusive(MaintenanceModeService.MaintenanceReason.BACKUP, () -> {}, null);

        String expected = MaintenanceModeService.renderMotdText(
                MaintenanceModeService.MaintenanceReason.BACKUP, defaultTemplates(), null);
        Assertions.assertEquals("服务器地图备份中，请稍后再试", expected, "备份场景默认文案应带场景词");
        verify(p).kick(Component.text(expected));
    }
}
