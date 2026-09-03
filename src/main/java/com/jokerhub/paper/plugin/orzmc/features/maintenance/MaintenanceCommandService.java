package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceProgress;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.MaintenanceModeService.MaintenanceReason;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import org.bukkit.entity.Player;

/**
 * {@code /maintenance on|off|status} 命令的服务逻辑（与 Brigadier 注册解耦，便于测试）。
 *
 * <p>与备份/优化的互斥约定：备份/优化进行中拒绝手动进入（避免双重踢人/保存中断）；
 * 手动维护期间备份照常执行，reason 被备份/优化覆盖，任务结束后 {@link WorldMaintenanceService}
 * 恢复手动维护。手动维护踢人投递到玩家所在 region 线程（Folia 实体线程约束）。</p>
 *
 * <p>并发约定：与 {@link WorldMaintenanceService} 共享同一把锁——维护模式状态机实例
 * {@code synchronized(mode)}。校验 + enter/exit（含锁内二次 isRunning 校验）整体持锁，
 * 与 runExclusive 的「CAS running + 读 wasManual + enter(reason)」同锁互斥，杜绝
 * check-then-act 竞态（reason 被覆盖 / 手动维护被静默清除）。</p>
 */
public final class MaintenanceCommandService {

    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final MaintenanceModeService mode;
    private final WorldMaintenanceService worldMaintenance;

    public MaintenanceCommandService(
            ServerFacade server,
            TypedConfigProvider configs,
            OrzTextStyles styles,
            MaintenanceModeService mode,
            WorldMaintenanceService worldMaintenance) {
        this.server = server;
        this.configs = configs;
        this.styles = styles;
        this.mode = mode;
        this.worldMaintenance = worldMaintenance;
    }

    /** 进入手动维护。返回 null 表示成功，否则为拒绝提示。 */
    public String enterManual() {
        // 校验 + enter 收敛到维护模式状态机实例锁内原子执行：与
        // WorldMaintenanceService.runExclusive 的 CAS + 读 wasManual + enter(reason) 同一把锁，
        // 消除「先查 isRunning 再 enter」的 check-then-act 竞态（reason 被覆盖 / 手动维护被静默清除）。
        synchronized (mode) {
            if (worldMaintenance.isRunning()) {
                return "当前正在执行地图备份/优化，无法手动进入维护模式";
            }
            if (mode.isActive() && mode.reason() == MaintenanceReason.MANUAL) {
                return "服务器已处于手动维护模式";
            }
            mode.enter(MaintenanceReason.MANUAL);
        }
        server.runSync(() -> {
            // 手动维护踢人：统一场景文案（templates.yml maintenance_motd_manual，PR4 迁移）
            String kickText =
                    MaintenanceModeService.renderMotdText(MaintenanceReason.MANUAL, configs.templates(), null);
            for (Player p : server.server().getOnlinePlayers()) {
                p.getScheduler().run(server.plugin(), t -> p.kick(styles.warn(kickText)), () -> {});
            }
        });
        return null;
    }

    /** 退出手动维护。返回 null 表示成功，否则为拒绝提示。 */
    public String exitManual() {
        synchronized (mode) {
            if (!mode.isActive()) {
                return "服务器当前未处于维护模式";
            }
            if (worldMaintenance.isRunning()) {
                return "当前正在执行地图备份/优化，无法退出维护模式";
            }
            // running==false：正常手动维护直接退出；残留态（runSync 调度失败等遗留的
            // BACKUP/OPTIMIZE 激活但 running 已复位）也允许强制退出自愈——没有备份/优化在跑，
            // 不存在「任务结束后自动恢复」，留着只会让登录拦截/MOTD 永久维护中。
            mode.exit();
            return null;
        }
    }

    /** 当前维护状态描述（用于 /maintenance status）。 */
    public String status() {
        var snap = mode.status();
        if (!snap.active()) {
            return "服务器未处于维护模式";
        }
        String reasonCn =
                switch (snap.reason() == null ? MaintenanceReason.MANUAL : snap.reason()) {
                    case BACKUP -> "地图备份中";
                    case OPTIMIZE -> "地图优化中";
                    case MANUAL -> "手动维护中";
                };
        MaintenanceProgress progress = snap.progress();
        if (progress != null) {
            return reasonCn + " " + progress.progressMessage();
        }
        return reasonCn;
    }
}
