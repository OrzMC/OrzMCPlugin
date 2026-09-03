package com.jokerhub.paper.plugin.orzmc.features.update;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.UpdateConfig;
import com.jokerhub.paper.plugin.orzmc.infra.net.AsyncHttp;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient.LatestVersion;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 插件自更新核心（纯后台任务，无 Bukkit 依赖，Folia 天然安全）。
 *
 * <p>职责：查询 Hangar 当前通道最新版本 → 与「当前运行版本 + 构建时间」比对 → 需要更新时
 * 下载 jar 到 {@code plugins/update/}（文件名与平台一致，sha256 校验通过后原子落盘；Paper
 * 重启时按插件元数据 name 匹配应用并删除旧 jar）。全部网络/文件 IO 走异步线程，不触碰服务器
 * region 线程。</p>
 *
 * <p>版本判定规则（精确比对，避免误判）：{@code currentVersion} 为构建期烘焙的发布串
 * （与 Hangar 版本名一致，如 {@code 1.0.24-dev.360}），仅当远程版本名不同且其发布时间晚于
 * 本地构建时间才视为有新版本；同一版本串已下载过（待重启）视为已就绪不重复下载。</p>
 */
public final class UpdateService {

    /** 平台未返回文件名时的兜底落盘名（正常时与 Hangar {@code fileInfo.name} 一致，如 OrzMC-1.0.24.jar）。 */
    static final String FALLBACK_FILE_NAME = "OrzMC.jar";

    private static final Duration DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DOWNLOAD_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HangarClient hangar;
    private final Supplier<UpdateConfig> config;
    private final String currentVersion;
    private final Instant buildTime;
    private final File updateFolder;
    private final Logger logger;
    private final AtomicBoolean downloadInFlight = new AtomicBoolean(false);
    /** 已下载待重启的版本串（内存态，重启后清空）。 */
    private volatile String stagedVersion;
    /** 已下载待重启的 jar 文件（带平台文件名；版本更迭后清理旧名文件时记录用）。 */
    private volatile File stagedFile;

    public UpdateService(
            HangarClient hangar,
            Supplier<UpdateConfig> config,
            String currentVersion,
            Instant buildTime,
            File updateFolder,
            Logger logger) {
        this.hangar = hangar;
        this.config = config;
        this.currentVersion = currentVersion;
        this.buildTime = buildTime;
        this.updateFolder = updateFolder;
        this.logger = logger;
    }

    public enum State {
        /** 本地已是最新。 */
        UP_TO_DATE,
        /** 发现可更新的新版本。 */
        AVAILABLE,
        /** 无法识别本地运行版本（未携带构建信息），不做自动更新。 */
        UNKNOWN_LOCAL,
        /** 检查失败（网络/解析）。 */
        CHECK_FAILED
    }

    public record CheckOutcome(State state, LatestVersion latest) {
        public static CheckOutcome of(State state, LatestVersion latest) {
            return new CheckOutcome(state, latest);
        }
    }

    public enum DownloadState {
        DOWNLOADED,
        ALREADY_DOWNLOADED,
        NO_UPDATE,
        BUSY,
        FAILED
    }

    public record DownloadOutcome(DownloadState state, String detail) {}

    public String currentVersion() {
        return currentVersion;
    }

    public String stagedVersion() {
        return stagedVersion;
    }

    /** 已下载待重启的 jar 文件（可能为 null，测试/命令回显用）。 */
    public File stagedFile() {
        return stagedFile;
    }

    /** 检查当前通道是否有新版本。网络/解析失败收敛为 {@link State#CHECK_FAILED}，不抛异常。 */
    public CompletableFuture<CheckOutcome> check() {
        UpdateConfig cfg = config.get();
        return hangar.latest(cfg.channel()).handle((remote, err) -> {
            if (err != null) {
                logger.warning("自更新检查失败（通道 " + cfg.channel() + "）: " + rootMessage(err));
                return CheckOutcome.of(State.CHECK_FAILED, null);
            }
            return classify(remote.orElse(null));
        });
    }

    private CheckOutcome classify(LatestVersion latest) {
        if (latest == null) {
            return CheckOutcome.of(State.UP_TO_DATE, null);
        }
        if (currentVersion == null || currentVersion.isBlank()) {
            logger.warning("自更新：无法识别当前运行版本（缺少构建信息），跳过更新判定，最新版本 " + latest.version());
            return CheckOutcome.of(State.UNKNOWN_LOCAL, latest);
        }
        if (isNewerThanCurrent(latest)) {
            return CheckOutcome.of(State.AVAILABLE, latest);
        }
        return CheckOutcome.of(State.UP_TO_DATE, latest);
    }

    /** 远程是否比本地新：版本串不同、未下载过、且发布时间晚于本地构建时间。 */
    private boolean isNewerThanCurrent(LatestVersion latest) {
        if (latest.version() == null || latest.version().equals(currentVersion)) {
            return false;
        }
        if (latest.version().equals(stagedVersion)) {
            return false;
        }
        return latest.publishedAt() != null && latest.publishedAt().isAfter(buildTime);
    }

    /**
     * 下载当前通道最新版本到 {@code plugins/update/}。单飞：并发调用只有一个执行，其余返回
     * {@link DownloadState#BUSY}。返回 future 永不带异常（错误收敛为 {@link DownloadState#FAILED}）。
     */
    public CompletableFuture<DownloadOutcome> downloadNow() {
        if (!downloadInFlight.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new DownloadOutcome(DownloadState.BUSY, "已有下载任务进行中，请稍候"));
        }
        return downloadNowUnlocked().whenComplete((outcome, err) -> downloadInFlight.set(false));
    }

    private CompletableFuture<DownloadOutcome> downloadNowUnlocked() {
        return check().thenCompose(this::installIfAvailable).exceptionally(ex -> {
            logger.warning("自更新下载失败: " + rootMessage(ex));
            return new DownloadOutcome(DownloadState.FAILED, rootMessage(ex));
        });
    }

    private CompletableFuture<DownloadOutcome> installIfAvailable(CheckOutcome check) {
        if (check.state() == State.CHECK_FAILED) {
            return CompletableFuture.completedFuture(new DownloadOutcome(DownloadState.FAILED, "检查更新失败，请查看控制台日志"));
        }
        if (check.state() == State.UNKNOWN_LOCAL) {
            return CompletableFuture.completedFuture(new DownloadOutcome(DownloadState.FAILED, "无法识别当前运行版本"));
        }
        LatestVersion latest = check.latest();
        if (latest == null) {
            return CompletableFuture.completedFuture(new DownloadOutcome(DownloadState.NO_UPDATE, "没有可更新的版本（已是最新）"));
        }
        if (latest.version().equals(stagedVersion)) {
            return CompletableFuture.completedFuture(new DownloadOutcome(
                    DownloadState.ALREADY_DOWNLOADED,
                    (stagedFile == null ? stagedFileName(latest) : stagedFile.getName()) + " 已下载（重启后生效）"));
        }
        if (latest.version().equals(currentVersion)) {
            return CompletableFuture.completedFuture(
                    new DownloadOutcome(DownloadState.NO_UPDATE, "没有可更新的版本（已是最新 " + currentVersion + "）"));
        }
        if (latest.downloadUrl() == null || latest.sha256() == null) {
            logger.warning("自更新：远程版本 " + latest.version() + " 缺少下载信息（downloadUrl/sha256）");
            return CompletableFuture.completedFuture(new DownloadOutcome(DownloadState.FAILED, "远程版本缺少下载信息"));
        }
        return fetchAndInstall(latest);
    }

    private CompletableFuture<DownloadOutcome> fetchAndInstall(LatestVersion latest) {
        return AsyncHttp.getBytes(
                        latest.downloadUrl(),
                        Map.of("Accept", "application/octet-stream"),
                        DOWNLOAD_CONNECT_TIMEOUT,
                        DOWNLOAD_REQUEST_TIMEOUT,
                        1)
                .thenApply(HttpResponse::body)
                .thenApply(bytes -> {
                    try {
                        File target = verifyAndInstall(latest, bytes);
                        stagedVersion = latest.version();
                        logger.info("自更新：新版本 " + latest.version() + " 已下载到 " + target + "，重启服务器后生效");
                        return new DownloadOutcome(DownloadState.DOWNLOADED, target.getAbsolutePath());
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    /** 校验 sha256 → 按平台文件名原子落盘 {@code plugins/update/}。 */
    private File verifyAndInstall(LatestVersion latest, byte[] bytes) throws IOException {
        String actual = sha256Hex(bytes);
        if (!actual.equalsIgnoreCase(latest.sha256())) {
            throw new IOException("sha256 校验失败（期望 " + latest.sha256() + "，实际 " + actual + "），已丢弃下载内容");
        }
        if (!updateFolder.exists() && !updateFolder.mkdirs()) {
            throw new IOException("无法创建更新目录: " + updateFolder);
        }
        String fileName = stagedFileName(latest);
        File target = new File(updateFolder, fileName);
        File tmp = new File(updateFolder, fileName + ".part");
        try {
            Files.write(tmp.toPath(), bytes);
            try {
                Files.move(
                        tmp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
        // 平台文件名为版本化名（如 OrzMC-1.0.24.jar）：新版本文件名变化，清理上一暂存文件，
        // 避免 plugins/update 内同插件多版本 jar 并存导致重启应用冲突。仅删本次会话自己下载的文件。
        File previous = stagedFile;
        if (previous != null && !previous.equals(target) && previous.isFile()) {
            try {
                Files.deleteIfExists(previous.toPath());
                logger.info("自更新：清理已过期的暂存文件 " + previous.getName());
            } catch (IOException e) {
                logger.warning("自更新：清理旧暂存文件失败 " + previous.getName() + " - " + e.getMessage());
            }
        }
        stagedFile = target;
        return target;
    }

    /** 落盘文件名：优先 Hangar {@code fileInfo.name}（平台原名，含版本号），缺失/非法时兜底。 */
    private static String stagedFileName(LatestVersion latest) {
        String raw = latest.fileName();
        if (raw != null && !raw.isBlank()) {
            String name = new File(raw).getName();
            if (name.endsWith(".jar")) {
                return name;
            }
        }
        return FALLBACK_FILE_NAME;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
