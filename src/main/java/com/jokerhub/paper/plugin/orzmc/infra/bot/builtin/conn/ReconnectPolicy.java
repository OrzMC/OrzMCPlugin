package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

/**
 * 网关连接重连退避策略（builtin IM 骨架，方案 §4.2）。
 *
 * <p>默认值对齐 EasyBot RobustWebSocketClient 既有参数（5s 起 / 60s 上限 / ±jitter / 稳定 20s 重置），
 * 参数可注入以便单测用小时间尺度验证。</p>
 *
 * @param baseRetryMs 首次重连基准延迟（毫秒，必须 &gt; 0）
 * @param maxRetryMs 指数退避上限（毫秒，必须 &gt;= baseRetryMs）
 * @param jitterPercent 抖动百分比 0–100（乘性扰动防重连惊群；0 = 无抖动，测试用）
 * @param stableResetMs 连接稳定窗口：连接保持该时长未断线即重置连续失败计数与退避（毫秒；0 = 立即重置）
 * @param maxConsecutiveFailures 连续重连失败上限（不含首次建连；0 = 无限自动重连）
 */
public record ReconnectPolicy(
        long baseRetryMs, long maxRetryMs, int jitterPercent, long stableResetMs, int maxConsecutiveFailures) {

    public static ReconnectPolicy defaults() {
        return new ReconnectPolicy(5000, 60000, 20, 20000, 0);
    }

    public ReconnectPolicy {
        if (baseRetryMs <= 0) {
            throw new IllegalArgumentException("baseRetryMs must be > 0");
        }
        if (maxRetryMs < baseRetryMs) {
            throw new IllegalArgumentException("maxRetryMs must be >= baseRetryMs");
        }
        if (jitterPercent < 0 || jitterPercent > 100) {
            throw new IllegalArgumentException("jitterPercent must be in 0..100");
        }
        if (stableResetMs < 0) {
            throw new IllegalArgumentException("stableResetMs must be >= 0");
        }
        if (maxConsecutiveFailures < 0) {
            throw new IllegalArgumentException("maxConsecutiveFailures must be >= 0 (0=unlimited)");
        }
    }
}
