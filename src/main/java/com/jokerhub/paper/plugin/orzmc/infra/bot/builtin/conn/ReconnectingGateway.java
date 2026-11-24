package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.TokenRefresher.RefreshOutcome;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

/**
 * 统一连接生命周期基类（builtin IM 骨架，方案 §4.2 / R12 / R13）。
 *
 * <p>基于 Java-WebSocket 封装各平台出站网关 WS 的公共生命周期：</p>
 * <ul>
 *   <li><b>指数退避自动重连</b>：5s 起 / 60s 上限 / ±jitter / 稳定 20s 重置（参数经 {@link ReconnectPolicy} 注入，
 *       默认值对齐 EasyBot RobustWebSocketClient）；连续失败达上限（策略配置）或鉴权不可恢复 → fatal 终止；</li>
 *   <li><b>协议心跳钩子</b>：子类在收到 hello 等握手帧后调用 {@link #configureHeartbeat(long, Supplier)}，
 *       基类按周期发送帧，并做静默看门狗（默认：连续 3 个心跳周期无任何入站活性 → 强制断开触发重连）。
 *       「入站活性」= 文本/二进制帧 + WS 控制帧 ping/pong（issue #610：空闲期平台只发控制帧时不得误判为死亡）；
 *       窗口可经 {@link #setSilenceTimeoutMs(long)}（im.yml {@code heartbeat_timeout_seconds}）覆盖；</li>
 *   <li><b>状态回调</b>：{@link GatewayStateListener} 上报 connected / disconnected / fatal，供健康聚合；</li>
 *   <li><b>鉴权错误回调</b>：子类识别鉴权失败后调用 {@link #onAuthFailure()}，经注入的 {@link TokenRefresher}
 *       刷新令牌后立即重连（REFRESHED）/退避重试（RETRY_LATER）/ fatal（DEAD）；</li>
 *   <li><b>线程纪律</b>：所有定时/重连任务在单线程调度器（线程名 {@code im-<name>-gw}）；WS 回调线程与子类钩子
 *       直接交互，子类不得在钩子内触碰 Bukkit API（R12，入站事件调度见 S6）；{@link #stop()} 幂等清理并等待终止（R13）。</li>
 * </ul>
 *
 * <p>协议内容（identify 帧构造 / opcode 分发 / resume 决策）由子类实现——本类只负责传输生命周期。</p>
 */
public abstract class ReconnectingGateway {

    public enum State {
        /** 尚未 start。 */
        IDLE,
        /** 正在建连（含退避等待中的重连）。 */
        CONNECTING,
        /** 连接已建立。 */
        OPEN,
        /** 连接丢失，即将排定重连。 */
        RECONNECTING,
        /** 不可恢复终止（重连耗尽 / 鉴权 DEAD），需手动 restart。 */
        FATAL,
        /** 已 stop，实例不可复用。 */
        STOPPED
    }

    private enum QueueResult {
        QUEUED,
        FATAL,
        IGNORED
    }

    /** 静默看门狗默认窗口：连续多少个心跳周期无任何入站活性即判定连接死亡。 */
    private static final long SILENT_CYCLES = 3;

    /** 同一段不稳定期内静默超时的 WARN 次数上限（超出降 INFO：空服长跑反复来回不应刷屏）。 */
    private static final int SILENCE_WARN_TIMES = 1;

    /**
     * 连续「同一远端地址」静默断流的提示阀值（issue #616）。
     *
     * <p>达到该次数时额外给一条带排查建议的 WARN，并抬高重连退避（{@link #silenceEscalationSteps()}）——
     * 「重连 → 重鉴权 → 再断流」的循环不仅没用，还会刷掉平台侧配额、把真问题埋在日志里。</p>
     */
    static final int SAME_ADDRESS_SILENCE_HINT = 3;

    /**
     * 「零入站提前判决」阈值：连接建立后连续发出该数量的心跳、却**一帧入站都没收到**，就立刻判失败重连，
     * 不再等满静默窗口（issue #616）。
     *
     * <p>依据（2026-11-23 通宵实测，55 次飞书静默里 30 次是「累计入站 0 帧」）：这类连接在 WS 握手阶段是成功的，
     * 但应用层一帧数据都没有——等满窗口（飞书 3×90s=270s）纯属空耗，期间群里发命令也不会有人回。
     * 取 2 次心跳：飞书 180s、QQ 60s、Discord 约 82s，都比原窗口早 1/3 以上，同时给慢回帧环境留了余量。</p>
     */
    static final int ZERO_INBOUND_HEARTBEAT_LIMIT = 2;

    /**
     * 建连（TCP + TLS + WS 握手）默认超时。
     *
     * <p>Java-WebSocket 的 2 参构造把 connect timeout 置为 0 = **无限等待**：对端接了 TCP 却不再完成 TLS/握手
     * （发报机被中间设备/墙回包的黑洞环境常见）时，{@code connect()} 会永久阻塞在建连的调度线程上——
     * 不重连、不发心跳、也不打任何日志，网关就这样“静默死掉”（2026-11-22 本机实测，见 issue #616）。</p>
     */
    static final int DEFAULT_CONNECT_TIMEOUT_MS = 15_000;

    private final String name;
    private final Logger log;
    private final ReconnectPolicy policy;
    private final TokenRefresher tokenRefresher;
    private final GatewayStateListener listener;

    private final Object lock = new Object();
    private final ScheduledExecutorService scheduler;

    private State state = State.IDLE;
    private volatile WebSocketClient currentWs;
    private volatile long lastReceivedMs;
    private volatile String lastProblem;

    private int reconnectAttempts;
    private boolean immediateNext;
    private ScheduledFuture<?> reconnectFuture;
    private ScheduledFuture<?> heartbeatFuture;
    private volatile long heartbeatIntervalMs;
    private volatile HeartbeatPump heartbeatPump;
    /** 静默窗口覆盖（毫秒；0 = 按心跳周期 × {@link #SILENT_CYCLES} 自动推导）。见 {@link #setSilenceTimeoutMs(long)}。 */
    private volatile long silenceTimeoutMs;
    /** 累计入站活性数（文本/二进制帧 + WS ping/pong）——每次建连清零，用于静默超时的诊断归因。 */
    private final AtomicLong inboundFrames = new AtomicLong();
    /** 本轮连接已发出的心跳帧数（每次建连清零，诊断「是不是心跳根本没发出去」）。 */
    private final AtomicLong heartbeatSends = new AtomicLong();

    /** 当前/上次连接的对端地址（{@code host:port}；issue #616 诊断用，未连接时为 null）。 */
    private volatile String remoteAddress;

    /**
     * 连续「同一远端地址」静默断流次数（issue #616）。
     *
     * <p>写路径在 {@code lock} 内；读路径（日志 / 诊断 / 退避计算）按 volatile 读，允许瞬时偏差。</p>
     */
    private volatile int sameAddressSilenceStreak;

    /** 上一次「零入站静默断流」的对端地址（与 {@link #sameAddressSilenceStreak} 配对判定同一地址反复断流）。 */
    private String silentStreakAddress;

    /** 连续「静默超时」触发的重连次数（连接稳定且收到过入站帧后清零）：用于日志分级、恢复提示与诊断快照。 */
    private volatile int silenceLosses;

    /** 心跳发送器：文本（QQ op1 JSON）或二进制（飞书 protobuf ping）各一实现，帧类型保持平台正确。 */
    private interface HeartbeatPump {
        void send(WebSocketClient ws);
    }

    /**
     * @param name 平台标识（如 "qq"），用于日志与调度线程命名
     * @param server 服务端日志门面
     * @param policy 重连退避策略
     * @param tokenRefresher 鉴权失败刷新策略；可为 null（此时 {@link #onAuthFailure()} 直接 fatal）
     * @param listener 连接状态观察者；可为 null
     */
    protected ReconnectingGateway(
            String name,
            ServerLogger server,
            ReconnectPolicy policy,
            TokenRefresher tokenRefresher,
            GatewayStateListener listener) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (server == null) {
            throw new IllegalArgumentException("server must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.name = name;
        this.log = server.logger();
        this.policy = policy;
        this.tokenRefresher = tokenRefresher;
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "im-" + name + "-gw");
            t.setDaemon(true);
            return t;
        });
    }

    // =====================================================================
    // 对外生命周期
    // =====================================================================

    /** 启动网关（首次建连立即执行）；已运行 / FATAL 状态下可安全调用以重启。 */
    public final void start() {
        synchronized (lock) {
            if (state == State.STOPPED) {
                throw new IllegalStateException("gateway '" + name + "' already stopped, create a new instance");
            }
            if (state == State.CONNECTING || state == State.OPEN || state == State.RECONNECTING) {
                return;
            }
            state = State.CONNECTING;
            reconnectAttempts = 0;
            lastProblem = null;
        }
        // 首次建连不在调用线程同步执行：resolveEndpoint 可能做阻塞 HTTP（如 QQ /gateway/bot 拉 WS 地址），
        // 而 start() 常由服务器线程调用（/bot、/orzmc config reload → 平台槽 reconcile）——异步化避免主线程
        // 等网络（R12）；状态已置 CONNECTING，重复 start 仍幂等。
        scheduler.execute(this::runAttempt);
    }

    /** 幂等终止：关闭连接、取消心跳与待重连、关闭调度线程并等待终止（R13）。实例不可复用。 */
    public final void stop() {
        WebSocketClient toClose;
        synchronized (lock) {
            if (state == State.STOPPED) {
                return;
            }
            state = State.STOPPED;
            toClose = currentWs;
            currentWs = null;
            cancelHeartbeatLocked();
            cancelReconnectLocked();
        }
        closeQuietly(toClose, 1000, "shutdown");
        // 不 awaitTermination：调度线程是守护线程，shutdownNow 已中断/清队；在途任务由 state=STOPPED 自检收敛，
        // 在服务器线程等待会白白阻塞 1s（/orzmc config reload 逐平台停旧建新时叠加）。
        scheduler.shutdownNow();
    }

    public final State state() {
        synchronized (lock) {
            return state;
        }
    }

    /** 最近一次问题描述（断开/错误/fatal 原因），供状态展示（S8 status）。 */
    public final String lastProblem() {
        return lastProblem;
    }

    public final String name() {
        return name;
    }

    /**
     * 运行时诊断快照（issue #616；{@code /config im status} 与日志共用）。
     *
     * <p>计数为原子量、地址/次数为 volatile，只在拿 {@code lock} 的瞬间取状态机快照：本方法可能被服务器主线程调用，
     * 不应与重连/心跳路径争抢锁。</p>
     */
    public final GatewayDiagnostics diagnostics() {
        long lastInbound = lastReceivedMs;
        String snapshotState;
        int attempts;
        String problem;
        synchronized (lock) {
            snapshotState = state.name();
            attempts = reconnectAttempts;
            problem = lastProblem;
        }
        long silentMs = lastInbound <= 0 ? 0L : Math.max(0L, System.currentTimeMillis() - lastInbound);
        return new GatewayDiagnostics(
                snapshotState,
                remoteAddress,
                inboundFrames.get(),
                heartbeatSends.get(),
                silentMs,
                silenceLosses,
                sameAddressSilenceStreak,
                attempts,
                problem);
    }

    /**
     * 静默断流抬高退避的指数步数（issue #616）：0 = 不抬高，封顶 3（即最多把退避再往后推 3 个指数级）。
     *
     * <p>为何要抬高：连接往往能活过 {@code stableResetMs}（默认 20s）后又一次「零入站」静默断流，
     * 退避计数被反复清零 → 永远以 5s 的节奏重连、重鉴权，既掩盖真问题也浪费平台配额。</p>
     */
    private int silenceEscalationSteps() {
        return Math.min(3, Math.max(0, sameAddressSilenceStreak - 1));
    }

    // =====================================================================
    // 子类模板钩子
    // =====================================================================

    /**
     * 解析本次建连的网关地址。每次连接尝试（含自动重连）都会调用；返回 null / 空白 / 抛异常视为本次建连失败并退避重试。
     *
     * <p>注意在调度线程执行，实现应快速返回（平台 URL 刷新由子类在后台完成并缓存）。</p>
     */
    protected abstract String resolveEndpoint() throws Exception;

    /** WS 握手阶段附加 HTTP 头（如网关需要 token 入请求头）；默认无。 */
    protected Map<String, String> connectHeaders() {
        return Map.of();
    }

    /** 连接已建立（每次成功建连都会回调，含自动重连）：子类在此发送 identify 帧、根据 hello 配置心跳等。 */
    protected abstract void onGatewayOpen();

    /**
     * 生效网络代理（D13 批次 5b 起：Telegram/Discord 等出墙平台经 HTTP 代理 CONNECT 连 WS）。
     * 默认直连（NO_PROXY）；子类按 {@code ImProxyConfig} 覆写。仅 HTTP 代理（Java-WebSocket 1.6 CONNECT 隧道）。
     */
    protected Proxy proxy() {
        return Proxy.NO_PROXY;
    }

    /** 收到一条文本帧（opcode 分发由子类完成）。QQ 等文本协议实现。 */
    protected abstract void onGatewayPayload(String payload);

    /** 收到一条二进制帧（如飞书 protobuf 帧）。默认为空实现，二进制平台子类覆写。 */
    protected void onGatewayPayload(byte[] payload) {}

    /** 连接已关闭（含建连失败的 close）。子类可在此识别鉴权关闭码并调用 {@link #onAuthFailure()} / {@link #reconnectNow()}。 */
    protected void onGatewayClosed(int code, String reason, boolean remote) {}

    /** 网关进入 fatal（重连耗尽 / 鉴权不可恢复），自动重连已停止。 */
    protected void onGatewayFatal(String message, Throwable cause) {}

    /**
     * 子类判定“不可恢复”时主动进入 fatal（停止自动重连并上报）。
     *
     * <p>场景：平台明确不允许连接的关闭码（如 QQ 4914 已下架 / 4915 已封禁）或配置类错误
     * （无效 intents）——此时推退重连既无用又噪音。fatal 后需 {@code start()} 才会重启。</p>
     */
    protected final void failFatal(String message) {
        fatal(message);
    }

    // =====================================================================
    // 子类可用工具
    // =====================================================================

    /** 发送文本帧；未连接时丢弃并告警。 */
    protected final void send(String payload) {
        WebSocketClient ws = currentWs;
        if (ws != null && ws.isOpen()) {
            try {
                ws.send(payload);
            } catch (Exception e) {
                log.warning("[" + name + "] WS 发送失败: " + e);
            }
        } else {
            log.warning("[" + name + "] 未连接，丢弃发送: " + clip(payload, 120));
        }
    }

    /** 发送二进制帧（飞书 protobuf 等）；未连接时丢弃并告警。 */
    protected final void sendBytes(byte[] payload) {
        WebSocketClient ws = currentWs;
        if (ws != null && ws.isOpen()) {
            try {
                ws.send(java.nio.ByteBuffer.wrap(payload));
            } catch (Exception e) {
                log.warning("[" + name + "] WS 二进制发送失败: " + e);
            }
        } else {
            log.warning("[" + name + "] 未连接，丢弃二进制发送");
        }
    }

    protected final boolean isOpen() {
        WebSocketClient ws = currentWs;
        return ws != null && ws.isOpen();
    }

    /** 立即重连（跳过退避）：鉴权刷新成功或子类主动要求（如 QQ 会话失效需尽快恢复）。 */
    protected final void reconnectNow() {
        closeAndReconnect(true);
    }

    /**
     * 鉴权失败回调（子类识别到 401/鉴权关闭码后调用）：
     * 经 {@link TokenRefresher} 刷新 → REFRESHED 立即重连 / RETRY_LATER 退避重试 / DEAD fatal；未注入刷新器直接 fatal。
     */
    protected final void onAuthFailure() {
        if (tokenRefresher == null) {
            fatal("鉴权失败但未配置令牌刷新器，无法自愈");
            return;
        }
        RefreshOutcome outcome;
        try {
            outcome = tokenRefresher.refresh();
        } catch (Exception e) {
            log.warning("[" + name + "] 令牌刷新异常，按退避重试处理: " + e);
            outcome = RefreshOutcome.RETRY_LATER;
        }
        switch (outcome) {
            case REFRESHED -> {
                log.info("[" + name + "] 鉴权令牌已刷新，立即重连");
                reconnectNow();
            }
            case RETRY_LATER -> {
                log.warning("[" + name + "] 令牌暂不可刷新，按退避稍后重连");
                closeAndReconnect(false);
            }
            case DEAD -> fatal("鉴权失败且令牌不可恢复（配置错误），停止自动重连");
        }
    }

    /** 配置协议心跳（文本帧，QQ）：每 {@code intervalMs} 发送一帧；静默窗口默认 3 个周期（见 {@link #setSilenceTimeoutMs(long)}）。intervalMs &lt;= 0 视为关闭心跳。 */
    protected final void configureHeartbeat(long intervalMs, Supplier<String> payloadFactory) {
        if (intervalMs <= 0 || payloadFactory == null) {
            disableHeartbeat();
            return;
        }
        configureHeartbeatPump(intervalMs, ws -> {
            try {
                ws.send(payloadFactory.get());
            } catch (Exception e) {
                log.warning("[" + name + "] 心跳文本发送失败: " + e);
            }
        });
    }

    /** 配置协议心跳（二进制帧，飞书 protobuf ping）：语义同上（静默看门狗对文本/二进制/控制帧入站均生效）。 */
    protected final void configureHeartbeatBytes(long intervalMs, Supplier<byte[]> payloadFactory) {
        if (intervalMs <= 0 || payloadFactory == null) {
            disableHeartbeat();
            return;
        }
        configureHeartbeatPump(intervalMs, ws -> {
            try {
                ws.send(java.nio.ByteBuffer.wrap(payloadFactory.get()));
            } catch (Exception e) {
                log.warning("[" + name + "] 心跳二进制发送失败: " + e);
            }
        });
    }

    private void configureHeartbeatPump(long intervalMs, HeartbeatPump pump) {
        synchronized (lock) {
            if (state == State.STOPPED || state == State.FATAL) {
                return;
            }
            heartbeatIntervalMs = intervalMs;
            heartbeatPump = pump;
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
            }
            heartbeatFuture =
                    scheduler.scheduleAtFixedRate(this::heartbeatTick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    /** 关闭心跳（连接关闭时基类自动调用；子类一般无需手动）。 */
    protected final void disableHeartbeat() {
        synchronized (lock) {
            heartbeatIntervalMs = 0;
            heartbeatPump = null;
            cancelHeartbeatLocked();
        }
    }

    /**
     * 覆盖静默看门狗窗口（毫秒；&lt;= 0 = 恢复默认「心跳周期 × 3」）。
     *
     * <p>配置入口：im.yml {@code platforms.<id>.heartbeat_timeout_seconds}（由各 adapter 在构造网关时下发）。
     * 默认值取自平台协议的握手参数（QQ hello / Discord hello / 飞书引导的 ping_interval），平台协议不变时
     * 不需要改；只有跨中间设备（NAT/代理）会回收长连接、或对端回帧本身很慢的环境才需要放大。</p>
     */
    public final void setSilenceTimeoutMs(long timeoutMs) {
        this.silenceTimeoutMs = Math.max(0, timeoutMs);
    }

    /** 当前静默窗口（毫秒；心跳未配置时返回 0）。 */
    protected final long silenceWindowMs() {
        long intervalMs = heartbeatIntervalMs;
        if (intervalMs <= 0) {
            return 0;
        }
        long override = silenceTimeoutMs;
        return override > 0 ? override : intervalMs * SILENT_CYCLES;
    }

    // =====================================================================
    // 连接尝试 / 状态机
    // =====================================================================

    private void runAttempt() {
        synchronized (lock) {
            if (state != State.CONNECTING) {
                return; // stop/fatal 竞态
            }
        }
        String endpoint;
        try {
            endpoint = resolveEndpoint();
        } catch (Exception e) {
            log.warning("[" + name + "] 解析网关地址异常: " + e);
            endpoint = null;
        }
        if (endpoint == null || endpoint.isBlank()) {
            log.warning("[" + name + "] 网关地址不可用（resolveEndpoint 返回空），本次建连失败");
            transitionLost(null);
            queueReconnect(false);
            return;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            log.warning("[" + name + "] 非法网关地址: " + endpoint);
            transitionLost(null);
            queueReconnect(false);
            return;
        }
        WebSocketClient ws = createClient(uri);
        synchronized (lock) {
            if (state != State.CONNECTING) {
                closeQuietly(ws, 1000, "stale attempt");
                return;
            }
            currentWs = ws;
        }
        try {
            ws.connect();
        } catch (Exception e) {
            // 超时/握手失败后必须关掉底层 socket：connect() 抛异常时库不会自己收尾，不关会漏 fd
            // （有了 connect timeout 后这条路径会变常见）。
            closeQuietly(ws, 1000, "connect failed");
            log.warning("[" + name + "] 建连失败（将退避重连，超时窗口 " + connectTimeoutMs() + "ms）: " + e);
            transitionLost(ws);
            queueReconnect(false);
            return;
        }
        scheduleConnectWatchdog(ws, connectTimeoutMs());
    }

    /**
     * 建连看门狗（TCP 连上但 TLS/WS 握手停滞的兵库）。
     *
     * <p>Java-WebSocket 的 {@code connect()} 是**非阻塞**的：真正的握手在库自己的线程里跑，而库的
     * {@code connectTimeout} 只管 {@code socket.connect()}——TLS 握手/HTTP Upgrade 响应一旦被对端或
     * 中间设备丢包，那个线程就永久阻塞，**既不会 onOpen 也不会 onClose/onError**，状态机因此永远停在
     * CONNECTING：不重连、不发心跳、不打任何日志（2026-11-22 实测：4 分钟无日志、state=CONNECTING，
     * 见 issue #616）。这里自己补一个超时，超时即判失败并退避。</p>
     */
    private void scheduleConnectWatchdog(WebSocketClient ws, int timeoutMs) {
        if (timeoutMs <= 0) {
            return;
        }
        scheduler.schedule(
                () -> {
                    boolean stuck;
                    synchronized (lock) {
                        stuck = state == State.CONNECTING && currentWs == ws;
                    }
                    if (!stuck) {
                        return;
                    }
                    log.warning("[" + name + "] 建连超时（" + timeoutMs + "ms 内未完成 TCP/TLS/WS 握手，对端或中间设备不回包），按失败退避重连");
                    closeQuietly(ws, 1000, "connect timeout");
                    transitionLost(ws);
                    queueReconnect(false);
                },
                timeoutMs,
                TimeUnit.MILLISECONDS);
    }

    private WebSocketClient createClient(URI uri) {
        Map<String, String> headers = connectHeaders();
        if (headers == null) {
            headers = Map.of();
        }
        // 4 参构造：第 4 个 int = connect timeout（0 = 无限等待，绝不能给 0，见 DEFAULT_CONNECT_TIMEOUT_MS）
        WebSocketClient client = new WebSocketClient(uri, new Draft_6455(), headers, connectTimeoutMs()) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                ReconnectingGateway.this.onWsOpen(this);
            }

            @Override
            public void onMessage(String message) {
                markInbound();
                ReconnectingGateway.this.onGatewayPayload(message);
            }

            @Override
            public void onMessage(java.nio.ByteBuffer message) {
                markInbound();
                byte[] bytes = new byte[message.remaining()];
                message.get(bytes);
                ReconnectingGateway.this.onGatewayPayload(bytes);
            }

            /**
             * WS 控制帧 ping 也算「连接活着」（issue #610）：空闲期平台侧可能只发控制帧做保活，
             * 不计数就会在空闲期被误判为静默死亡。默认实现会回 pong，必须 super 转发（否则对端会判定我们失联）。
             */
            @Override
            public void onWebsocketPing(org.java_websocket.WebSocket conn, org.java_websocket.framing.Framedata frame) {
                markInbound();
                super.onWebsocketPing(conn, frame);
            }

            /** WS 控制帧 pong：对端对本地 ping 的应答，同样是活性证据（协议心跳之外的第二种判活依据）。 */
            @Override
            public void onWebsocketPong(org.java_websocket.WebSocket conn, org.java_websocket.framing.Framedata frame) {
                markInbound();
                super.onWebsocketPong(conn, frame);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                ReconnectingGateway.this.onWsClose(this, code, reason, remote);
            }

            @Override
            public void onError(Exception ex) {
                ReconnectingGateway.this.onWsError(this, ex);
            }
        };
        // TCP 层 ping 关闭：本类以协议心跳（应用层）为准，避免与测试服务端/平台网关冲突
        client.setConnectionLostTimeout(0);
        // D13：出墙平台（Telegram/Discord）WS 经 HTTP 代理 CONNECT 隧道（默认 NO_PROXY 直连）
        Proxy effectiveProxy = proxy();
        if (effectiveProxy != null && effectiveProxy != Proxy.NO_PROXY) {
            client.setProxy(effectiveProxy);
        }
        return client;
    }

    /** 记录一次入站活性（文本/二进制帧、WS ping/pong 控制帧）——看门狗与诊断计数的唯一入口。 */
    private void markInbound() {
        lastReceivedMs = System.currentTimeMillis();
        inboundFrames.incrementAndGet();
    }

    /** 测试可见：本轮连接累计入站活性数。 */
    long inboundFrameCount() {
        return inboundFrames.get();
    }

    /**
     * 建连（TCP + TLS + WS 握手）超时（毫秒）。默认 {@link #DEFAULT_CONNECT_TIMEOUT_MS}；测试可覆盖。
     *
     * <p>无论环境如何，这里**不允许返回 0**：0 在 Java-WebSocket 里等于无限等待，会让网关在握手黑洞上悬挂。</p>
     */
    protected int connectTimeoutMs() {
        return DEFAULT_CONNECT_TIMEOUT_MS;
    }

    private void onWsOpen(WebSocketClient ws) {
        String addr = describeRemote(ws);
        synchronized (lock) {
            if (state == State.CONNECTING && currentWs == ws) {
                state = State.OPEN;
                lastReceivedMs = System.currentTimeMillis();
                inboundFrames.set(0);
                heartbeatSends.set(0);
                if (addr != null) {
                    remoteAddress = addr;
                }
                scheduleStableResetLocked();
            } else {
                // stop/fatal/过期尝试的迟到 open：立即关闭
                closeQuietly(ws, 1000, "stale open");
                return;
            }
        }
        // 建连成功属例行低噪（QQ 服务端每 ~30min 下发 op7 轮换会周期性触发）；
        // 失败/异常另有 warning/断开日志可见。诊断时可调日志级别打开。
        log.fine("[" + name + "] 网关连接已建立");
        if (listener != null) {
            listener.onConnected();
        }
        onGatewayOpen();
    }

    private void onWsClose(WebSocketClient ws, int code, String reason, boolean remote) {
        boolean wasOpen;
        boolean handled;
        synchronized (lock) {
            wasOpen = state == State.OPEN;
        }
        handled = transitionLost(ws);
        if (!handled) {
            return; // stop/fatal/已处理（重复回调或过期实例）
        }
        lastProblem = "断开 code=" + code + " reason=" + reason;
        if (wasOpen) {
            // 主动关闭（op7 轮换/退避/stale/stop 等 closeQuietly 均 code=1000 且 remote=false）→ FINE，
            // 避免每 30min QQ 轮换刷屏；意外断开（网络/服务端 close，code≠1000 或 remote）保留 INFO。
            if (code == 1000 && !remote) {
                log.fine("[" + name + "] 主动断开（将自动重连）: code=" + code + ", reason=" + reason);
            } else {
                log.info("[" + name + "] 连接断开（将自动重连）: code=" + code + ", reason=" + reason);
            }
            if (listener != null) {
                listener.onDisconnected(code, reason);
            }
        } else {
            log.info("[" + name + "] 建连失败/关闭: code=" + code + ", reason=" + reason);
        }
        try {
            onGatewayClosed(code, reason, remote);
        } catch (RuntimeException e) {
            log.warning("[" + name + "] onGatewayClosed 钩子异常: " + e);
        }
        queueAutoReconnect();
    }

    private void onWsError(WebSocketClient ws, Exception ex) {
        log.warning("[" + name + "] WS 错误: " + ex);
        lastProblem = String.valueOf(ex);
        synchronized (lock) {
            // 已建立连接的异常：强制关闭走 onClose 统一生命周期（避免仅 onError 无 onClose 时卡在 OPEN）
            if (state == State.OPEN && currentWs == ws) {
                closeQuietly(ws, 1006, "ws error");
                return;
            }
            // CONNECTING 中的失败：Java-WebSocket 通常跟随 onClose（code=-1/1006）由 onClose 收敛，避免双重计数；
            // 兜底：若 500ms 后仍无 onClose（个别环境只报 onError），视为本次建连失败并退避
            if (state == State.CONNECTING && currentWs == ws) {
                scheduler.schedule(
                        () -> {
                            boolean lost;
                            synchronized (lock) {
                                lost = state == State.CONNECTING && currentWs == ws && !ws.isOpen();
                            }
                            if (lost) {
                                transitionLost(ws);
                                queueAutoReconnect();
                            }
                        },
                        500,
                        TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 标记连接丢失/失败 → RECONNECTING 并清理。返回 false 表示无需处理（stop/fatal/重复回调/过期实例）。
     */
    private boolean transitionLost(WebSocketClient ws) {
        synchronized (lock) {
            if (state == State.STOPPED || state == State.FATAL) {
                return false;
            }
            if (state == State.OPEN || (state == State.CONNECTING && currentWs == ws)) {
                state = State.RECONNECTING;
                currentWs = null;
                cancelHeartbeatLocked();
                return true;
            }
            return false; // RECONNECTING（已处理）或 CONNECTING 但非该实例
        }
    }

    /** 断线后的自动重连（含子类钩子内未接管的情形）；读取 immediateNext 决定是否跳过退避。 */
    private void queueAutoReconnect() {
        boolean immediate;
        synchronized (lock) {
            immediate = immediateNext;
            immediateNext = false;
        }
        queueReconnect(immediate);
    }

    private void queueReconnect(boolean immediate) {
        QueueResult result;
        synchronized (lock) {
            result = queueReconnectLocked(immediate);
        }
        if (result == QueueResult.FATAL) {
            fatal("连续重连失败已达上限（" + policy.maxConsecutiveFailures() + " 次），停止自动重连");
        }
    }

    private QueueResult queueReconnectLocked(boolean immediate) {
        if (state == State.STOPPED || state == State.FATAL) {
            return QueueResult.IGNORED;
        }
        if (state == State.OPEN || (state == State.CONNECTING && currentWs != null)) {
            return QueueResult.IGNORED; // 存活或尝试中：由各自路径接管
        }
        if (immediate) {
            if (state == State.CONNECTING && reconnectFuture != null) {
                reconnectFuture.cancel(false);
                reconnectFuture = null;
            }
            reconnectAttempts = 0;
            scheduleAttemptLocked(0);
            state = State.CONNECTING;
            return QueueResult.QUEUED;
        }
        if (state == State.CONNECTING && reconnectFuture != null) {
            return QueueResult.IGNORED; // 已有退避重连排定
        }
        int cap = policy.maxConsecutiveFailures();
        if (cap > 0 && reconnectAttempts >= cap) {
            return QueueResult.FATAL;
        }
        reconnectAttempts++;
        long delay = backoffDelayMs(reconnectAttempts);
        log.info("[" + name + "] 第 " + reconnectAttempts + " 次重连将在 " + delay + "ms 后进行");
        scheduleAttemptLocked(delay);
        state = State.CONNECTING;
        return QueueResult.QUEUED;
    }

    private void scheduleAttemptLocked(long delayMs) {
        reconnectFuture = scheduler.schedule(
                () -> {
                    synchronized (lock) {
                        if (state != State.CONNECTING) {
                            return;
                        }
                        reconnectFuture = null;
                    }
                    runAttempt();
                },
                delayMs,
                TimeUnit.MILLISECONDS);
    }

    /** 立即重连（跳过退避）；当前 OPEN 时先断开让 onClose 统一排队。 */
    private void closeAndReconnect(boolean immediate) {
        WebSocketClient toClose;
        synchronized (lock) {
            if (state == State.STOPPED || state == State.FATAL || state == State.IDLE) {
                return; // 未启动/已终止：无事可做
            }
            if (state == State.CONNECTING && currentWs != null) {
                return; // 尝试中：鉴权失败会由服务端 close 触发，无需主动干预
            }
            if (state == State.OPEN) {
                immediateNext = immediate;
                toClose = currentWs;
            } else {
                toClose = null;
            }
        }
        if (toClose != null) {
            closeQuietly(toClose, 1000, immediate ? "reconnect now" : "retry later");
            return; // 由 onClose 统一排队
        }
        queueReconnect(immediate);
    }

    // =====================================================================
    // fatal / 心跳
    // =====================================================================

    private void fatal(String message) {
        WebSocketClient toClose;
        synchronized (lock) {
            if (state == State.STOPPED) {
                return;
            }
            state = State.FATAL;
            lastProblem = message;
            toClose = currentWs;
            currentWs = null;
            cancelHeartbeatLocked();
            cancelReconnectLocked();
        }
        log.severe("[" + name + "] " + message);
        closeQuietly(toClose, 1000, "fatal");
        if (listener != null) {
            listener.onFatal(message, null);
        }
        onGatewayFatal(message, null);
    }

    private void heartbeatTick() {
        WebSocketClient ws;
        long intervalMs;
        HeartbeatPump pump;
        synchronized (lock) {
            ws = currentWs;
            intervalMs = heartbeatIntervalMs;
            pump = heartbeatPump;
        }
        if (ws == null || !ws.isOpen() || intervalMs <= 0 || pump == null) {
            return;
        }
        pump.send(ws);
        heartbeatSends.incrementAndGet();
        long silentMs = System.currentTimeMillis() - lastReceivedMs;
        // 零入站提前判决：连上以后一帧都没收到，且已经发够 N 次心跳 → 不等满窗口，直接判失败重连
        if (inboundFrames.get() == 0L && heartbeatSends.get() >= zeroInboundHeartbeatLimit()) {
            onZeroInboundTimeout(ws, silentMs);
            return;
        }
        long windowMs = silenceWindowMs();
        if (windowMs > 0 && silentMs > windowMs) {
            onSilenceTimeout(ws, silentMs, windowMs);
        }
    }

    /**
     * 静默超时 → 强制断开触发重连。
     *
     * <p><b>日志口径（issue #610）</b>：旧日志只打「窗口」却声称「N ms 无任何入站帧」，把阈值误读成了实测静默
     * 时长，无法判断是「对端不回帧」还是「本地定时任务被拖住」。现在同时打印实测静默时长、窗口、本轮已发心跳
     * 帧数与入站活性数——两个数字一对就能定性：已发 &gt; 0 且入站为 0 = 出站正常、对端/链路不回；入站 &gt; 0 但
     * 静默时长已超窗 = 定时任务被长任务拖住（属调度问题）。</p>
     *
     * <p><b>同一地址反复静默（issue #616）</b>：本轮连接<b>一帧都没收到</b>、且对端地址与上次静默断流相同 → 计入连续次数；
     * 达到 {@link #SAME_ADDRESS_SILENCE_HINT} 时额外给一条带排查建议的 WARN，并通过
     * {@link #silenceEscalationSteps()} 抬高后续退避（不再以 5s 的节奏反复重鉴权）。</p>
     *
     * <p><b>噪声控制</b>：同一段不稳定期内首条 WARN，后续降 INFO；连接稳定<b>且真正收到过入站帧</b>后由
     * {@link #scheduleStableResetLocked()} 清零并打一行「结束静默重连期」。</p>
     */
    private void onSilenceTimeout(WebSocketClient ws, long silentMs, long windowMs) {
        String detail = "静默 " + silentMs + "ms（窗口 " + windowMs + "ms，本轮已发心跳 " + heartbeatSends.get() + " 帧，累计入站 "
                + inboundFrames.get() + " 帧" + remoteSuffix(ws) + "）";
        recordSilenceLoss(ws, "心跳静默超时，" + detail, "heartbeat silence");
    }

    /**
     * 零入站提前失败（issue #616）：连上、心跳发得出去、但一帧都没收到 → 不等满静默窗口就重连。
     *
     * <p>实测依据：2026-11-23 通宵 55 次飞书静默中 30 次是「累计入站 0 帧」；这类连接不会有任何好转，
     * 等满 270s 只是把「群里发命令没人回」的时间拉长。</p>
     */
    private void onZeroInboundTimeout(WebSocketClient ws, long silentMs) {
        String detail = "已发心跳 " + heartbeatSends.get() + " 帧但仍零入站（静默 " + silentMs + "ms" + remoteSuffix(ws) + "）";
        recordSilenceLoss(ws, "连接后零入站，提前判失败（" + detail + "）", "zero-inbound");
    }

    /** 对端地址后缀（无地址时为空串），日志用。 */
    private static String remoteSuffix(WebSocketClient ws) {
        String addr = describeRemote(ws);
        return addr == null ? "" : "，远端 " + addr;
    }

    /**
     * 静默类失败的统一记账：累计次数、按对端地址归并「同址静默」、分级日志、达阈值给排查提示，最后断开重连。
     *
     * @param summary 事件摘要（调用方给出「静默 …ms / 零入站 …」等口径)
     * @param closeReason close 帧 reason（便于日志区分是窗口超时还是零入站提前判失败）
     */
    private void recordSilenceLoss(WebSocketClient ws, String summary, String closeReason) {
        int losses;
        int streak;
        boolean hintNow;
        String addr = describeRemote(ws);
        synchronized (lock) {
            losses = ++silenceLosses;
            // 按对端地址归并「静默断流」：**与收到过几帧无关**。
            // 2026-11-23 实测（dev.439，issue #616）：同一远端 111.161.67.196 连续两次静默，
            // 分别是「已发 48 / 入站 46」和「已发 5 / 入站 3」——收到过帧之后照样会冻结，
            // 早期版本只统计「零入站」的连接，结果把这类最典型的模式漏掉了。
            if (addr == null ? silentStreakAddress == null : addr.equals(silentStreakAddress)) {
                sameAddressSilenceStreak++;
            } else {
                silentStreakAddress = addr;
                sameAddressSilenceStreak = 1;
            }
            streak = sameAddressSilenceStreak;
            hintNow = streak == SAME_ADDRESS_SILENCE_HINT;
        }
        String message = summary + "，强制断开触发重连";
        if (losses > 1) {
            message += "（本段第 " + losses + " 次）";
        }
        if (streak > 1) {
            message += "（同一远端第 " + streak + " 次静默）";
        }
        if (losses <= SILENCE_WARN_TIMES) {
            log.warning("[" + name + "] " + message);
        } else {
            log.info("[" + name + "] " + message);
        }
        if (hintNow) {
            log.warning("[" + name + "] 连续 " + streak + " 次在远端 " + addr
                    + " 上反复静默断流：多为本机出网路径（NAT / HTTP 代理 / 无线链路 / 中间设备）对该长连接端点不友好，"
                    + "短连接（HTTPS API）通常仍正常。排查顺序见 docs/dev/im-inbound-stall.md；"
                    + "先试 im.yml platforms.<id>.proxy、改用有线，或把实例改成 network: host 对照。");
        }
        closeQuietly(ws, 1006, closeReason);
    }

    /** 零入站提前判决的心跳数阈值；测试可覆盖。 */
    protected int zeroInboundHeartbeatLimit() {
        return ZERO_INBOUND_HEARTBEAT_LIMIT;
    }

    private void scheduleStableResetLocked() {
        long stable = policy.stableResetMs();
        if (stable <= 0) {
            reconnectAttempts = 0;
            return;
        }
        scheduler.schedule(
                () -> {
                    int cleared;
                    boolean healthy;
                    synchronized (lock) {
                        cleared = 0;
                        healthy = false;
                        if (state == State.OPEN && currentWs != null && currentWs.isOpen()) {
                            reconnectAttempts = 0;
                            // 关键区分（issue #616）：稳定不等于健康。连上以后一帧入站都没收到，
                            // 正是「入站断流」的前奏，此时清零就等于把同一地址的可疑模式忘掉。
                            healthy = inboundFrames.get() > 0;
                            if (healthy) {
                                cleared = silenceLosses;
                                silenceLosses = 0;
                                sameAddressSilenceStreak = 0;
                                silentStreakAddress = null;
                            }
                        }
                    }
                    if (cleared > 0) {
                        // issue #610 期望 3：状态恢复要给一行 INFO（此前只有 FINE，运维看不出已经好了）
                        log.info("[" + name + "] 连接已稳定 " + stable + "ms，结束静默重连期（本轮累计 " + cleared + " 次静默重连）");
                    } else if (!healthy) {
                        log.fine("[" + name + "] 连接稳定 " + stable + "ms 但本轮入站 0 帧（入站断流前兆，保留静默计数）");
                    }
                },
                stable,
                TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeatLocked() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    private void cancelReconnectLocked() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    // =====================================================================
    // 工具
    // =====================================================================

    private long backoffDelayMs(int attempt) {
        // issue #616：同一远端反复「零入站」静默断流时抬高退避指数，避免 5s 一轮的无效重鉴权
        int escalated = attempt + silenceEscalationSteps();
        double base = policy.baseRetryMs() * Math.pow(2, Math.max(0, escalated - 1));
        long capped = (long) Math.min(base, policy.maxRetryMs());
        int jitter = Math.min(100, Math.max(0, policy.jitterPercent()));
        double factor = 1.0 + ((ThreadLocalRandom.current().nextDouble() * 2 - 1) * (jitter / 100.0));
        return Math.max(0, (long) (capped * factor));
    }

    /** 对端地址描述（{@code host:port}；未连上/库未提供时返回 null）。 */
    private static String describeRemote(WebSocketClient ws) {
        if (ws == null) {
            return null;
        }
        try {
            java.net.InetSocketAddress addr = ws.getRemoteSocketAddress();
            if (addr == null) {
                return null;
            }
            String host = addr.getAddress() == null
                    ? String.valueOf(addr.getHostString())
                    : addr.getAddress().getHostAddress();
            return host + ":" + addr.getPort();
        } catch (RuntimeException e) {
            return null; // 诊断信息取不到不应影响重连主流程
        }
    }

    private static void closeQuietly(WebSocketClient ws, int code, String reason) {
        if (ws == null) {
            return;
        }
        try {
            ws.closeConnection(code, reason);
        } catch (RuntimeException ignored) {
            // 关闭失败无碍：连接已死或正在关闭
        }
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
