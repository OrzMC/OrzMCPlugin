package com.jokerhub.paper.plugin.orzmc.features.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 命令审计日志（安全加固 P0-4）。
 *
 * <p>把（放行的）命令执行与（拦截的）危险命令追加写入
 * {@code plugins/OrzMC/audit/command_audit.log}，一行一条：</p>
 *
 * <pre>{@code ISO时间 | 来源(game/console/RCON/bot) | 发送者 | 命令原文 | 结果(executed/blocked)}</pre>
 *
 * <p>文件超过上限（默认 5MB）时轮转：当前日志改名 {@code command_audit.log.1}（覆盖旧备份），
 * 从新文件重新开始。开关由 {@code SecurityGuardConfig.auditEnabled} 注入（每次读取最新配置）。</p>
 *
 * <p><b>写盘异步化</b>：{@code record()} 只格式化入队（非阻塞），单写线程落盘——命令在主/region
 * 线程触发，同步写盘（含 5MB 文件 {@code Files.size} 检查）会拖慢 tick 线程。时间戳在入队时
 * 采样（记录命令执行时刻，而非写盘时刻）。</p>
 */
public final class CommandAuditService {

    /** 默认单文件上限：5MB。 */
    public static final int DEFAULT_MAX_BYTES = 5 * 1024 * 1024;

    /** 审计日志文件名。 */
    public static final String LOG_FILE_NAME = "command_audit.log";

    /** 轮转备份文件名（单级覆盖最旧）。 */
    public static final String ROTATED_FILE_NAME = "command_audit.log.1";

    /** 来源：玩家聊天栏命令。 */
    public static final String SOURCE_GAME = "game";
    /** 来源：控制台命令。 */
    public static final String SOURCE_CONSOLE = "console";
    /** 来源：RCON。 */
    public static final String SOURCE_RCON = "RCON";
    /** 来源：机器人（$e 控制台执行）。 */
    public static final String SOURCE_BOT = "bot";

    /** 结果：命令被放行并执行。 */
    public static final String RESULT_EXECUTED = "executed";
    /** 结果：危险命令被拦截。 */
    public static final String RESULT_BLOCKED = "blocked";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Supplier<Boolean> enabledSupplier;
    private final Path logFile;
    private final int maxBytes;
    private final Logger logger;
    /** 单写线程：串行落盘，避免多线程 append 交错 + tick 线程阻塞。 */
    private final ExecutorService writer;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public CommandAuditService(Supplier<Boolean> enabledSupplier, Path auditDir, int maxBytes, Logger logger) {
        this.enabledSupplier = enabledSupplier;
        this.logFile = auditDir.resolve(LOG_FILE_NAME);
        this.maxBytes = maxBytes;
        this.logger = logger;
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OrzMC-CommandAudit");
            t.setDaemon(true);
            return t;
        });
    }

    /** 追加一条审计记录。来源见 {@code SOURCE_*}，blocked 决定结果 {@code blocked}/{@code executed}。 */
    public void record(String source, String sender, String commandLine, boolean blocked) {
        if (!Boolean.TRUE.equals(enabledSupplier.get()) || closed.get()) {
            return;
        }
        String line = TIMESTAMP.format(OffsetDateTime.now())
                + " | " + source
                + " | " + sanitize(sender)
                + " | " + sanitize(commandLine)
                + " | " + (blocked ? RESULT_BLOCKED : RESULT_EXECUTED)
                + System.lineSeparator();
        writer.submit(() -> writeLine(line));
    }

    /** 冲刷所有待写记录（测试与 shutdown 前调用，保证落盘）。 */
    public void flush() {
        try {
            writer.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("[OrzMC] 命令审计冲刷失败: " + e.getMessage());
        }
    }

    /** 停用写线程（shutdown 后 record 直接丢弃，不再入队）。 */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            writer.shutdown();
        }
    }

    /** 单行落盘 + 超限轮转（写线程内执行）。 */
    private void writeLine(String line) {
        try {
            Files.createDirectories(logFile.getParent());
            rotateIfNeeded();
            Files.writeString(
                    logFile, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            logger.warning("[OrzMC] 命令审计写入失败: " + e.getMessage());
        }
    }

    /** 超限即轮转：当前文件改名 .1（覆盖旧备份），后续写入新文件。 */
    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(logFile) || Files.size(logFile) < maxBytes) {
            return;
        }
        Files.deleteIfExists(logFile.resolveSibling(ROTATED_FILE_NAME));
        Files.move(logFile, logFile.resolveSibling(ROTATED_FILE_NAME));
    }

    /** 命令原文可能出现换行，替换为空格保证一行一条记录。 */
    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\r', ' ').replace('\n', ' ');
    }
}
