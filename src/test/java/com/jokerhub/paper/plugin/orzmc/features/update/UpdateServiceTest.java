package com.jokerhub.paper.plugin.orzmc.features.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.UpdateConfig;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient.LatestVersion;
import java.io.File;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/**
 * {@link UpdateService} 测试：版本判定（新/旧/同/已暂存/未知本地）与下载闭环
 * （sha256 校验 → 按平台文件名原子落盘 plugins/update → 二次调用幂等 → 新版本文件名变化时
 * 清理旧暂存）。全部 mock 网络层，无真实外呼。
 */
class UpdateServiceTest {

    private static final String CURRENT = "1.0.24-dev.360";
    private static final Instant BUILD_TIME = Instant.parse("2026-08-01T00:00:00Z");

    @TempDir
    File tempDir;

    private HangarClient hangar;
    private UpdateConfig cfg;
    private UpdateService service;

    @BeforeEach
    void setUp() {
        hangar = mock(HangarClient.class);
        cfg = new UpdateConfig(true, "release", 12L, false);
        service = serviceFor(CURRENT, BUILD_TIME);
    }

    private UpdateService serviceFor(String currentVersion, Instant buildTime) {
        Supplier<UpdateConfig> config = () -> cfg;
        return new UpdateService(
                hangar, config, currentVersion, buildTime, new File(tempDir, "update"), mock(Logger.class));
    }

    private File updateDir() {
        return new File(tempDir, "update");
    }

    private void stubLatest(LatestVersion latest) {
        when(hangar.latest("release")).thenReturn(CompletableFuture.completedFuture(Optional.ofNullable(latest)));
    }

    /** 模拟平台发布：文件名 = OrzMC-{version}.jar（与 CI 产物命名一致）。 */
    private LatestVersion remote(String version, Instant publishedAt) {
        return new LatestVersion(
                version, publishedAt, "OrzMC-" + version + ".jar", "https://cdn.example.com/OrzMC.jar", "stub-sha");
    }

    /** 在 AsyncHttp.getBytes 桩生效期间执行 action（MockedStatic 作用域须覆盖真实调用点）。 */
    private <T> T withStubbedDownload(byte[] jar, Callable<T> action) throws Exception {
        // 先在 stubbing 链外构造 response（避免嵌套 when() 触发 Mockito UnfinishedStubbing）
        CompletableFuture<HttpResponse<byte[]>> response = CompletableFuture.completedFuture(httpBytes(jar));
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() -> AsyncHttp.getBytes(
                            anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(response);
            return action.call();
        }
    }

    // ---- check()：版本判定 ----

    @Test
    void check_noRemote_upToDateWithNullLatest() {
        stubLatest(null);

        UpdateService.CheckOutcome outcome = service.check().join();

        assertEquals(UpdateService.State.UP_TO_DATE, outcome.state());
        assertNull(outcome.latest());
    }

    @Test
    void check_sameVersion_upToDate() {
        stubLatest(remote(CURRENT, BUILD_TIME.plusSeconds(60)));

        UpdateService.CheckOutcome outcome = service.check().join();

        assertEquals(UpdateService.State.UP_TO_DATE, outcome.state(), "版本串相同即使发布时间更晚也不算新");
    }

    @Test
    void check_newerVersion_available() {
        stubLatest(remote("1.0.24-dev.361", BUILD_TIME.plusSeconds(3600)));

        UpdateService.CheckOutcome outcome = service.check().join();

        assertEquals(UpdateService.State.AVAILABLE, outcome.state());
        assertEquals("1.0.24-dev.361", outcome.latest().version());
    }

    @Test
    void check_remotePublishedBeforeLocalBuildTime_notDowngraded() {
        // 本地构建时间晚于 Hangar 上任何发布（本地新构建尚未发版）→ 不视为可更新
        stubLatest(remote("1.0.23", BUILD_TIME.minusSeconds(86400)));

        UpdateService.CheckOutcome outcome = service.check().join();

        assertEquals(UpdateService.State.UP_TO_DATE, outcome.state());
    }

    @Test
    void check_blankCurrentVersion_unknownLocal() {
        UpdateService blankService = serviceFor("", BUILD_TIME);
        stubLatest(remote("1.0.24", BUILD_TIME.plusSeconds(60)));

        UpdateService.CheckOutcome outcome = blankService.check().join();

        assertEquals(UpdateService.State.UNKNOWN_LOCAL, outcome.state(), "无法识别本地运行版本时不做自动更新判定");
    }

    @Test
    void check_hangarFailure_checkFailed() {
        CompletableFuture<Optional<LatestVersion>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new CompletionException(new RuntimeException("connection refused")));
        when(hangar.latest("release")).thenReturn(failed);

        UpdateService.CheckOutcome outcome = service.check().join();

        assertEquals(UpdateService.State.CHECK_FAILED, outcome.state());
    }

    // ---- downloadNow()：下载闭环 ----

    @Test
    void downloadNow_sameAsCurrent_noUpdate() {
        stubLatest(remote(CURRENT, BUILD_TIME.plusSeconds(60)));

        UpdateService.DownloadOutcome outcome = service.downloadNow().join();

        assertEquals(UpdateService.DownloadState.NO_UPDATE, outcome.state());
    }

    @Test
    void downloadNow_newer_downloadAndStage_writesPlatformFileName() throws Exception {
        byte[] jar = fakeJarBytes();
        String sha = sha256Hex(jar);
        LatestVersion newer = new LatestVersion(
                "1.0.24-dev.361", BUILD_TIME.plusSeconds(3600), "OrzMC-1.0.24-dev.361.jar", "https://cdn/x.jar", sha);
        stubLatest(newer);

        UpdateService.DownloadOutcome outcome =
                withStubbedDownload(jar, () -> service.downloadNow().join());

        assertEquals(UpdateService.DownloadState.DOWNLOADED, outcome.state());
        File staged = new File(updateDir(), "OrzMC-1.0.24-dev.361.jar");
        assertTrue(staged.isFile(), "落盘文件名应保持与平台 fileInfo.name 一致");
        assertArrayEquals(jar, Files.readAllBytes(staged.toPath()));
        assertEquals("1.0.24-dev.361", service.stagedVersion());
        assertEquals(staged, service.stagedFile());
    }

    @Test
    void downloadNow_remoteFileNameMissing_fallsBackToDefaultName() throws Exception {
        byte[] jar = fakeJarBytes();
        String sha = sha256Hex(jar);
        LatestVersion newer =
                new LatestVersion("1.0.24-dev.361", BUILD_TIME.plusSeconds(3600), null, "https://cdn/x.jar", sha);
        stubLatest(newer);

        UpdateService.DownloadOutcome outcome =
                withStubbedDownload(jar, () -> service.downloadNow().join());

        assertEquals(UpdateService.DownloadState.DOWNLOADED, outcome.state());
        File staged = new File(updateDir(), "OrzMC.jar");
        assertTrue(staged.isFile(), "平台未返回文件名时应回退默认名 OrzMC.jar");
    }

    @Test
    void downloadNow_afterStaging_secondCallAlreadyDownloaded() throws Exception {
        byte[] jar = fakeJarBytes();
        String sha = sha256Hex(jar);
        LatestVersion newer = new LatestVersion(
                "1.0.24-dev.361", BUILD_TIME.plusSeconds(3600), "OrzMC-1.0.24-dev.361.jar", "https://cdn/x.jar", sha);
        stubLatest(newer);

        UpdateService.DownloadOutcome first =
                withStubbedDownload(jar, () -> service.downloadNow().join());
        assertEquals(UpdateService.DownloadState.DOWNLOADED, first.state());

        // 第二次：命中 stagedVersion → 短路，不再触碰下载网络
        UpdateService.DownloadOutcome second = service.downloadNow().join();

        assertEquals(
                UpdateService.DownloadState.ALREADY_DOWNLOADED, second.state(), "已暂存待重启的版本应短路为 ALREADY_DOWNLOADED");
    }

    @Test
    void downloadNow_newVersionDifferentFileName_removesPreviouslyStagedFile() throws Exception {
        byte[] jarV1 = fakeJarBytes();
        byte[] jarV2 = fakeJarBytes("PK fake jar v2 content");
        String shaV1 = sha256Hex(jarV1);
        String shaV2 = sha256Hex(jarV2);
        LatestVersion v1 = new LatestVersion(
                "1.0.24-dev.361",
                BUILD_TIME.plusSeconds(3600),
                "OrzMC-1.0.24-dev.361.jar",
                "https://cdn/x-361.jar",
                shaV1);
        LatestVersion v2 = new LatestVersion(
                "1.0.24-dev.362",
                BUILD_TIME.plusSeconds(7200),
                "OrzMC-1.0.24-dev.362.jar",
                "https://cdn/x-362.jar",
                shaV2);
        stubLatest(v1);
        assertEquals(
                UpdateService.DownloadState.DOWNLOADED,
                withStubbedDownload(jarV1, () -> service.downloadNow().join()).state());
        assertTrue(new File(updateDir(), "OrzMC-1.0.24-dev.361.jar").isFile());

        // 新版平台文件名变化（版本化名）：落盘新名并清理旧暂存，避免同插件多 jar 并存
        stubLatest(v2);
        assertEquals(
                UpdateService.DownloadState.DOWNLOADED,
                withStubbedDownload(jarV2, () -> service.downloadNow().join()).state());

        assertFalse(new File(updateDir(), "OrzMC-1.0.24-dev.361.jar").exists(), "旧版本暂存文件应被清理");
        assertTrue(new File(updateDir(), "OrzMC-1.0.24-dev.362.jar").isFile(), "新版本按平台文件名落盘");
        assertEquals(1, updateDir().listFiles().length, "plugins/update 内同一插件只保留最新一个暂存");
        assertEquals("1.0.24-dev.362", service.stagedVersion());
    }

    @Test
    void downloadNow_shaMismatch_failedAndNoFileLeft() throws Exception {
        byte[] jar = fakeJarBytes();
        LatestVersion newer = new LatestVersion(
                "1.0.24-dev.361",
                BUILD_TIME.plusSeconds(3600),
                "OrzMC-1.0.24-dev.361.jar",
                "https://cdn/x.jar",
                "wrong-sha");
        stubLatest(newer);

        UpdateService.DownloadOutcome outcome =
                withStubbedDownload(jar, () -> service.downloadNow().join());

        assertEquals(UpdateService.DownloadState.FAILED, outcome.state());
        assertTrue(outcome.detail().contains("sha256 校验失败"), "失败原因应指向 sha256 校验，实际: " + outcome.detail());
        assertNull(service.stagedVersion());
        assertNull(service.stagedFile());
        if (updateDir().exists()) {
            assertEquals(0, updateDir().listFiles().length, "校验失败后不得留下任何文件（含 .part 临时文件）");
        }
    }

    @Test
    void downloadNow_shaMismatch_keepsPreviouslyStagedFile() throws Exception {
        byte[] goodJar = fakeJarBytes();
        String goodSha = sha256Hex(goodJar);
        LatestVersion v1 = new LatestVersion(
                "1.0.24-dev.361",
                BUILD_TIME.plusSeconds(3600),
                "OrzMC-1.0.24-dev.361.jar",
                "https://cdn/x-361.jar",
                goodSha);
        stubLatest(v1);
        assertEquals(
                UpdateService.DownloadState.DOWNLOADED,
                withStubbedDownload(goodJar, () -> service.downloadNow().join()).state());

        // 更新的版本 sha 校验失败：不得误删此前已就绪的暂存
        LatestVersion bad = new LatestVersion(
                "1.0.24-dev.362",
                BUILD_TIME.plusSeconds(7200),
                "OrzMC-1.0.24-dev.362.jar",
                "https://cdn/x-362.jar",
                "wrong-sha");
        stubLatest(bad);
        UpdateService.DownloadOutcome outcome =
                withStubbedDownload(goodJar, () -> service.downloadNow().join());

        assertEquals(UpdateService.DownloadState.FAILED, outcome.state());
        assertTrue(new File(updateDir(), "OrzMC-1.0.24-dev.361.jar").isFile(), "sha 校验失败不得清理已就绪的旧暂存");
        assertEquals("1.0.24-dev.361", service.stagedVersion());
    }

    @Test
    void downloadNow_checkFailed_failed() {
        CompletableFuture<Optional<LatestVersion>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));
        when(hangar.latest("release")).thenReturn(failed);

        UpdateService.DownloadOutcome outcome = service.downloadNow().join();

        assertEquals(UpdateService.DownloadState.FAILED, outcome.state());
    }

    @Test
    void downloadNow_unknownLocal_failed() {
        UpdateService blankService = serviceFor("", BUILD_TIME);
        stubLatest(remote("1.0.24", BUILD_TIME.plusSeconds(60)));

        UpdateService.DownloadOutcome outcome = blankService.downloadNow().join();

        assertEquals(UpdateService.DownloadState.FAILED, outcome.state());
    }

    @Test
    void downloadNow_concurrentSecondCall_busy() throws Exception {
        byte[] jar = fakeJarBytes();
        String sha = sha256Hex(jar);
        LatestVersion newer = new LatestVersion(
                "1.0.24-dev.361", BUILD_TIME.plusSeconds(3600), "OrzMC-1.0.24-dev.361.jar", "https://cdn/x.jar", sha);
        stubLatest(newer);
        // 第一个下载的 HTTP future 挂起，制造"下载中"窗口
        CompletableFuture<HttpResponse<byte[]>> pendingBytes = new CompletableFuture<>();
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            asyncHttp
                    .when(() -> AsyncHttp.getBytes(
                            anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(pendingBytes);

            CompletableFuture<UpdateService.DownloadOutcome> first = service.downloadNow();
            // 第一单仍 in-flight：并发第二单应立刻 BUSY，不触碰网络
            UpdateService.DownloadOutcome busy = service.downloadNow().join();
            assertEquals(UpdateService.DownloadState.BUSY, busy.state());

            // 完成第一单 → 正常下载成功
            pendingBytes.complete(httpBytes(jar));
            assertEquals(UpdateService.DownloadState.DOWNLOADED, first.join().state());
        }
        File staged = new File(updateDir(), "OrzMC-1.0.24-dev.361.jar");
        assertTrue(staged.isFile(), "第一单完成后应落盘");
    }

    // ---- helpers ----

    private static byte[] fakeJarBytes() {
        return fakeJarBytes("PK fake jar content for sha256 test");
    }

    private static byte[] fakeJarBytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

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
