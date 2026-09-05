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
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * 统一连接生命周期基类（builtin IM 骨架，方案 §4.2 / R12 / R13）。
 *
 * <p>基于 Java-WebSocket 封装各平台出站网关 WS 的公共生命周期：</p>
 * <ul>
 *   <li><b>指数退避自动重连</b>：5s 起 / 60s 上限 / ±jitter / 稳定 20s 重置（参数经 {@link ReconnectPolicy} 注入，
 *       默认值对齐 EasyBot RobustWebSocketClient）；连续失败达上限（策略配置）或鉴权不可恢复 → fatal 终止；</li>
 *   <li><b>协议心跳钩子</b>：子类在收到 hello 等握手帧后调用 {@link #configureHeartbeat(long, Supplier)}，
 *       基类按周期发送帧，并做静默看门狗（连续 3 个心跳周期无任何入站帧 → 强制断开触发重连）；</li>
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

    /** 静默看门狗阈值：连续多少个心跳周期无任何入站帧即判定连接死亡。 */
    private static final long SILENT_CYCLES = 3;

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
        runAttempt();
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
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    /** 配置协议心跳（文本帧，QQ）：每 {@code intervalMs} 发送一帧；连续 3 个周期无任何入站帧则强制断开触发重连。intervalMs &lt;= 0 视为关闭心跳。 */
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

    /** 配置协议心跳（二进制帧，飞书 protobuf ping）：语义同上（静默看门狗对二进制入站同样生效）。 */
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
            log.warning("[" + name + "] 建连异常（将退避重连）: " + e);
            transitionLost(ws);
            queueReconnect(false);
        }
    }

    private WebSocketClient createClient(URI uri) {
        Map<String, String> headers = connectHeaders();
        if (headers == null) {
            headers = Map.of();
        }
        WebSocketClient client = new WebSocketClient(uri, headers) {
            @Override
            public void onOpen(ServerHandshake handshakeData) {
                ReconnectingGateway.this.onWsOpen(this);
            }

            @Override
            public void onMessage(String message) {
                lastReceivedMs = System.currentTimeMillis();
                ReconnectingGateway.this.onGatewayPayload(message);
            }

            @Override
            public void onMessage(java.nio.ByteBuffer message) {
                lastReceivedMs = System.currentTimeMillis();
                byte[] bytes = new byte[message.remaining()];
                message.get(bytes);
                ReconnectingGateway.this.onGatewayPayload(bytes);
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

    private void onWsOpen(WebSocketClient ws) {
        synchronized (lock) {
            if (state == State.CONNECTING && currentWs == ws) {
                state = State.OPEN;
                lastReceivedMs = System.currentTimeMillis();
                scheduleStableResetLocked();
            } else {
                // stop/fatal/过期尝试的迟到 open：立即关闭
                closeQuietly(ws, 1000, "stale open");
                return;
            }
        }
        log.info("[" + name + "] 网关连接已建立");
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
            log.info("[" + name + "] 连接断开（将自动重连）: code=" + code + ", reason=" + reason);
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
        long silentMs = intervalMs * SILENT_CYCLES;
        if (System.currentTimeMillis() - lastReceivedMs > silentMs) {
            log.warning("[" + name + "] 心跳静默超时（" + silentMs + "ms 无任何入站帧），强制断开触发重连");
            closeQuietly(ws, 1006, "heartbeat silence");
        }
    }

    private void scheduleStableResetLocked() {
        long stable = policy.stableResetMs();
        if (stable <= 0) {
            reconnectAttempts = 0;
            return;
        }
        scheduler.schedule(
                () -> {
                    synchronized (lock) {
                        if (state == State.OPEN && currentWs != null && currentWs.isOpen()) {
                            reconnectAttempts = 0;
                        }
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
        double base = policy.baseRetryMs() * Math.pow(2, Math.max(0, attempt - 1));
        long capped = (long) Math.min(base, policy.maxRetryMs());
        int jitter = Math.min(100, Math.max(0, policy.jitterPercent()));
        double factor = 1.0 + ((ThreadLocalRandom.current().nextDouble() * 2 - 1) * (jitter / 100.0));
        return Math.max(0, (long) (capped * factor));
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
