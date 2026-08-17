package com.jokerhub.paper.plugin.orzmc.infra.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 全局日志环形缓冲，供 {@code $e} 控制台命令输出窗口收集使用。
 *
 * <p>设计为纯 Java、零 Log4J 依赖：日志系统（Log4J Appender）通过 {@link #capture(String)}
 * 把每一行日志喂进来，主线程通过 {@link #watermark()} + {@link #drainSince(long)} 取
 * 「执行命令后新增的日志行」。环形缓冲容量固定，超容量丢弃最老的行，内存占用有界。
 *
 * <p>线程安全：{@code capture} 由日志线程并发调用，{@code watermark}/{@code drainSince}
 * 由服务器主线程调用，全部方法 {@code synchronized} 串行化（容量 500 时开销可忽略）。
 */
public final class LogCaptureService {

    /** 缓冲容量。 */
    private final int capacity;

    /** ANSI 颜色码（{@code ESC[33m} 等）：插件命令输出常带色，发群前剥离。 */
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;\\d]*[A-Za-z]");

    /** 环形缓冲：尾部是最新的行。 */
    private final Deque<CapturedLine> buffer;

    /** 单调递增序号：既当水位，也用于窗口过滤。 */
    private long sequence;

    public LogCaptureService(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    /** 捕获一行（或多行，内部按 {@code \n} 拆分、剥 ANSI 色码、丢弃空行）。 */
    public synchronized void capture(String rawText) {
        if (rawText == null) {
            return;
        }
        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            // 快速路径：无 ANSI 码与 CR 时跳过正则/替换（服务器每条日志都会经过这里）
            if (line.indexOf('\u001B') >= 0) {
                line = ANSI_ESCAPE.matcher(line).replaceAll("");
                if (line.isBlank()) {
                    continue;
                }
            }
            if (buffer.size() == capacity) {
                buffer.removeFirst();
            }
            buffer.addLast(new CapturedLine(++sequence, line));
        }
    }

    /**
     * 当前水位：执行命令前调用，作为窗口起点。
     *
     * @return 当前已捕获的最大行序号
     */
    public synchronized long watermark() {
        return sequence;
    }

    /**
     * 取 {@code fromSeq} 之后新增的日志行文本（保序、幂等：不消费缓冲）。
     *
     * @param fromSeq 窗口起点序号（执行命令前取到的 {@link #watermark()}）
     * @return 新增行文本列表；无新增返回空列表
     */
    public synchronized List<String> drainSince(long fromSeq) {
        List<String> lines = new ArrayList<>();
        for (CapturedLine captured : buffer) {
            if (captured.seq() > fromSeq) {
                lines.add(captured.text());
            }
        }
        return lines;
    }

    /**
     * 窗口是否发生容量驱逐（水位到缓冲起点之间存在缺口）。
     *
     * <p>{@code $e} 窗口内服务器日志流量大时（如备份任务刷日志），缓冲满会把
     * 水位之后的行驱逐，{@link #drainSince(long)} 会静默丢行。调用方检测到缺口
     * 应在输出头部提示「可能不完整」。
     *
     * @param fromSeq 执行命令前取到的水位
     * @return true 表示 {@code fromSeq} 之后有行已被驱逐
     */
    public synchronized boolean hasGapSince(long fromSeq) {
        CapturedLine oldest = buffer.peekFirst();
        return oldest != null && oldest.seq() > fromSeq + 1;
    }

    /** 缓冲行数（测试与诊断用）。 */
    public synchronized int size() {
        return buffer.size();
    }

    /** 单条捕获行：序号 + 文本。 */
    public record CapturedLine(long seq, String text) {}
}
