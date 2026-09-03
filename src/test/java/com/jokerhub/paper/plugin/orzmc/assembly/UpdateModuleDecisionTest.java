package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.update.UpdateService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.UpdateConfig;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient.LatestVersion;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.io.File;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UpdateModule 调度/决策链测试（确定性，无真实定时器）：
 * 用桩 HangarClient 造真实 UpdateService，捕获 runLater/runAsync 回调手工驱动，
 * 验证：启动排程、热重载停链、周期自链、AVAILABLE 反应（提示 vs 自动下载）、UP_TO_DATE 日志。
 */
@ExtendWith(MockitoExtension.class)
class UpdateModuleDecisionTest {

    private static final long INITIAL_DELAY_TICKS = 60L;
    private static final long TICKS_PER_HOUR = 20L * 60L * 60L;
    private static final String CURRENT = "1.0.24-dev.360";
    private static final Instant BUILD_TIME = Instant.parse("2026-08-01T00:00:00Z");

    @TempDir
    File tempDir;

    @Mock
    private PlatformModule platform;

    @Mock
    private ServerFacade server;

    @Mock
    private TypedConfigProvider configs;

    @Mock
    private Logger logger;

    @Mock
    private HangarClient hangar;

    private UpdateConfig cfg;
    private Optional<LatestVersion> remote;
    private List<Runnable> laterTasks;
    private List<Runnable> asyncTasks;
    private UpdateService updateService;
    private UpdateModule module;

    @BeforeEach
    void setUp() {
        cfg = new UpdateConfig(true, "release", 12L, false);
        remote = Optional.empty();
        when(platform.serverFacade()).thenReturn(server);
        when(platform.configs()).thenReturn(configs);
        when(server.logger()).thenReturn(logger);
        // 热重载语义：每次读取都拿当前 cfg（测试中可切换 enabled/autoDownload）
        // lenient：setup_disabled 等用例不读 configs.update()，避免 UnnecessaryStubbing。
        lenient().when(configs.update()).thenAnswer(inv -> cfg);
        // runLater/runAsync 回调捕获，手工驱动（确定性，不经真实调度器）
        laterTasks = new ArrayList<>();
        asyncTasks = new ArrayList<>();
        lenient()
                .doAnswer(inv -> {
                    laterTasks.add(inv.getArgument(0));
                    return null;
                })
                .when(server)
                .runLater(any(Runnable.class), anyLong());
        lenient()
                .doAnswer(inv -> {
                    asyncTasks.add(inv.getArgument(0));
                    return null;
                })
                .when(server)
                .runAsync(any(Runnable.class));
        // Hangar 桩：latest(channel) 返回当前 remote（可切空/新版本）
        lenient().when(hangar.latest(anyString())).thenAnswer(inv -> CompletableFuture.completedFuture(remote));
        updateService = new UpdateService(hangar, () -> cfg, CURRENT, BUILD_TIME, new File(tempDir, "update"), logger);
        module = new UpdateModule(platform, updateService);
    }

    private void fireFirstLater() {
        assertFalse(laterTasks.isEmpty(), "应已排程");
        laterTasks.remove(0).run();
    }

    private void fireFirstAsync() {
        assertFalse(asyncTasks.isEmpty(), "应已派生异步检查");
        asyncTasks.remove(0).run();
    }

    private LatestVersion remote(String version, Instant publishedAt) {
        return new LatestVersion(
                version, publishedAt, "OrzMC-" + version + ".jar", "https://cdn.example.com/x.jar", "stub-sha");
    }

    // ---- 启动排程 ----

    @Test
    void setup_enabled_schedulesInitialCheck60Ticks() {
        module.setup();

        verify(logger).info(contains("自更新已启用"));
        assertEquals(1, laterTasks.size(), "应排一次初始检查");
    }

    @Test
    void setup_disabled_skipsScheduling() {
        cfg = new UpdateConfig(false, "release", 12L, false);

        module.setup();

        verify(logger).info(contains("自更新已禁用"));
        assertTrue(laterTasks.isEmpty(), "禁用时不排程");
    }

    // ---- 自链与热停 ----

    @Test
    void initialCheck_hotReloadDisabled_stopsChain() {
        module.setup();

        cfg = new UpdateConfig(false, "release", 12L, false);
        fireFirstLater();

        verify(logger).info(contains("已被运行时关闭，停止周期检查"));
        assertTrue(laterTasks.isEmpty(), "停链后不再排下一轮");
        assertTrue(asyncTasks.isEmpty(), "禁用后不再派生异步检查");
    }

    @Test
    void intervalPositive_reschedulesAfterEachCheck() {
        module.setup();

        fireFirstLater(); // 首次到点：检查 + 自链排下一轮 → 仍剩 1 个待执行
        fireFirstAsync(); // 执行一次周期检查（remote 空 → UP_TO_DATE）

        assertEquals(1, laterTasks.size(), "间隔>0 每轮到点后应自链排下一轮");
        verify(logger).info(contains("已是最新版本"));
    }

    @Test
    void intervalZero_checksOnceNoReschedule() {
        cfg = new UpdateConfig(true, "release", 0L, false);
        module.setup();

        fireFirstLater();

        assertTrue(laterTasks.isEmpty(), "间隔 0 = 只查一次，不再自链");
    }

    // ---- AVAILABLE 反应 ----

    @Test
    void available_autoDownloadFalse_logsNoticeWithoutDownloading() {
        remote = Optional.of(remote("1.0.24-dev.361", BUILD_TIME.plusSeconds(3600)));
        module.setup();
        fireFirstLater();
        fireFirstAsync();

        verify(logger).info(contains("发现新版本 1.0.24-dev.361"));
        assertNull(updateService.stagedVersion(), "auto_download=false 不下载");
    }

    @Test
    void available_autoDownloadTrue_downloadsAndStages() throws Exception {
        cfg = new UpdateConfig(true, "release", 12L, true);
        byte[] jar = "PK canned jar".getBytes(StandardCharsets.UTF_8);
        String sha = sha256Hex(jar);
        remote = Optional.of(new LatestVersion(
                "1.0.24-dev.361", BUILD_TIME.plusSeconds(3600), "OrzMC-1.0.24-dev.361.jar", "https://cdn/x.jar", sha));
        module.setup();
        fireFirstLater();

        CompletableFuture<HttpResponse<byte[]>> response = CompletableFuture.completedFuture(httpBytes(jar));
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() -> AsyncHttp.getBytes(
                            anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(response);
            fireFirstAsync();
        }

        assertEquals("1.0.24-dev.361", updateService.stagedVersion(), "auto_download=true 应自动下载到 plugins/update");
        assertTrue(new File(new File(tempDir, "update"), "OrzMC-1.0.24-dev.361.jar").isFile());
        verify(logger, never()).warning(contains("自动下载失败"));
    }

    @Test
    void upToDate_noStaged_logsCurrentVersion() {
        remote = Optional.of(remote(CURRENT, BUILD_TIME.plusSeconds(60)));
        module.setup();
        fireFirstLater();
        fireFirstAsync();

        verify(logger).info(contains("已是最新版本 v" + CURRENT));
        assertNull(updateService.stagedVersion(), "远程=本地版本时不下载");
    }

    // ---- helpers ----

    private static HttpResponse<byte[]> httpBytes(byte[] body) {
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> resp = mock(HttpResponse.class);
        when(resp.body()).thenReturn(body);
        return resp;
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
