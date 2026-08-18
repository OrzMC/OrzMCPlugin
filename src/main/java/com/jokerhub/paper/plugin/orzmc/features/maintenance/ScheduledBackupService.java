package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * 定时自动备份（安全加固 P1-1）。
 *
 * <p>按 {@code maintenance.backup_interval_hours}（默认 0 = 关闭）周期触发
 * {@link WorldMaintenanceService#backup(long, int, java.util.function.Consumer)}，
 * 把文章 26 §5「备份不要依赖手动」落地为自动调度。</p>
 *
 * <p>热重载：注册一个轻量检查器（每分钟一次），每次 tick 惰性读取配置；间隔/开关
 * 字段经 {@code /config reload} 修改后，下一个检查点即按新值重排，无需重启。</p>
 *
 * <p>并发安全：重复 tick 不会叠加 —— {@link WorldMaintenanceService#runExclusive} 内部
 * 用 {@code AtomicBoolean} 互斥，前一次备份尚未结束时再次触发直接跳过；错误通知沿用
 * 现有 PRIVATE 私信路由（由 {@code Notifier} 的 MAINTENANCE_BACKUP_ERROR 处理）。</p>
 */
public final class ScheduledBackupService {

    /** 1 分钟的 tick 数（20 tick/s），检查器周期。 */
    private static final long CHECK_TICKS = 20L * 60L;

    /** 每次检查推进的游戏分钟数。 */
    private static final long CHECK_MINUTES = 1L;

    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final WorldMaintenanceService maintenance;

    /** 当前计划的间隔小时数（0 = 关闭）；与配置不一致时表示发生了热重载。 */
    private long plannedIntervalHours;

    /** 自「当前计划」开始累计的游戏分钟数，达到 {@link #targetMinutes} 触发一次备份。 */
    private long elapsedMinutes;

    /** 当前计划对应的触发分钟数（间隔小时 × 60）。 */
    private long targetMinutes;

    private ScheduledTask task;

    public ScheduledBackupService(
            ServerFacade server, TypedConfigProvider configs, WorldMaintenanceService maintenance) {
        this.server = server;
        this.configs = configs;
        this.maintenance = maintenance;
    }

    /**
     * 注册常驻检查器。无论开关状态都注册（关闭时每分钟空跑，成本可忽略），
     * 这样运行中通过 {@code /config reload} 修改间隔/开关也能即时生效。
     */
    public void setup() {
        cancelTask(); // 重设前取消旧任务
        // 强制首个检查点按当前配置重排（插件重载后重新倒计时）
        plannedIntervalHours = 0;
        elapsedMinutes = 0;
        targetMinutes = 0;
        this.task = server.runTaskTimer(this::tick, CHECK_TICKS, CHECK_TICKS);
    }

    /** 取消定时任务（插件卸载时）。 */
    public void tearDown() {
        cancelTask();
    }

    /**
     * 尽力取消：取消失败不阻塞卸载流程。真实 Paper/Folia 在插件禁用时会自动回收
     * 本插件全部任务，显式取消仅为及时释放；MockBukkit 的 {@code PaperScheduledTask.cancel()}
     * 尚未实现（4.115.0），测试环境也靠此防御避免卸载异常。
     */
    private void cancelTask() {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (RuntimeException e) {
            server.logger().warning("[定时备份] 取消调度任务失败: " + e.getMessage());
        } finally {
            task = null;
        }
    }

    /** 每分钟检查一次：配置变化重排倒计时；到点触发一次备份。 */
    void tick() {
        MaintenanceConfig cfg = configs.maintenance();
        long intervalHours = cfg.backupIntervalHours();
        if (intervalHours != plannedIntervalHours) {
            // 配置变化（含首启 / 关闭 / 调整间隔）：按当前值重排，倒计时从此刻开始
            plannedIntervalHours = intervalHours;
            elapsedMinutes = 0;
            targetMinutes = intervalHours <= 0 ? 0 : Math.multiplyExact(intervalHours, 60L);
        }
        if (plannedIntervalHours <= 0) {
            return; // 关闭：不触发
        }
        elapsedMinutes += CHECK_MINUTES;
        if (elapsedMinutes >= targetMinutes) {
            runBackup(cfg);
            elapsedMinutes = 0;
        }
    }

    /** 触发一次备份；进行中的备份互斥跳过，进度仅落服务器日志（不打扰群聊）。 */
    private void runBackup(MaintenanceConfig cfg) {
        maintenance.backup(
                cfg.optimizeTickTimeThreshold(),
                cfg.backupRetentionCount(),
                msg -> server.logger().info("[定时备份] " + msg));
    }
}
