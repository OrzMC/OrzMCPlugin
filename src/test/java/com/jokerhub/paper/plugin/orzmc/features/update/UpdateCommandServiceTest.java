package com.jokerhub.paper.plugin.orzmc.features.update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.features.update.UpdateService.CheckOutcome;
import com.jokerhub.paper.plugin.orzmc.features.update.UpdateService.DownloadOutcome;
import com.jokerhub.paper.plugin.orzmc.features.update.UpdateService.DownloadState;
import com.jokerhub.paper.plugin.orzmc.features.update.UpdateService.State;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient.LatestVersion;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code /update check|now} 命令状态→文案映射测试：mock UpdateService（completed future，
 * 回调同步驱动）与 mock styles（文案经 styles.x(text) 转发，校验映射正确），console 路径
 * 由 runSync doAnswer 内联执行完成回投。不发真实命令/不触碰调度器与网络。
 */
@ExtendWith(MockitoExtension.class)
class UpdateCommandServiceTest {

    private static final LatestVersion LATEST = new LatestVersion(
            "1.0.24-dev.361",
            Instant.parse("2026-08-20T10:00:00Z"),
            "OrzMC-1.0.24-dev.361.jar",
            "https://cdn.example.com/x.jar",
            "abc");

    @Mock
    private ServerFacade server;

    @Mock
    private OrzTextStyles styles;

    @Mock
    private UpdateService updates;

    @Mock
    private CommandSender sender;

    private UpdateCommandService service;

    @BeforeEach
    void setUp() {
        service = new UpdateCommandService(server, updates, styles);
        // console 回投走 runSync：内联执行，让 sender.sendMessage 在测试线程立即发生
        lenient()
                .doAnswer(inv -> {
                    ((Runnable) inv.getArgument(0)).run();
                    return null;
                })
                .when(server)
                .runSync(any(Runnable.class));
    }

    /** UNKNOWN_LOCAL 分支要回显 Hangar 最新版本，须带上 latest（其余失败态 latest 为 null）。 */
    private static CheckOutcome check(State state) {
        return CheckOutcome.of(
                state,
                state == State.UP_TO_DATE || state == State.AVAILABLE || state == State.UNKNOWN_LOCAL ? LATEST : null);
    }

    // ---- /update check ----

    @Test
    void check_available_mapsToSuccessHint() {
        when(updates.check()).thenReturn(CompletableFuture.completedFuture(check(State.AVAILABLE)));

        service.check(sender);

        verify(styles).success("发现新版本 1.0.24-dev.361。执行 /update now 下载，重启服务器后生效");
    }

    @Test
    void check_upToDateWithLatest_mapsToInfoWithVersions() {
        when(updates.check()).thenReturn(CompletableFuture.completedFuture(check(State.UP_TO_DATE)));
        when(updates.currentVersion()).thenReturn("1.0.24-dev.360");

        service.check(sender);

        verify(styles).info("已是最新版本 1.0.24-dev.361（本地 1.0.24-dev.360）");
    }

    @Test
    void check_upToDateNoRemote_mapsToInfoNoVersionInfo() {
        when(updates.check()).thenReturn(CompletableFuture.completedFuture(CheckOutcome.of(State.UP_TO_DATE, null)));

        service.check(sender);

        verify(styles).info("当前通道无可用版本信息");
    }

    @Test
    void check_checkFailed_mapsToError() {
        when(updates.check()).thenReturn(CompletableFuture.completedFuture(check(State.CHECK_FAILED)));

        service.check(sender);

        verify(styles).error("检查更新失败，请查看控制台日志");
    }

    @Test
    void check_unknownLocal_mapsToErrorWithRemoteVersion() {
        when(updates.check()).thenReturn(CompletableFuture.completedFuture(check(State.UNKNOWN_LOCAL)));

        service.check(sender);

        verify(styles).error("无法识别当前运行版本（构建信息缺失）；Hangar 最新版本 1.0.24-dev.361");
    }

    @Test
    void check_asyncError_mapsToError() {
        when(updates.check()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        service.check(sender);

        verify(styles).error("检查更新失败：boom");
    }

    // ---- /update now ----

    @Test
    void download_downloaded_mapsToSuccessWithTarget() {
        when(updates.downloadNow())
                .thenReturn(CompletableFuture.completedFuture(new DownloadOutcome(
                        DownloadState.DOWNLOADED, "/server/plugins/update/OrzMC-1.0.24-dev.361.jar")));

        service.downloadNow(sender);

        verify(styles).success("新版本已下载到 /server/plugins/update/OrzMC-1.0.24-dev.361.jar，重启服务器后生效");
    }

    @Test
    void download_alreadyDownloaded_mapsToInfo() {
        when(updates.downloadNow())
                .thenReturn(CompletableFuture.completedFuture(
                        new DownloadOutcome(DownloadState.ALREADY_DOWNLOADED, "OrzMC-1.0.24-dev.361.jar 已下载（重启后生效）")));

        service.downloadNow(sender);

        verify(styles).info("OrzMC-1.0.24-dev.361.jar 已下载（重启后生效）");
    }

    @Test
    void download_noUpdate_mapsToInfo() {
        when(updates.downloadNow())
                .thenReturn(CompletableFuture.completedFuture(
                        new DownloadOutcome(DownloadState.NO_UPDATE, "没有可更新的版本（已是最新）")));

        service.downloadNow(sender);

        verify(styles).info("没有可更新的版本（已是最新）");
    }

    @Test
    void download_busy_mapsToWarn() {
        when(updates.downloadNow())
                .thenReturn(
                        CompletableFuture.completedFuture(new DownloadOutcome(DownloadState.BUSY, "已有下载任务进行中，请稍候")));

        service.downloadNow(sender);

        verify(styles).warn("已有下载任务进行中，请稍候");
    }

    @Test
    void download_failed_mapsToError() {
        when(updates.downloadNow())
                .thenReturn(
                        CompletableFuture.completedFuture(new DownloadOutcome(DownloadState.FAILED, "sha256 校验失败")));

        service.downloadNow(sender);

        verify(styles).error("下载失败：sha256 校验失败");
    }

    @Test
    void download_asyncError_mapsToError() {
        when(updates.downloadNow()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("conn reset")));

        service.downloadNow(sender);

        verify(styles).error("下载失败：conn reset");
    }

    @Test
    void consoleReply_actuallySendsComponent() {
        when(updates.check()).thenReturn(CompletableFuture.completedFuture(check(State.AVAILABLE)));
        net.kyori.adventure.text.TextComponent expected = net.kyori.adventure.text.Component.text("x");
        when(styles.success(anyString())).thenReturn(expected);

        service.check(sender);

        verify(sender).sendMessage(expected);
    }

    @Test
    void check_nullOutcome_mapsToError() {
        when(updates.check()).thenReturn(CompletableFuture.completedFuture(null));

        service.check(sender);

        verify(styles).error("检查更新失败，请查看控制台日志");
    }
}
