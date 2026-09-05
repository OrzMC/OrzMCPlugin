package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectingGateway;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 飞书长连接事件订阅 WS 客户端（批次4 F3b；协议对照官方 larksuite-oapi-sdk-rs 0.3.11 ws.rs + 飞书开放平台
 * 「长连接」事件订阅模式）。
 *
 * <p>生命周期（复用 {@link ReconnectingGateway} 骨架的重连/看门狗，二进制帧钩子）：</p>
 * <ul>
 *   <li><b>端点引导</b>：每次连接尝试经 {@link FeishuApiClient#fetchWsEndpoint()}（POST /callback/ws/endpoint，
 *       AppID/AppSecret 直换）拉取 wss 地址 + 服务端下发的 PingInterval——引导失败返回 null → 骨架退避重试；
 *       成功则缓存 {url, pingIntervalMs} 后建连（建连无附加鉴权头，URL 自带）；</li>
 *   <li><b>帧分发</b>（全部为二进制 protobuf 帧，FeishuFrame）：CONTROL+type=ping → 回 pong（回显 seq/log/
 *       service）；CONTROL+type=pong → 忽略（SDK 仅在 pong 携带 ClientConfig 时更新参数，本网关维持引导值）；
 *       DATA+type=event → payload 即事件 v2 信封 JSON，透传 {@link FeishuEventSink} 后回 <b>ACK 帧</b>
 *       （code=200：平台停止重推；处理抛异常时 code=500 → 平台重推，靠 event_id 去重防重复消费）；</li>
 *   <li><b>心跳</b>：建连后按引导 PingInterval（默认 120s）发送二进制 ping 帧（service=端点 URL 中
 *       {@code service_id} 查询参数，缺省 0——SDK 同语义），静默看门狗（3 周期无入站）强制重连由骨架负责；</li>
 *   <li><b>鉴权/终止</b>：服务端握手拒绝 / 建连失败统一走骨架退避；凭据问题持续退避并由健康聚合降级
 *       （飞书长连接无独立 WS token，引导即鉴权——不触发 TokenRefresher）。</li>
 * </ul>
 *
 * <p><b>线程纪律（R12）</b>：事件回调发生在 WS 读线程，{@link FeishuEventSink} 实现不得触碰 Bukkit API
 * （入站归一/调度归 F4）。{@link #stop()} 幂等清理（R13）。</p>
 */
public final class FeishuGatewayClient extends ReconnectingGateway {

    private static final String QUERY_SERVICE_ID = "service_id";
    private static final byte[] ACK_OK = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ACK_ERR = "{\"code\":500}".getBytes(StandardCharsets.UTF_8);

    private final Logger log;
    private final FeishuApiClient api;
    private final FeishuEventSink sink;
    private final java.net.Proxy proxy;
    /** 最近一次引导的服务端 ping 间隔（毫秒；引导缺省 ClientConfig → 120s，SDK 默认）。 */
    private volatile long pingIntervalMs = 120_000;
    /** 最近一次引导的 service_id（端点 URL query 参数，SDK 用其构造心跳帧）。 */
    private volatile int serviceId;

    /**
     * @param server 服务端日志门面
     * @param policy 重连退避策略（null → 骨架默认）
     * @param api 飞书 REST 客户端（fetchWsEndpoint 引导）
     * @param sink 事件回调（可为 null：仅连接不上报事件）
     * @param listener 连接状态观察者（可为 null）
     */
    public FeishuGatewayClient(
            ServerLogger server,
            ReconnectPolicy policy,
            FeishuApiClient api,
            FeishuEventSink sink,
            GatewayStateListener listener) {
        this(server, policy, api, sink, listener, java.net.Proxy.NO_PROXY);
    }

    /**
     * @param server 服务端日志门面
     * @param policy 重连退避策略（null → 骨架默认）
     * @param api 飞书 REST 客户端（fetchWsEndpoint 引导，REST 经其内部代理）
     * @param sink 事件回调（可为 null：仅连接不上报事件）
     * @param listener 连接状态观察者（可为 null）
     * @param proxy 生效网络代理（D13：海外服务器访问飞书长连接 WS 经 HTTP 代理 CONNECT；默认直连）
     */
    public FeishuGatewayClient(
            ServerLogger server,
            ReconnectPolicy policy,
            FeishuApiClient api,
            FeishuEventSink sink,
            GatewayStateListener listener,
            java.net.Proxy proxy) {
        super("feishu", server, policy, null, listener);
        if (server == null) {
            throw new IllegalArgumentException("server must not be null");
        }
        if (api == null) {
            throw new IllegalArgumentException("api must not be null");
        }
        this.log = server.logger();
        this.api = api;
        this.sink = sink;
        this.proxy = proxy == null ? java.net.Proxy.NO_PROXY : proxy;
    }

    /** 生效网络代理（D13：飞书长连接 WS 经 HTTP 代理 CONNECT；默认直连）。 */
    @Override
    protected java.net.Proxy proxy() {
        return proxy;
    }

    @Override
    protected String resolveEndpoint() {
        FeishuApiClient.WsEndpoint ep = api.fetchWsEndpoint();
        if (ep == null) {
            return null; // 骨架退避重试（凭据问题持续失败 → 健康聚合降级）
        }
        this.pingIntervalMs = Math.max(1000, ep.pingIntervalSecs() * 1000L);
        this.serviceId = parseServiceId(ep.url());
        return ep.url();
    }

    @Override
    protected void onGatewayOpen() {
        // 飞书无握手帧：建连即按引导间隔发心跳（二进制 ping）；服务端 ping 的回 pong 在 onGatewayPayload 处理
        configureHeartbeatBytes(
                pingIntervalMs, () -> FeishuFrame.ping(serviceId).encode());
    }

    @Override
    protected void onGatewayPayload(String payload) {
        // 飞书平台网关仅下发二进制 protobuf 帧；文本帧不应发生（防御性日志）
        log.warning("[feishu] 收到意外文本帧（应全为二进制），忽略: " + payload);
    }

    @Override
    protected void onGatewayPayload(byte[] payload) {
        FeishuFrame frame;
        try {
            frame = FeishuFrame.decode(payload);
        } catch (RuntimeException e) {
            log.warning("[feishu] 帧解码失败（忽略）: " + e);
            return;
        }
        if (frame.method() == FeishuFrame.METHOD_CONTROL) {
            handleControl(frame);
            return;
        }
        if (frame.method() == FeishuFrame.METHOD_DATA) {
            handleData(frame);
        }
    }

    private void handleControl(FeishuFrame frame) {
        String type = frame.type();
        if (FeishuFrame.TYPE_PING.equals(type)) {
            // 服务端 ping → 回 pong（回显 seq/log/service）
            sendBytes(FeishuFrame.pong(frame).encode());
            return;
        }
        if (FeishuFrame.TYPE_PONG.equals(type)) {
            // 服务端对客户端心跳的 pong；SDK 可能携带新 ClientConfig，本网关维持引导值即可
            return;
        }
        log.warning("[feishu] 未知控制帧 type=" + type);
    }

    private void handleData(FeishuFrame frame) {
        if (!FeishuFrame.TYPE_EVENT.equals(frame.type()) || frame.payload() == null) {
            return; // 非事件数据帧（理论上不发生）
        }
        boolean ok = true;
        if (sink != null) {
            try {
                sink.onEvent(frame.payload());
            } catch (RuntimeException e) {
                log.warning("[feishu] 事件处理异常（回 ACK 500 触发平台重推）: " + e);
                ok = false;
            }
        }
        // ACK：回显 seq/log/service/method + payload code（200 停推 / 500 重推）
        sendBytes(FeishuFrame.eventAck(frame, 0L, ok ? ACK_OK : ACK_ERR).encode());
    }

    /** 端点 URL query 中 service_id 解析（SDK ws_query_param QUERY_SERVICE_ID 语义）。 */
    private static int parseServiceId(String url) {
        try {
            String query = java.net.URI.create(url).getQuery();
            if (query == null) {
                return 0;
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && QUERY_SERVICE_ID.equals(pair.substring(0, eq))) {
                    return Integer.parseInt(pair.substring(eq + 1));
                }
            }
        } catch (RuntimeException ignored) {
            // 缺省 0
        }
        return 0;
    }
}
