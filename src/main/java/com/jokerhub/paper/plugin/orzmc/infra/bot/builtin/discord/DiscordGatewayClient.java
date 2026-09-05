package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGateway;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Discord Gateway WS 客户端（builtin DC adapter，批次 5b；官方 Gateway v10 协议）。
 *
 * <p>复用 {@link ReconnectingGateway} 统一连接生命周期（指数退避重连/心跳看门狗/状态回调/stop），本类只实现
 * Discord 协议语义（与 QQ v1 网关同为「hello→identify/resume→心跳」形态）：</p>
 * <ul>
 *   <li><b>identify/resume 决策</b>：建连后等服务端 hello（op10）再出手——有会话（READY 的 session_id）且
 *       收过事件 → op6 resume（seq 续传）；否则 op2 identify（token + intents + properties）；</li>
 *   <li><b>心跳</b>：hello 携带 heartbeat_interval，按 0.75 安全系数配置周期发送 op1（d=最新 seq，未收事件为 null）；</li>
 *   <li><b>事件分发</b>：op0 的 READY 捕获 session_id / RESUMED 标记续传成功，其余事件透传 {@link DiscordEventSink}；</li>
 *   <li><b>重连</b>：op7 服务端要求重连 → 保留会话立即重连（走 resume）；op9 无效会话 → d=true 可 resume、
 *       d=false 清会话全量 re-identify；</li>
 *   <li><b>鉴权错误</b>：关闭码 4004（Authentication failed，token 无效）→ {@link #onAuthFailure()}（无令牌刷新器
 *       → fatal 停止自动重连，对齐 Telegram 401=fatal 语义——DC token 为长期静态凭据，配置错误不反复重试）。</li>
 * </ul>
 *
 * <p>每次连接尝试经 {@link DiscordApiClient#fetchGatewayUrl} 解析网关地址（60s 缓存防限频）。
 * token 为长期静态（构造时消费，无刷新语义）。线程纪律（R12）：本类逻辑全在 WS 读线程/网关调度线程
 * （{@code im-discord-gw}）执行，事件经 sink 回调——实现方不得触碰 Bukkit API；{@link #stop()} 幂等清理（R13）。</p>
 */
public final class DiscordGatewayClient extends ReconnectingGateway {

    /** 鉴权失败关闭码：identify 后 token 无效（Discord 4004 Authentication failed）。 */
    private static final int CLOSE_AUTH_FAILED = 4004;

    /** 心跳安全系数：按 hello 间隔的 0.75 发送（留出网络抖动余量，防止看门狗误杀）。 */
    private static final double HEARTBEAT_SAFETY = 0.75;

    /**
     * 入站事件订阅 intents（批次 5b 最小集）：GUILD_MESSAGES(1&lt;&lt;9) + DIRECT_MESSAGES(1&lt;&lt;12)
     * + MESSAGE_CONTENT(1&lt;&lt;15，特权——需开发者门户开启，否则收不到 content）。
     */
    static final int INTENTS = (1 << 9) | (1 << 12) | (1 << 15);

    private final String token;
    private final DiscordApiClient api;
    private final DiscordEventSink sink;
    private final Logger log;

    /** 客户端最新事件序号（随每个带 s 的入站帧推进），用于心跳 d 与 resume seq。 */
    private final AtomicLong seq = new AtomicLong(-1);
    /** READY 下发的会话 id：具备时断线重连走 resume。 */
    private volatile String sessionId;

    /**
     * @param server 服务端日志门面
     * @param policy 重连退避策略（null → 骨架默认 5s 起/60s 上限）
     * @param api REST 客户端（网关地址引导/自检共用）
     * @param token Discord bot token（静态长期凭据；REST 用 Bot 前缀在 ApiClient，此处为 identify/resume 裸 token）
     * @param sink 入站事件回调（可为 null：仅连接不上报业务事件）
     * @param listener 连接状态观察者（可为 null；健康聚合注入）
     */
    public DiscordGatewayClient(
            ServerLogger server,
            ReconnectPolicy policy,
            DiscordApiClient api,
            String token,
            DiscordEventSink sink,
            GatewayStateListener listener) {
        super("discord", server, policy, null, listener);
        if (api == null) {
            throw new IllegalArgumentException("api must not be null");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        this.api = api;
        this.token = token;
        this.sink = sink;
        this.log = server.logger();
    }

    // =====================================================================
    // ReconnectingGateway 模板钩子
    // =====================================================================

    @Override
    protected String resolveEndpoint() {
        String url = api.fetchGatewayUrl();
        if (url == null || url.isBlank()) {
            log.warning("[discord] 网关地址不可用（/gateway/bot 失败——token 无效或网络/代理不可达），本次建连失败将退避重试");
            return null;
        }
        return url;
    }

    @Override
    protected void onGatewayOpen() {
        // Discord 建连后由服务端先发 hello（op10）；identify/resume 与心跳配置随 hello 完成
    }

    @Override
    protected void onGatewayPayload(String payload) {
        JsonObject root;
        try {
            root = JsonParser.parseString(payload).getAsJsonObject();
        } catch (RuntimeException e) {
            log.warning("[discord] 无法解析网关帧，忽略: " + clip(payload));
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
                log.info("[discord] 收到 op7（reconnect），立即重连并尝试 resume");
                reconnectNow();
            }
            case 9 -> {
                // 无效会话：d=true 可 resume（保留会话重连）；d=false 需全量 re-identify（清会话）
                boolean resumable = root.has("d")
                        && !root.get("d").isJsonNull()
                        && root.get("d").isJsonPrimitive()
                        && root.get("d").getAsBoolean();
                if (!resumable) {
                    sessionId = null;
                    log.warning("[discord] 收到 op9（无效会话，需 re-identify），清除 session 后重连");
                } else {
                    log.info("[discord] 收到 op9（无效会话但可 resume），重连续传");
                }
                reconnectNow();
            }
            default -> {
                // op1/op11 心跳帧与回执等：无需动作（基类已按入站帧喂活看门狗）
            }
        }
    }

    @Override
    protected void onGatewayClosed(int code, String reason, boolean remote) {
        if (code == CLOSE_AUTH_FAILED) {
            log.warning("[discord] 网关鉴权失败关闭（code=4004，token 无效），停止自动重连（检查 token 配置）");
            onAuthFailure(); // 无令牌刷新器 → fatal 停（DC token 长期静态，配置错误不反复重试）
        }
        // 其余关闭码（网络断/超时/op9 后的正常断开等）由基类统一退避自动重连
    }

    @Override
    protected void onGatewayFatal(String message, Throwable cause) {
        log.severe("[discord] 网关不可恢复终止: " + message);
    }

    // =====================================================================
    // Discord 协议处理
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
            log.warning("[discord] hello 缺少合法 heartbeat_interval，无法配置心跳（依赖静默看门狗兜底）");
        } else {
            configureHeartbeat((long) (intervalMs * HEARTBEAT_SAFETY), this::heartbeatFrame);
        }
        sendIdentifyOrResume();
    }

    /** 心跳帧（op1，d=最新事件序号；未收过事件为 null——Discord 要求 null 而非 0）。 */
    private String heartbeatFrame() {
        JsonObject frame = new JsonObject();
        frame.addProperty("op", 1);
        long current = seq.get();
        if (current >= 0) {
            frame.addProperty("d", current);
        } else {
            frame.add("d", JsonNull.INSTANCE);
        }
        return frame.toString();
    }

    /**
     * resume（op6）或 identify（op2）决策：有 READY 会话且收过事件 → resume 续传；否则全量 identify
     * （对齐 QQ：resume 失败由服务端 op9/关闭码收敛，届时转全量 identify）。
     */
    private void sendIdentifyOrResume() {
        long currentSeq = seq.get();
        String sid = sessionId;
        boolean canResume = sid != null && !sid.isEmpty() && currentSeq >= 0;
        JsonObject frame = new JsonObject();
        if (canResume) {
            JsonObject d = new JsonObject();
            d.addProperty("token", token);
            d.addProperty("session_id", sid);
            d.addProperty("seq", currentSeq);
            frame.addProperty("op", 6);
            frame.add("d", d);
            log.info("[discord] 发送 resume（session 续传）");
        } else {
            JsonObject d = new JsonObject();
            d.addProperty("token", token);
            d.addProperty("intents", INTENTS);
            JsonObject props = new JsonObject();
            props.addProperty("os", "linux");
            props.addProperty("browser", "java");
            props.addProperty("device", "java");
            d.add("properties", props);
            frame.addProperty("op", 2);
            frame.add("d", d);
            log.info("[discord] 发送 identify（intents=" + INTENTS + "）");
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
                String botName = null;
                if (root.has("d") && root.get("d").isJsonObject()) {
                    JsonObject d = root.getAsJsonObject("d");
                    if (d.has("session_id") && !d.get("session_id").isJsonNull()) {
                        sessionId = d.get("session_id").getAsString();
                    }
                    if (d.has("user") && d.get("user").isJsonObject()) {
                        JsonObject user = d.getAsJsonObject("user");
                        if (user.has("username") && !user.get("username").isJsonNull()) {
                            botName = user.get("username").getAsString();
                        }
                    }
                }
                log.info("[discord] 网关 READY（会话已建立" + (botName == null ? "" : "，bot @" + botName) + "）");
            }
            case "RESUMED" -> log.info("[discord] 网关 RESUMED（会话续传成功）");
            default -> {
                if (sink != null) {
                    try {
                        sink.onGatewayEvent(type, rawFrame);
                    } catch (RuntimeException e) {
                        log.warning("[discord] 事件回调异常: " + e);
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
