package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.LoginRateLimitConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 进服限流/反 bot 判定核心（纯逻辑，安全加固 P2-2）。
 *
 * <p>在 {@code AsyncPlayerPreLoginEvent} 事件线程（异步）上按 IP 做两类检查：</p>
 * <ol>
 *   <li><b>频率</b>：60s 滑动窗口内登录尝试超过 {@code max_login_attempts_per_minute} 即拒绝——
 *       拦截 bot 群刷登录包；</li>
 *   <li><b>并发</b>：该 IP 当前在线玩家数达到 {@code max_concurrent_per_ip} 即拒绝——
 *       拦截单 IP 多开/代练/alt 农场。</li>
 * </ol>
 *
 * <p>状态按 IP 字符串分桶（{@link ConcurrentHashMap}），事件线程可安全并发；玩家加入时登记
 * 并发、退出时按玩家名反查 IP 注销（退出时 socket 可能已关闭无法取地址）。配置通过
 * {@link Supplier} 注入，事件侧每次读取最新配置（与 {@code ChatSpamFilterService} 一致），
 * /config reload 生效。</p>
 */
public final class LoginRateLimitService {

    /** 频率限流滑动窗口长度（毫秒）。 */
    private static final long WINDOW_MS = 60_000L;

    private final Supplier<LoginRateLimitConfig> configSupplier;
    private final LongSupplier clock;
    /** ip → 窗口内各次登录尝试时刻。 */
    private final Map<String, Deque<Long>> attemptTimes = new ConcurrentHashMap<>();
    /** ip → 当前在线玩家名集合（并发计数）。 */
    private final Map<String, Set<String>> onlineByIp = new ConcurrentHashMap<>();
    /** 玩家名 → ip（退出时反查，地址可能已关闭）。 */
    private final Map<String, String> ipByPlayer = new ConcurrentHashMap<>();

    public LoginRateLimitService(Supplier<LoginRateLimitConfig> configSupplier) {
        this(configSupplier, System::currentTimeMillis);
    }

    /** 测试用：注入可控时钟以验证窗口滑动。 */
    LoginRateLimitService(Supplier<LoginRateLimitConfig> configSupplier, LongSupplier clock) {
        this.configSupplier = configSupplier;
        this.clock = clock;
    }

    /** 判定该 IP 是否频率超限；放行则记录本次尝试。 */
    public boolean isRateLimited(String ip) {
        if (!enabled()) {
            return false;
        }
        long now = clock.getAsLong();
        Deque<Long> times = attemptTimes.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= configSupplier.get().maxLoginAttemptsPerMinute()) {
                return true;
            }
            times.addLast(now);
            return false;
        }
    }

    /** 判定该 IP 当前在线玩家是否已达并发上限。 */
    public boolean isConcurrencyReached(String ip) {
        if (!enabled()) {
            return false;
        }
        Set<String> online = onlineByIp.get(ip);
        return online != null && online.size() >= configSupplier.get().maxConcurrentPerIp();
    }

    /** 玩家成功加入后登记并发（同一 IP 下的在线名单）。 */
    public void onPlayerJoin(String ip, String playerName) {
        ipByPlayer.put(playerName, ip);
        onlineByIp.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(playerName);
    }

    /** 玩家退出后注销并发（按玩家名反查 IP，socket 可能已关闭）。 */
    public void onPlayerQuit(String playerName) {
        String ip = ipByPlayer.remove(playerName);
        if (ip == null) {
            return;
        }
        Set<String> online = onlineByIp.get(ip);
        if (online != null) {
            online.remove(playerName);
            if (online.isEmpty()) {
                onlineByIp.remove(ip, online);
            }
        }
    }

    /** 清理单个 IP 的限流/并发状态。 */
    public void clear(String ip) {
        attemptTimes.remove(ip);
        onlineByIp.remove(ip);
    }

    private boolean enabled() {
        return configSupplier.get().enabled();
    }
}
