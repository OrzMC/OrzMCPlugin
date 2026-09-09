package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.features.update.UpdateCommandService;
import com.jokerhub.paper.plugin.orzmc.features.update.UpdateService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.UpdateConfig;
import com.jokerhub.paper.plugin.orzmc.infra.net.HangarClient;
import com.jokerhub.paper.plugin.orzmc.infra.version.BuildInfo;
import java.io.File;
import java.time.Instant;
import java.util.Optional;

/**
 * 插件自更新模块。
 *
 * <p>启动延迟数秒后在异步线程查询 Hangar 指定通道最新版本，并按 {@code update.*} 配置
 * 自动下载到 {@code plugins/update/} 或仅提示。周期检查用 runLater 自链（间隔 0 = 只查一次），
 * 配置热重载关闭 {@code update.enabled} 后下一轮自动停链。</p>
 *
 * <p>线程模型：全部网络/文件 IO 走 {@code ServerFacade.runAsync}（Folia AsyncScheduler），
 * 不触碰任何 region 线程；调度链自身落在 global region 上，每次只做「派生异步任务」的轻活。</p>
 */
public final class UpdateModule implements ServiceModule {

    /** 初始检查延迟：给服务器启动留出余量（3s）。 */
    private static final long INITIAL_DELAY_TICKS = 60L;

    private static final long TICKS_PER_HOUR = 20L * 60L * 60L;

    private final PlatformModule platform;
    private final UpdateService updateService;
    private final UpdateCommandService updateCommandService;

    public UpdateModule(PlatformModule platform) {
        this(platform, buildUpdateService(platform));
    }

    /**
     * 测试接缝（包内可见）：注入现成 UpdateService，绕开读取插件元数据/数据目录等重型装配，
     * 专测调度链与配置反应逻辑。
     */
    UpdateModule(PlatformModule platform, UpdateService updateService) {
        this.platform = platform;
        this.updateService = updateService;
        this.updateCommandService = new UpdateCommandService(
                platform.serverFacade(), updateService, platform.textStyles(), platform.i18nService());
    }

    private static UpdateService buildUpdateService(PlatformModule platform) {
        var plugin = platform.serverFacade().plugin();
        Optional<BuildInfo> build = BuildInfo.load(plugin.getClass().getClassLoader());
        String currentVersion;
        Instant buildTime;
        if (build.isPresent()) {
            currentVersion = build.get().buildVersion();
            buildTime = build.get().buildTime();
        } else {
            // 兜底（非常规运行环境）：用插件元数据版本；构建时间置 EPOCH 使「版本名不同即视为新」
            currentVersion = plugin.getPluginMeta().getVersion();
            buildTime = Instant.EPOCH;
            platform.serverFacade().logger().warning("未找到构建信息（orzmc-build.properties），自更新按插件描述版本比对");
        }
        File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
        return new UpdateService(
                new HangarClient(),
                () -> platform.configs().update(),
                currentVersion,
                buildTime,
                updateFolder,
                platform.serverFacade().logger());
    }

    @Override
    public void setup() {
        if (!platform.configs().update().enabled()) {
            platform.serverFacade().logger().info("自更新已禁用（update.enabled=false），跳过周期检查");
            return;
        }
        platform.serverFacade()
                .logger()
                .info("自更新已启用（通道 " + platform.configs().update().channel() + "，当前 v" + updateService.currentVersion()
                        + "），启动后将异步检查新版本");
        scheduleNext(INITIAL_DELAY_TICKS);
    }

    @Override
    public void tearDown() {
        // 调度链任务绑定插件生命周期，禁用时自动回收；无独立资源需释放
    }

    /** 自链调度：runLater 延迟后执行一次检查 + 派生异步，再排下一次（interval>0 时）。 */
    private void scheduleNext(long delayTicks) {
        platform.serverFacade()
                .runLater(
                        () -> {
                            UpdateConfig cfg = platform.configs().update();
                            if (!cfg.enabled()) {
                                platform.serverFacade().logger().info("自更新已被运行时关闭，停止周期检查");
                                return;
                            }
                            platform.serverFacade().runAsync(this::periodicCheck);
                            if (cfg.checkIntervalHours() > 0) {
                                scheduleNext(cfg.checkIntervalHours() * TICKS_PER_HOUR);
                            }
                        },
                        delayTicks);
    }

    private void periodicCheck() {
        UpdateConfig cfg = platform.configs().update();
        updateService.check().thenAccept(outcome -> {
            switch (outcome.state()) {
                case UP_TO_DATE -> {
                    var latest = outcome.latest();
                    if (latest != null && latest.version().equals(updateService.stagedVersion())) {
                        platform.serverFacade().logger().info("自更新：新版本 " + latest.version() + " 已就绪，重启服务器后生效");
                    } else {
                        platform.serverFacade()
                                .logger()
                                .info("自更新：已是最新版本 v" + updateService.currentVersion() + "（通道 " + cfg.channel() + "）");
                    }
                }
                case AVAILABLE -> {
                    var latest = outcome.latest();
                    if (cfg.autoDownload()) {
                        updateService.downloadNow().thenAccept(dl -> {
                            if (dl.state() == UpdateService.DownloadState.FAILED) {
                                platform.serverFacade().logger().warning("自更新：自动下载失败 - " + dl.detail());
                            }
                        });
                    } else {
                        platform.serverFacade()
                                .logger()
                                .info("自更新：发现新版本 " + latest.version() + "（通道 " + cfg.channel()
                                        + "）。可执行 /update now 下载，或开启 update.auto_download");
                    }
                }
                default -> {
                    // CHECK_FAILED / UNKNOWN_LOCAL：UpdateService 内部已记录日志，此处静默
                }
            }
        });
    }

    public UpdateService updateService() {
        return updateService;
    }

    public UpdateCommandService updateCommandService() {
        return updateCommandService;
    }
}
