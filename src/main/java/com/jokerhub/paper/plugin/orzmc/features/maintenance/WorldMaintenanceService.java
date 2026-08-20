package com.jokerhub.paper.plugin.orzmc.features.maintenance;

import static java.nio.file.Files.readAttributes;

import com.jokerhub.orzmc.world.*;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
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

    /**
     * 将毫秒耗时格式化为中文可读形式（分级取整）：
     * <ul>
     *   <li>&lt; 1 秒 → {@code 854毫秒}</li>
     *   <li>&lt; 1 分钟 → {@code 35秒}</li>
     *   <li>&lt; 1 小时 → {@code 2分35秒}</li>
     *   <li>≥ 1 小时 → {@code 1小时2分3秒}（整分/整小时省略低位；如 {@code 1小时0分5秒}、{@code 2小时3分}）</li>
     * </ul>
     * 秒数按四舍五入取整（154901ms → 155s → {@code 2分35秒}）；负值按 0 处理。
     */
    public static String formatDuration(long ms) {
        long safeMs = Math.max(0, ms);
        if (safeMs < 1000) {
            return safeMs + "毫秒";
        }
        long totalSec = Math.round(safeMs / 1000.0);
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("小时");
        }
        if (minutes > 0 || (hours > 0 && seconds > 0)) {
            sb.append(minutes).append("分");
        }
        if (seconds > 0 || (hours == 0 && minutes == 0)) {
            sb.append(seconds).append("秒");
        }
        return sb.toString();
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
            Templates tpls = configs.templates();
            java.util.Map<String, String> vars = new java.util.HashMap<>();
            vars.put("label", label);
            vars.put("stage", stage.name());
            vars.put("percent", String.valueOf(percent));
            vars.put("stage_name", progressEvent.getStage().name());
            vars.put("stage_cn", stageDisplayCN(progressEvent.getStage()));
            long elapsedMs = Math.max(1, System.currentTimeMillis() - startMs);
            double ratePerSec = (current * 1000.0) / elapsedMs;
            long etaMs = (long) Math.max(0, (total - current) / Math.max(1e-6, ratePerSec) * 1000.0);
            TemplateOptions opt = configs.templateOptions();
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
                        doneKey,
                        java.util.Map.of(
                                "label",
                                label,
                                "duration_ms",
                                String.valueOf(durationMs),
                                "duration_human",
                                formatDuration(durationMs)));
                callback.accept(done.message());
                // 聚合提示：chunk 级错误（损坏区块已安全保留）完成后汇总一次
                long chunkErrors = chunkErrorCount.get();
                if (chunkErrors > 0) {
                    String summary = "（" + label + "含 " + chunkErrors + " 个损坏区块，已安全保留原始数据，详见服务器日志）";
                    server.logger().info(summary);
                    callback.accept(summary);
                }
            }
            return Unit.INSTANCE;
        };
    }

    private final java.util.concurrent.atomic.AtomicInteger chunkErrorCount =
            new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicBoolean fatalErrorReported =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** 备份并行度 = CPU 逻辑核数（backup-core 按维度+区域并行扫描/写入，单线程大世界太慢）。 */
    private static int cpuParallelism() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    private Function1<Object, Unit> errorHandler(String label, Consumer<String> callback) {
        return obj -> {
            server.logger().warning(String.valueOf(obj));
            String s = String.valueOf(obj);
            // chunk 级解析/写入错误（unknown compression / ZLIB 截断 / 荒谬长度等损坏区块）：
            // 修复后由 backup-core 安全跳过或保留原始数据，不视为失败——聚合计数，Done 时汇总提示
            if (s.contains("Pattern matching failed")
                    || s.contains("kind=Pattern")
                    || s.contains("Chunk data unreadable")
                    || s.contains("corrupted length field")) {
                chunkErrorCount.incrementAndGet();
                if (chunkErrorCount.get() == 1) {
                    callback.accept("发现" + label + "目标包含损坏区块，将安全保留原始数据（不丢失）...");
                }
                return Unit.INSTANCE;
            }
            // 致命错误（压缩失败/输出失败等）：限频 1 次完整失败通知，避免重复刷屏
            if (fatalErrorReported.compareAndSet(false, true)) {
                long durationMs = Math.max(0, System.currentTimeMillis() - startMs);
                String errKey = "备份".equals(label) ? "maintenance_backup_error" : "maintenance_optimize_error";
                MessageEnvelope err = configs.renderEvent(
                        errKey,
                        java.util.Map.of(
                                "label",
                                label,
                                "duration_ms",
                                String.valueOf(durationMs),
                                "duration_human",
                                formatDuration(durationMs)));
                callback.accept(err.message());
                notifier.event(errKey, err);
                callback.accept("地图" + label + "失败");
            }
            return Unit.INSTANCE;
        };
    }

    private void runOptimizerJob(
            boolean backupMode, Path input, Path outputOrNull, long tickTimeThreshold, Consumer<String> callback) {
        callback.accept("正在" + (backupMode ? "备份" : "优化") + "地图，请稍等......");
        String label = backupMode ? "备份" : "优化";
        DefaultMcaIOFactory mcaIOFactory = new DefaultMcaIOFactory();
        RealFileSystem fs = RealFileSystem.INSTANCE;
        DefaultOptimizer.INSTANCE.run(input, outputOrNull, builder -> {
            builder.setFilter(new FilterOptions(tickTimeThreshold, false, true));
            builder.setOutputOptions(new OutputOptions(!backupMode, backupMode, true, true, false));
            builder.setProgress(new ProgressOptions(100L, 1000L, event -> {
                progressHandler(label, callback).invoke(event);
            }));
            builder.setRuntime(new RuntimeOptions(cpuParallelism()));
            builder.setHooks(new Hooks(errorHandler(label, callback), null, null));
            // IOOptions 第三参 syncOnFinalize=true（0.3.0+ API，与默认一致）：跳过 finalize 后
            // 逐 region fsync（更快）；zip 由 backup-core 写到 output 父目录（backup/）
            builder.setIo(new IOOptions(fs, mcaIOFactory, true));
            return Unit.INSTANCE;
        });
    }

    public void runExclusive(String kickText, Runnable asyncWork, Runnable finallyWork) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // 复位本次运行的错误聚合计数器：否则跨 run 累积——fatalErrorReported 一旦置 true，
        // 后续 run 的致命错误不再发群通知；chunkErrorCount 累积导致干净 run 误报「含 N 个损坏区块」。
        chunkErrorCount.set(0);
        fatalErrorReported.set(false);
        server.runSync(() -> {
            startMs = System.currentTimeMillis();
            for (Player p : server.server().getOnlinePlayers()) {
                // Folia：踢人投递到玩家所在 region 线程；save 命令留在 global region 的 dispatchCommand
                p.getScheduler().run(server.plugin(), t -> p.kick(styles.warn(kickText)), () -> {});
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
                    File worldDir = worldFolder();
                    // 备份目录放服务器核心根目录（非插件数据目录），便于快照/迁移整体打包
                    File worldBackupDir = new File(server.server().getWorldContainer(), "backup");
                    if (!worldBackupDir.exists() && !worldBackupDir.mkdirs()) {
                        server.logger().warning("创建地图备份目录失败: " + worldBackupDir.getAbsolutePath());
                        callback.accept("地图备份失败");
                        return;
                    }
                    Path input = worldDir.toPath();
                    callback.accept("服务器地图目录：" + input);
                    // 中间目录放备份结果目录（backup/tempDir）：backup-core 0.3.x 校验要求
                    // output 与 input（世界目录）不重叠——backup/ 是世界目录的兄弟路径，天然满足；
                    // zip 由 backup-core 写到 output 父目录（backup/），无需移动。
                    // 崩溃/断电残留由启动清理兜底（cleanupStaleBackupTemp）。
                    Path output = worldBackupDir.toPath().resolve("tempDir");
                    callback.accept("地图备份目录：" + worldBackupDir);
                    long before = latestBackupZipMtime(worldBackupDir);
                    runOptimizerJob(true, input, output, tickTimeThreshold, callback);
                    if (latestBackupZipMtime(worldBackupDir) <= before) {
                        // backup-core 完成但 backup/ 无新 zip（压缩失败被内部吞掉等）：
                        // 明确报失败且跳过 prune——旧备份是唯一可靠副本，不能误删
                        server.logger().severe("备份文件未落盘: " + worldBackupDir.getAbsolutePath());
                        callback.accept("地图备份失败（备份文件未生成，请检查服务器日志与磁盘空间）");
                        return;
                    }
                    pruneOldZipsWithLogger(worldBackupDir, retainCount, server.logger());
                },
                null);
    }

    /** 世界根目录（备份/优化 input）：以 worldContainer 为基准解析 level-name 世界目录
     *  （server.properties 配置，默认 world/）。
     *  ⚠️ 26.1+ 布局下 World#getWorldFolder()/getWorldPath() 返回的是维度数据目录
     *  （world/dimensions/minecraft/overworld）而非世界根——直接用作 input 会漏备
     *  level.dat/players/世界级 data/下界/末地（#215 回归）；改回 1.0.17 的
     *  getWorldContainer 系 API 定位世界根。backup/ 与它是兄弟路径，
     *  天然满足 backup-core 0.3.x 的 input/output 不重叠校验。 */
    private File worldFolder() {
        File container = server.server().getWorldContainer();
        File levelRoot = new File(container, levelNameFromProperties(container));
        if (levelRoot.isDirectory()) {
            return levelRoot;
        }
        // level-name 目录缺失：回退默认 world/；仍不存在时交给 backup-core 明确报错——
        // 不能回退 container 本身：backup/ 嵌套其内会触发 0.3.x 重叠校验拒绝，
        // 或把 plugins/logs/历史 zip 全部扫入备份。
        return new File(container, "world");
    }

    /** 读取 server.properties 的 level-name（尊重自定义世界目录名），缺失/解析失败回退默认 "world"。 */
    private static String levelNameFromProperties(File container) {
        File props = new File(container, "server.properties");
        if (props.isFile()) {
            try (java.io.InputStream in = new java.io.FileInputStream(props)) {
                java.util.Properties p = new java.util.Properties();
                p.load(in);
                String name = p.getProperty("level-name");
                if (name != null && !name.isBlank() && isValidLevelName(name)) {
                    return name.trim();
                }
            } catch (Exception e) {
                // 解析失败（含非法 Unicode 转义序列导致的 IllegalArgumentException）回退默认值，备份不因此中断
            }
        }
        return "world";
    }

    /** level-name 只允许目录名（Minecraft 本身不允许路径分隔符），拒绝越出容器/撞入备份目录。 */
    private static boolean isValidLevelName(String name) {
        return !name.contains("/") && !name.contains("\\") && !name.contains("..") && !name.equals(".");
    }

    /** backup/ 下最新 zip 的 mtime（无 zip 为 0）。备份成功判定：备份后出现 mtime 更新的 zip。 */
    private static long latestBackupZipMtime(File backupDir) {
        File[] zips = backupDir.listFiles(f -> f.isFile() && f.getName().endsWith(".zip"));
        long latest = 0L;
        if (zips != null) {
            for (File z : zips) {
                latest = Math.max(latest, z.lastModified());
            }
        }
        return latest;
    }

    /** 启动清理：崩溃/断电可能导致 backup/tempDir 残留，删除防占用磁盘与污染下次备份。 */
    public static void cleanupStaleBackupTemp(File backupDir, java.util.logging.Logger logger) {
        File tempDir = new File(backupDir, "tempDir");
        if (!tempDir.exists()) {
            return;
        }
        try {
            deleteTreeQuietly(tempDir.toPath(), logger);
            logger.info("已清理上次异常残留的备份临时目录: " + tempDir.getAbsolutePath());
        } catch (Exception e) {
            logger.log(Level.WARNING, "清理备份临时目录残留失败: " + tempDir.getAbsolutePath(), e);
        }
    }

    /** 递归删除临时目录（备份成功/失败均清理，防残留被 walk 扫入下次备份源）。 */
    private static void deleteTreeQuietly(Path root, java.util.logging.Logger logger) {
        try {
            java.nio.file.Files.walk(root)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            java.nio.file.Files.deleteIfExists(p);
                        } catch (java.io.IOException ignored) {
                            // 忽略单文件删除失败，尽力清理
                        }
                    });
        } catch (java.io.IOException e) {
            logger.log(Level.WARNING, "清理备份临时目录失败: " + root, e);
        }
    }

    public void optimize(long tickTimeThreshold, Consumer<String> callback) {
        runExclusive(
                "服务器地图优化中，请稍后再尝试登录。",
                () -> {
                    Path input = worldFolder().toPath();
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
