package com.jokerhub.paper.plugin.orzmc.infra.notify;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按固定周期限频的通知/告警抑制器。
 *
 * <p>key 在 periodMs 内最多放行一次。已废弃的 {@code tnt.notify_throttle_ms} 读取
 * （上下线限流）已由 {@code player_notify.window_ms} 聚合窗口取代，相关 {@code shouldRunDefault}
 * /{@code runDefault} 死代码一并移除；本类仅保留固定周期限频供与 TNT 无关的告警使用。</p>
 */
public final class ThrottledNotifier {
    private final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();
    private volatile long lastCleanup = 0L;

    /** 按固定周期限频：key 在 periodMs 内最多放行一次，用于与 TNT 无关的告警限频。 */
    public boolean shouldRun(String key, long periodMs) {
        return shouldRun(key, periodMs, periodMs);
    }

    private boolean shouldRun(String key, long periodMs, long ttlMs) {
        long now = System.currentTimeMillis();
        // 判定的依据用 compute 的返回值而非外部 boolean[]：ConcurrentHashMap.compute 在 CAS
        // 重试时可能多次调用 remapping 函数，外部副作用数组会被「先置 true、最终未更新」污染
        // （假放行）。stamp 装箱一次，remapping 返回 stamp ⇔ 本次确实放行（引用相等判定）。
        Long stamp = now;
        Long result = last.compute(key, (k, prev) -> {
            if (prev == null || now - prev >= periodMs) {
                return stamp; // 放行：写回本次时间戳
            }
            return prev; // 窗口内：保持原时间戳，不放行
        });
        maybeCleanup(now, ttlMs);
        return result == stamp;
    }

    private void maybeCleanup(long now, long ttlMs) {
        long lc = lastCleanup;
        if (now - lc >= ttlMs) {
            lastCleanup = now;
            last.entrySet().removeIf(e -> now - e.getValue() >= ttlMs);
        }
    }
}
