package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import static java.nio.file.Files.readAttributes;

import com.jokerhub.orzmc.world.*;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.OrzUtil;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateResolvers;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.bukkit.entity.Player;

public class WorldMaintenanceService {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long startMs = 0L;
    private final ServerFacade server;
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final Notifier notifier;

    public WorldMaintenanceService(
            ServerFacade server, TypedConfigProvider configs, OrzTextStyles styles, Notifier notifier) {
        this.server = server;
        this.configs = configs;
        this.styles = styles;
        this.notifier = notifier;
    }

    public boolean isRunning() {
        return running.get();
    }

    public enum MaintenanceStage {
        Start,
        Running,
        Done,
        Error
    }

    public static MaintenanceStage mapProgressStage(ProgressStage stage) {
        if (stage == null) return MaintenanceStage.Running;
        if (stage == ProgressStage.Done) return MaintenanceStage.Done;
        return MaintenanceStage.Running;
    }

    private static String stageDisplayCN(ProgressStage s) {
        if (s == null) return "进行中";
        String n = s.name();
        if ("Region".equalsIgnoreCase(n)) return "区域";
        if ("Chunk".equalsIgnoreCase(n)) return "区块";
        if ("File".equalsIgnoreCase(n)) return "文件";
        if ("Done".equalsIgnoreCase(n)) return "完成";
        return "进行中";
    }

    private Function1<ProgressEvent, Unit> progressHandler(String label, Consumer<String> callback) {
        return progressEvent -> {
            Long current = progressEvent.getCurrent();
            Long total = progressEvent.getTotal();
            if (current == null || total == null || current <= 0 || total <= 0) {
                return Unit.INSTANCE;
            }
            int percent = (int) Math.ceil(current * 100.0 / total);
            MaintenanceStage stage = mapProgressStage(progressEvent.getStage());
            TypedConfigs.Templates tpls = configs.templates();
            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("label", label);
            vars.put("stage", stage.name());
            vars.put("percent", String.valueOf(percent));
            vars.put("stage_name", progressEvent.getStage().name());
            vars.put("stage_cn", stageDisplayCN(progressEvent.getStage()));
            long elapsedMs = Math.max(1, System.currentTimeMillis() - startMs);
            double ratePerSec = (current * 1000.0) / elapsedMs;
            long etaMs = (long) Math.max(0, (total - current) / Math.max(1e-6, ratePerSec) * 1000.0);
            TypedConfigs.TemplateOptions opt = configs.templateOptions();
            double ratePer = ratePerSec;
            String rateUnit = "/s";
            if ("per_min".equalsIgnoreCase(opt.rateUnit())) {
                ratePer = ratePerSec * 60.0;
                rateUnit = "/min";
            }
            long etaValue = etaMs;
            String etaUnit = "ms";
            if ("sec".equalsIgnoreCase(opt.etaUnit())) {
                etaValue = Math.round(etaMs / 1000.0);
                etaUnit = "s";
            } else if ("min".equalsIgnoreCase(opt.etaUnit())) {
                etaValue = Math.round(etaMs / 1000.0 / 60.0);
                etaUnit = "min";
            }
            String stageName = progressEvent.getStage().name();
            String stageI18n = TemplateResolvers.stageAlias(stageName, opt);
            vars.put("stage_cn", stageI18n);
            vars.put("stage_i18n", stageI18n);
            vars.put("rate_per", String.format("%.2f", ratePer));
            vars.put("rate_unit", rateUnit);
            vars.put("eta_value", String.valueOf(etaValue));
            vars.put("eta_unit", etaUnit);
            vars.put("current", String.valueOf(current));
            vars.put("total", String.valueOf(total));
            String eventKey = "备份".equals(label) ? "maintenance_backup_stage" : "maintenance_optimize_stage";
            MessageEnvelope env = configs.renderEvent(eventKey, vars);
            server.logger().info(env.message());
            if (progressEvent.getStage() == ProgressStage.Done) {
                long durationMs = Math.max(0, System.currentTimeMillis() - startMs);
                String doneKey = "备份".equals(label) ? "maintenance_backup_done" : "maintenance_optimize_done";
                MessageEnvelope done = configs.renderEvent(
                        doneKey, java.util.Map.of("label", label, "duration_ms", String.valueOf(durationMs)));
                callback.accept(done.message());
            }
            return Unit.INSTANCE;
        };
    }

    private Function1<Object, Unit> errorHandler(String label, Consumer<String> callback) {
        return obj -> {
            server.logger().warning(String.valueOf(obj));
            long durationMs = Math.max(0, System.currentTimeMillis() - startMs);
            String errKey = "备份".equals(label) ? "maintenance_backup_error" : "maintenance_optimize_error";
            MessageEnvelope err = configs.renderEvent(
                    errKey, java.util.Map.of("label", label, "duration_ms", String.valueOf(durationMs)));
            callback.accept(err.message());
            notifier.event(errKey, err);
            callback.accept("地图" + label + "失败");
            return Unit.INSTANCE;
        };
    }

    private void runOptimizerJob(
            boolean backupMode, Path input, Path outputOrNull, long tickTimeThreshold, Consumer<String> callback) {
        callback.accept("正在" + (backupMode ? "备份" : "优化") + "地图，请稍等......");
        OptimizerConfig cfg;
        DefaultMcaIOFactory mcaIOFactory = new DefaultMcaIOFactory();
        RealFileSystem fs = RealFileSystem.INSTANCE;
        if (backupMode) {
            cfg = new OptimizerConfig(
                    input,
                    outputOrNull,
                    tickTimeThreshold,
                    false,
                    ProgressMode.Region,
                    true,
                    false,
                    true,
                    true,
                    100L,
                    1000L,
                    errorHandler("备份", callback),
                    progressHandler("备份", callback),
                    0,
                    true,
                    null,
                    null,
                    fs,
                    null,
                    null,
                    mcaIOFactory);
        } else {
            cfg = new OptimizerConfig(
                    input,
                    null,
                    tickTimeThreshold,
                    false,
                    ProgressMode.Region,
                    false,
                    true,
                    true,
                    true,
                    100L,
                    1000L,
                    errorHandler("优化", callback),
                    progressHandler("优化", callback),
                    0,
                    true,
                    null,
                    null,
                    fs,
                    null,
                    null,
                    mcaIOFactory);
        }
        Optimizer.run(cfg);
    }

    public void runExclusive(String kickText, Runnable asyncWork, Runnable finallyWork) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        server.runSync(() -> {
            startMs = System.currentTimeMillis();
            for (Player p : server.server().getOnlinePlayers()) {
                p.kick(styles.warn(kickText));
            }
            OrzUtil.executeConsoleCmd(server, () -> {}, "save-off", "save-all flush");
            server.runAsync(() -> {
                try {
                    asyncWork.run();
                } catch (Exception e) {
                    server.logger().log(Level.SEVERE, "WorldMaintenanceService 异步任务异常", e);
                } finally {
                    OrzUtil.executeConsoleCmd(server, () -> {}, "save-on");
                    running.set(false);
                    if (finallyWork != null) {
                        finallyWork.run();
                    }
                }
            });
        });
    }

    public void backup(long tickTimeThreshold, int retainCount, Consumer<String> callback) {
        runExclusive(
                "服务器地图备份中，请稍后再尝试登录。",
                () -> {
                    File worldContainerDir = server.server().getWorldContainer();
                    File worldBackupDir = new File(server.plugin().getDataFolder(), "backup");
                    if (!worldBackupDir.exists() && !worldBackupDir.mkdirs()) {
                        server.logger().warning("创建地图备份目录失败: " + worldBackupDir.getAbsolutePath());
                        callback.accept("地图备份失败");
                        return;
                    }
                    Path input = Path.of(worldContainerDir.getAbsolutePath());
                    callback.accept("服务器地图目录：" + input);
                    File worldBackupTempDir = new File(worldBackupDir, "tempDir");
                    Path output = Path.of(worldBackupTempDir.getAbsolutePath());
                    callback.accept("地图备份目录：" + worldBackupDir);
                    runOptimizerJob(true, input, output, tickTimeThreshold, callback);
                    pruneOldZipsWithLogger(worldBackupDir, retainCount, server.logger());
                },
                null);
    }

    public void optimize(long tickTimeThreshold, Consumer<String> callback) {
        runExclusive(
                "服务器地图优化中，请稍后再尝试登录。",
                () -> {
                    File worldContainerDir = server.server().getWorldContainer();
                    Path input = Path.of(worldContainerDir.getAbsolutePath());
                    runOptimizerJob(false, input, null, tickTimeThreshold, callback);
                },
                null);
    }

    public static void pruneOldZips(File backupDir, int retain) {
        pruneOldZipsWithLogger(backupDir, retain, null);
    }

    private static void pruneOldZipsWithLogger(File backupDir, int retain, java.util.logging.Logger logger) {
        if (retain <= 0) retain = 10;
        File[] zips = backupDir.listFiles(f -> f.isFile() && f.getName().endsWith(".zip"));
        if (zips == null || zips.length <= retain) return;
        Arrays.sort(zips, (a, b) -> {
            try {
                BasicFileAttributes ab = readAttributes(a.toPath(), BasicFileAttributes.class);
                BasicFileAttributes bb = readAttributes(b.toPath(), BasicFileAttributes.class);
                return Long.compare(
                        bb.creationTime().toMillis(), ab.creationTime().toMillis());
            } catch (Exception e) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (int i = retain; i < zips.length; i++) {
            try {
                boolean deleted = zips[i].delete();
                if (!deleted && logger != null) {
                    logger.warning("删除旧备份失败: " + zips[i].getName());
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.severe("清理旧备份异常: " + e);
                }
            }
        }
    }
}
