package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGateway;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.TokenRefresher;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * QQ 出站网关 WS 客户端（builtin QQ adapter 上行，协议参照 EasyBot easybot-adapter-qq gateway.rs）。
 *
 * <p>复用 {@link ReconnectingGateway} 统一连接生命周期（指数退避重连/心跳看门狗/状态回调/stop），本类只实现
 * QQ 协议语义：</p>
 * <ul>
 *   <li><b>identify/resume 决策</b>：每次建连后等服务端 hello（op10）再出手——有会话（READY 的 session_id）且
 *       已收过事件（seq &gt; 0）→ op6 resume 续传；否则 op2 identify（token + intents=1&lt;&lt;25 + shard[0,1]）；</li>
 *   <li><b>心跳</b>：hello 携带 heartbeat_interval，按 EasyBot 的 0.75 安全系数配置周期发送 op1（d=最新 seq）；</li>
 *   <li><b>事件分发</b>：op0 的 READY 捕获 session_id、RESUMED 标记恢复成功，其余事件类型透传 {@link QqEventSink}
 *       （事件归一/线程调度归 S6，本类只透传）；op7 会话失效 → 立即重连（保留 session 尝试 resume）；op9 无效会话
 *       → 清除 session 后立即重连（全量 re-identify）；op11 心跳回执仅喂活（基类看门狗已统计入站帧）；</li>
 *   <li><b>鉴权错误</b>：服务端关闭码 4004（token 无效）→ {@link #onAuthFailure()}（绑定
 *       {@code tokenProvider.onAuthFailure()}，失败映射 RETRY_LATER 防风暴，对齐方案 §4.2）。</li>
 * </ul>
 *
 * <p>每次连接尝试（含自动重连）经 {@link QqGatewayUrlFetcher} 重新解析网关地址（token 用 {@link TokenProvider#fresh()}
 * 自动预刷新）；地址解析被拒（AUTH）→ 强制重换令牌一次后按退避重试。</p>
 *
 * <p><b>线程纪律（R12）</b>：本类全部逻辑在 WS 读线程 / 网关调度线程（{@code im-qq-gw}）执行，事件经
 * {@link QqEventSink} 回调——实现方不得触碰 Bukkit API（入站调度 S6）；{@link #stop()} 幂等清理（R13）。</p>
 */
public final class QqGatewayClient extends ReconnectingGateway {

    /** QQ 群 + C2C 事件 intent（官方 GROUP_AND_C2C_EVENT = 1 &lt;&lt; 25）。 */
    public static final int INTENT_GROUP_AND_C2C = 1 << 25;

    /** 鉴权失败关闭码：identify 后 token 无效。 */
    private static final int CLOSE_AUTH_FAILED = 4004;

    /** 心跳安全系数：按 hello 间隔的 0.75 发送（对齐 EasyBot，留出网络抖动余量，防止看门狗误杀）。 */
    private static final double HEARTBEAT_SAFETY = 0.75;

    /** 网关 URL 缓存窗口：/gateway/bot 有频率限制（实测 HTTP 400 code 100017），窗口内复用不重取。 */
    private static final long GATEWAY_URL_CACHE_MS = 60_000;
    /** op9 无效会话防抖：距上次处理不足该时长仅清 session，不立即重连（防重连风暴触发平台限频）。 */
    private static final long OP9_MIN_INTERVAL_MS = 15_000;

    private final TokenProvider tokens;
    private final QqGatewayUrlFetcher urlFetcher;
    private final int intents;
    private final QqEventSink sink;
    private final java.net.Proxy proxy;
    private final Logger log;

    /** 客户端最新事件序号（随每个带 s 的入站帧推进），用于心跳 d 与 resume seq。 */
    private final AtomicLong seq = new AtomicLong();
    /** READY 下发的会话 id：具备 + seq>0 时重连走 resume。 */
    private volatile String sessionId;
    /** 最近成功网关 URL（缓存，限频保护）。 */
    private volatile String cachedGatewayUrl;

    private volatile long cachedGatewayUrlMs;
    /** 最近一次 op9 处理时间（防抖）。 */
    private volatile long lastOp9HandledMs;

    /**
     * @param server 服务端日志门面
     * @param policy 重连退避策略（null → 骨架默认 5s 起/60s 上限）
     * @param tokens QQ access_token 提供者（通常为 RefreshableTokenProvider，fetch=QqApiClient::fetchAccessToken）
     * @param urlFetcher 网关 WS 地址解析器（生产为 {@link QqApiClient}）
     * @param sink 入站事件回调（可为 null：仅连接不上报业务事件）
     * @param listener 连接状态观察者（可为 null；健康聚合 S7 注入）
     */
    public QqGatewayClient(
            ServerLogger server,
            ReconnectPolicy policy,
            TokenProvider tokens,
            QqGatewayUrlFetcher urlFetcher,
            QqEventSink sink,
            GatewayStateListener listener) {
        this(server, policy, tokens, urlFetcher, INTENT_GROUP_AND_C2C, sink, listener, java.net.Proxy.NO_PROXY);
    }

    public QqGatewayClient(
            ServerLogger server,
            ReconnectPolicy policy,
            TokenProvider tokens,
            QqGatewayUrlFetcher urlFetcher,
            int intents,
            QqEventSink sink,
            GatewayStateListener listener) {
        this(server, policy, tokens, urlFetcher, intents, sink, listener, java.net.Proxy.NO_PROXY);
    }

    public QqGatewayClient(
            ServerLogger server,
            ReconnectPolicy policy,
            TokenProvider tokens,
            QqGatewayUrlFetcher urlFetcher,
            int intents,
            QqEventSink sink,
            GatewayStateListener listener,
            java.net.Proxy proxy) {
        super("qq", server, policy, refresherFor(tokens), listener);
        if (tokens == null) {
            throw new IllegalArgumentException("tokens must not be null");
        }
        if (urlFetcher == null) {
            throw new IllegalArgumentException("urlFetcher must not be null");
        }
        if (intents <= 0) {
            throw new IllegalArgumentException("intents must be > 0");
        }
        this.tokens = tokens;
        this.urlFetcher = urlFetcher;
        this.intents = intents;
        this.sink = sink;
        this.proxy = proxy == null ? java.net.Proxy.NO_PROXY : proxy;
        this.log = server.logger();
    }

    /** 鉴权失败回调绑定：token 换发成功 → REFRESHED（立即重连）；换发失败 → RETRY_LATER（退避，防风暴）。 */
    private static TokenRefresher refresherFor(TokenProvider tokens) {
        return () -> tokens.onAuthFailure() != null
                ? TokenRefresher.RefreshOutcome.REFRESHED
                : TokenRefresher.RefreshOutcome.RETRY_LATER;
    }

    // =====================================================================
    // ReconnectingGateway 模板钩子
    // =====================================================================

    @Override
    protected String resolveEndpoint() {
        String token = tokens.fresh();
        if (token == null) {
            log.warning("[qq] 无可用 access_token，本次建连失败（将退避重试）");
            return null;
        }
        // /gateway/bot 限频保护（实测 HTTP 400 code 100017）：窗口内复用最近 URL，不重复请求
        long now = System.currentTimeMillis();
        String cached = cachedGatewayUrl;
        if (cached != null && now - cachedGatewayUrlMs < GATEWAY_URL_CACHE_MS) {
            return cached;
        }
        QqGatewayUrlFetcher.Result result = urlFetcher.fetch(token);
        return switch (result.status()) {
            case SUCCESS -> {
                cachedGatewayUrl = result.url();
                cachedGatewayUrlMs = now;
                yield result.url();
            }
            case AUTH -> {
                // token 被平台提前作废：强制重换一次，下次尝试用新 token（换发失败也按退避，防风暴）
                log.warning("[qq] 网关地址鉴权被拒，强制重换令牌后重试");
                tokens.onAuthFailure();
                yield null;
            }
            case TRANSIENT -> null;
        };
    }

    @Override
    protected void onGatewayOpen() {
        // QQ 建连后由服务端先发 hello（op10）；identify/resume 与心跳配置在 onGatewayPayload 中随 hello 完成
    }

    /** 生效网络代理（D13：海外服务器访问 QQ 网关 WS 经 HTTP 代理 CONNECT；默认直连）。 */
    @Override
    protected java.net.Proxy proxy() {
        return proxy;
    }

    @Override
    protected void onGatewayPayload(String payload) {
        JsonObject root;
        try {
            root = JsonParser.parseString(payload).getAsJsonObject();
        } catch (RuntimeException e) {
            log.warning("[qq] 无法解析网关帧，忽略: " + clip(payload));
            return;
        }
        if (!root.has("op") || !root.get("op").isJsonPrimitive()) {
            return;
        }
        JsonElement s = root.get("s");
        if (s != null && !s.isJsonNull() && s.isJsonPrimitive()) {
            seq.set(s.getAsLong());
        }
        switch (root.get("op").getAsInt()) {
            case 10 -> onHello(root);
            case 0 -> dispatch(root, payload);
            case 7 -> {
                // 服务端要求重连（会话可续）：立即重连，下次尝试走 resume（保留 session_id/seq）
                log.info("[qq] 收到 op7（会话失效重连请求），立即重连并尝试 resume");
                reconnectNow();
            }
            case 9 -> {
                // resume 失败 / 会话无效：清除会话；防抖后立即重连（全量 re-identify）——
                // 若被平台限频/连续拒绝，过快重连会触发网关 URL 频率限制（实测 HTTP 400 code 100017）
                sessionId = null;
                long now = System.currentTimeMillis();
                if (now - lastOp9HandledMs >= OP9_MIN_INTERVAL_MS) {
                    lastOp9HandledMs = now;
                    log.info("[qq] 收到 op9（无效会话），清除 session 后重连并全量 re-identify");
                    reconnectNow();
                } else {
                    log.warning("[qq] 收到 op9 但距上次处理过近，跳过立即重连（等待连接关闭/退避）");
                }
            }
            default -> {
                // op1/op6/op11 等本端发起的帧/心跳回执：无需动作（基类已按入站帧喂活看门狗）
            }
        }
    }

    @Override
    protected void onGatewayClosed(int code, String reason, boolean remote) {
        if (code == CLOSE_AUTH_FAILED) {
            log.warning("[qq] 网关鉴权失败关闭（code=4004），触发令牌刷新重连");
            onAuthFailure();
        }
        // 其余关闭码（网络断/超时等）由基类统一退避自动重连
    }

    @Override
    protected void onGatewayFatal(String message, Throwable cause) {
        log.severe("[qq] 网关不可恢复终止: " + message);
    }

    // =====================================================================
    // QQ 协议处理
    // =====================================================================

    /** hello（op10）：按服务端心跳间隔配置心跳，随后 identify/resume。 */
    private void onHello(JsonObject root) {
        long intervalMs = 0;
        if (root.has("d") && root.get("d").isJsonObject()) {
            JsonObject d = root.getAsJsonObject("d");
            if (d.has("heartbeat_interval") && d.get("heartbeat_interval").isJsonPrimitive()) {
                intervalMs = d.get("heartbeat_interval").getAsLong();
            }
        }
        if (intervalMs <= 0) {
            log.warning("[qq] hello 缺少合法 heartbeat_interval，无法配置心跳（依赖静默看门狗兜底）");
        } else {
            configureHeartbeat((long) (intervalMs * HEARTBEAT_SAFETY), this::heartbeatFrame);
        }
        sendIdentifyOrResume();
    }

    /** 心跳帧（op1，d=最新事件序号；对齐 EasyBot 心跳载荷）。 */
    private String heartbeatFrame() {
        JsonObject frame = new JsonObject();
        frame.addProperty("op", 1);
        frame.addProperty("d", seq.get());
        return frame.toString();
    }

    /**
     * resume（op6）或 identify（op2）决策：有会话且收过事件 → resume 续传；否则全量 identify
     * （对齐 EasyBot：resume 失败由服务端 op9/关闭码收敛，届时转全量 identify）。
     */
    private void sendIdentifyOrResume() {
        String token = tokens.fresh();
        if (token == null) {
            log.warning("[qq] 发送 identify/resume 前无可用 token（将随下次重连重试）");
            return;
        }
        long currentSeq = seq.get();
        String sid = sessionId;
        boolean canResume = sid != null && !sid.isEmpty() && currentSeq > 0;
        JsonObject frame = new JsonObject();
        // QQ 网关 identify/resume 的 token 字段为鉴权串（QQBot <access_token>，EasyBot 实机验证；裸 token 会被 op9 拒）
        String gatewayToken = "QQBot " + token;
        if (canResume) {
            JsonObject d = new JsonObject();
            d.addProperty("token", gatewayToken);
            d.addProperty("session_id", sid);
            d.addProperty("seq", currentSeq);
            frame.addProperty("op", 6);
            frame.add("d", d);
            log.info("[qq] 发送 resume（session 续传）");
        } else {
            JsonObject d = new JsonObject();
            d.addProperty("token", gatewayToken);
            d.addProperty("intents", intents);
            JsonArray shard = new JsonArray();
            shard.add(0);
            shard.add(1);
            d.add("shard", shard);
            frame.addProperty("op", 2);
            frame.add("d", d);
            log.info("[qq] 发送 identify（intents=" + intents + "）");
        }
        send(frame.toString());
    }

    /** op0 事件分发：READY 捕获会话，其余类型透传事件回调。 */
    private void dispatch(JsonObject root, String rawFrame) {
        if (!root.has("t") || root.get("t").isJsonNull()) {
            return; // 无事件类型：非事件帧
        }
        String type = root.get("t").getAsString();
        switch (type) {
            case "READY" -> {
                if (root.has("d") && root.get("d").isJsonObject()) {
                    JsonObject d = root.getAsJsonObject("d");
                    if (d.has("session_id") && !d.get("session_id").isJsonNull()) {
                        sessionId = d.get("session_id").getAsString();
                    }
                }
                log.info("[qq] 网关 READY（会话已建立）");
            }
            case "RESUMED" -> log.info("[qq] 网关 RESUMED（会话续传成功）");
            default -> {
                if (sink != null) {
                    try {
                        sink.onGatewayEvent(type, rawFrame);
                    } catch (RuntimeException e) {
                        log.warning("[qq] 事件回调异常: " + e);
                    }
                }
            }
        }
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
