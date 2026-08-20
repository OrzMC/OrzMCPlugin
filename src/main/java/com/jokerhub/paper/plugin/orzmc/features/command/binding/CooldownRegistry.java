package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class CooldownRegistry {
    private static final ConcurrentHashMap<String, Long> lastInvoke = new ConcurrentHashMap<>();
    /** 测试用：注入假时钟避免真实 sleep；生产默认系统时钟。 */
    static LongSupplier clock = System::currentTimeMillis;

    private CooldownRegistry() {}

    public static boolean isCoolingDown(String key, int seconds) {
        if (seconds <= 0) return false;
        long now = clock.getAsLong();
        Long prev = lastInvoke.get(key);
        if (prev == null || now - prev >= seconds * 1000L) {
            lastInvoke.put(key, now);
            return false;
        }
        return true;
    }

    /** 清空冷却状态（供测试隔离；生产 key=命令|玩家 有界，冷却过期即覆盖，无需主动清扫）。 */
    static void reset() {
        lastInvoke.clear();
    }
}
