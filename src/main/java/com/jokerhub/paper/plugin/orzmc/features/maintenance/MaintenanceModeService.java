package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 维护模式独立状态机。
 *
 * <p>与 {@link WorldMaintenanceService} 的生命周期解耦：备份/优化（经
 * {@code WorldMaintenanceService}）驱动它进入 {@code BACKUP}/{@code OPTIMIZE}，
 * 服主可经 {@code /maintenance on} 手动进入 {@code MANUAL}；MOTD / 登录拦截 /
 * 群消息统一读取这里的状态渲染文案。</p>
 *
 * <p>线程安全（Folia 多线程）：{@code active} 用原子布尔，其余字段 volatile；
 * 进度快照 record 不可变，任何线程读到的是完整一致的快照。</p>
 */
public final class MaintenanceModeService {

    /** 维护场景：地图备份 / 地图优化 / 服主手动进入。 */
    public enum MaintenanceReason {
        BACKUP,
        OPTIMIZE,
        MANUAL
    }

    /** 进度快照（不可变 record）：阶段中文名 + 百分比 + 预计剩余秒数 + 完整进度行文案。 */
    public record MaintenanceProgress(String stage, int percent, long etaSeconds, String progressMessage) {
        static MaintenanceProgress of(String stage, int percent, long etaSeconds) {
            long eta = Math.max(0, etaSeconds);
            String message = "进度：" + stage + " " + percent + "% 预计剩余 " + eta + "秒";
            return new MaintenanceProgress(stage, percent, eta, message);
        }
    }

    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile MaintenanceReason reason;
    private volatile long startedAtMillis = 0L;
    private volatile MaintenanceProgress progress;

    /** 进入维护模式（记录开始时间，清空上一次进度）。 */
    public void enter(MaintenanceReason reason) {
        this.reason = reason;
        this.startedAtMillis = System.currentTimeMillis();
        this.progress = null;
        active.set(true);
    }

    /** 更新进度快照（stage 为中文阶段名，percent 0-100，etaSeconds 预计剩余秒数）。 */
    public void updateProgress(String stage, int percent, long etaSeconds) {
        progress = MaintenanceProgress.of(stage, percent, etaSeconds);
    }

    /** 退出维护模式（清空 reason 与进度）。 */
    public void exit() {
        active.set(false);
        reason = null;
        progress = null;
    }

    public boolean isActive() {
        return active.get();
    }

    /** 当前维护原因；未激活时为 null（调用方应先判 isActive）。 */
    public MaintenanceReason reason() {
        return reason;
    }

    public long startedAt() {
        return startedAtMillis;
    }

    public MaintenanceProgress progress() {
        return progress;
    }

    /** 原子状态快照：active/reason/progress 同锁一次读取，避免多次读拼接不一致。 */
    public synchronized MaintenanceStatus status() {
        return new MaintenanceStatus(active.get(), reason, progress);
    }

    public record MaintenanceStatus(boolean active, MaintenanceReason reason, MaintenanceProgress progress) {}

    /**
     * 将文案模板中的 {@code {stage}}/{@code {percent}}/{@code {eta}} 占位符替换为进度值；
     * 无进度快照（progress==null）时把三个进度占位符全部替换为空串——消除 manual/无进度场景
     * 自定义模板显示字面量 "{percent}" 的问题（有进度时行为不变）。
     */
    public static String renderTemplate(String template, MaintenanceProgress progress) {
        if (template == null) {
            return "";
        }
        if (progress == null) {
            return template.replace("{stage}", "").replace("{percent}", "").replace("{eta}", "");
        }
        String rendered = template;
        if (progress.stage() != null) {
            rendered = rendered.replace("{stage}", progress.stage());
        }
        rendered = rendered.replace("{percent}", String.valueOf(progress.percent()));
        rendered = rendered.replace("{eta}", String.valueOf(progress.etaSeconds()));
        return rendered;
    }

    /**
     * 按维护场景渲染统一提示文案（MOTD / 登录拦截 / 踢人三处共用，2026-09-02 迁移）。
     *
     * <p>场景模板由 {@code templates.yml} 的 {@code maintenance_motd_*} 键驱动，进度行由
     * {@code maintenance_motd_progress_line} 模板驱动。场景模板默认纯文案（不含进度占位符）；
     * 当场景非 MANUAL、有进度、且场景模板未声明任何进度占位符时，追加换行 + 渲染后的进度行。
     * 若服主自定义场景模板自带 {@code {stage}/{percent}/{eta}}，则不追加（防两行进度重复）。</p>
     *
     * @param reason    维护场景；null 视为未知（返回固定兜底文案，不渲染进度行）
     * @param templates 模板配置（非 null；reason 非 null 时使用）
     * @param progress  进度快照；可能为 null（manual/刚进入无进度）
     */
    public static String renderMotdText(MaintenanceReason reason, Templates templates, MaintenanceProgress progress) {
        if (reason == null) {
            return "服务器维护中，请稍后再尝试登录。";
        }
        String scene =
                switch (reason) {
                    case BACKUP -> templates.maintenanceMotdBackup();
                    case OPTIMIZE -> templates.maintenanceMotdOptimize();
                    case MANUAL -> templates.maintenanceMotdManual();
                };
        String rendered = renderTemplate(scene, progress);
        if (progress != null && reason != MaintenanceReason.MANUAL && !hasProgressPlaceholders(scene)) {
            rendered = rendered + "\n" + renderTemplate(templates.maintenanceMotdProgressLine(), progress);
        }
        return rendered;
    }

    /** 场景模板是否声明了进度占位符：含任一则占位符已渲染进场景文案，无需再追加独立进度行。 */
    private static boolean hasProgressPlaceholders(String template) {
        return template != null
                && (template.contains("{stage}") || template.contains("{percent}") || template.contains("{eta}"));
    }
}
