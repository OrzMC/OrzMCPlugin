package com.jokerhub.paper.plugin.orzmc.infra.bot;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 入站事件去重（防平台重放导致命令重复执行）。
 *
 * <p><b>为什么需要</b>：QQ 网关断线重连走 {@code op6 RESUME} 会话续传、Discord Gateway 重连走 RESUME 重放
 * 遗漏事件——两侧平台都是「至少一次」投递语义，同一条用户消息可能被再次下发。业务命令里有非幂等操作
 * （{@code $e} 控制台执行、{@code $b} 备份、{@code $o} 优化、{@code $r} 升降级），重放会造成重复执行。</p>
 *
 * <p><b>语义</b>：按平台消息 id（QQ {@code d.id} / Discord {@code d.id}）在 TTL 窗口内只放行一次；无 id
 * 时 fail-open（放行，保持既有行为，不因缺字段吞消息）。判定与写入原子（{@code ConcurrentHashMap.compute}
 * 返回值 + 时间戳引用相等，避免 CAS 重试污染外部状态——同 {@code ThrottledNotifier} 的教训）。</p>
 *
 * <p>容量有界：超过 {@link #MAX_ENTRIES} 时清理过期项，仍超限则整体清空（去重是尽力而为的防护，不占内存）。</p>
 */
public final class InboundDedup {

    /** 条目上限：超限清理过期项/整体清空，防陌生会话轰炸刷爆内存。 */
    static final int MAX_ENTRIES = 512;

    private final long ttlMs;
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private volatile long lastCleanupMs;

    /**
     * @param ttlMs 去重窗口（毫秒）；≤0 时按 5 分钟兜底（覆盖一次典型重连重放窗口）
     */
    public InboundDedup(long ttlMs) {
        this.ttlMs = ttlMs <= 0 ? 300_000L : ttlMs;
    }

    /**
     * 记录并判定：首次见到 → true（调用方应继续处理）；TTL 窗口内已见过 → false（重复，应丢弃）。
     *
     * @param messageId 平台消息 id（null/空白 → 返回 true：无 id 不去重）
     */
    public boolean firstSeen(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long stamp = now;
        Long result = seen.compute(messageId, (key, prev) -> prev == null || now - prev >= ttlMs ? stamp : prev);
        maybeCleanup(now);
        return result == stamp;
    }

    /** 当前记录数（测试/诊断用）。 */
    int size() {
        return seen.size();
    }

    private void maybeCleanup(long now) {
        if (seen.size() < MAX_ENTRIES) {
            return;
        }
        if (now - lastCleanupMs >= ttlMs) {
            lastCleanupMs = now;
            seen.entrySet().removeIf(e -> now - e.getValue() >= ttlMs);
        }
        if (seen.size() >= MAX_ENTRIES) {
            seen.clear(); // 全在窗口内仍超限（异常洪峰）：整体清空，丢旧保新
        }
    }
}
