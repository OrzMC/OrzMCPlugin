package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.bot.TextChunker;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq.QqApiClient;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq.QqGatewayClient;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq.QqInboundProcessor;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq.QqSender;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.RefreshableTokenProvider;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ImProxyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.QqPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * QQ 平台适配器（builtin，S7 聚合 S4–S6 产物）：出站网关连接（QqGatewayClient）+ 下行发送（QqSender）
 * + 入站处理（QqInboundProcessor）的组合根 + 健康上报（key {@code builtin.qq}）。
 *
 * <p>凭据（{@link QqPlatformConfig}）仅在本类构造时消费一次；access_token 经 RefreshableTokenProvider
 * （fetch=QQ getAppAccessToken）自动预刷新/鉴权失败强换。网关鉴权失败（4004）→ onAuthFailure 已由
 * QqGatewayClient 内部处理；本类只把连接状态桥接进 {@link HealthRegistry}（线程安全，回调线程可直接写）。</p>
 */
public final class QqBuiltinAdapter implements BuiltinPlatform {

    public static final String HEALTH_KEY = "builtin.qq";
    /** QQ access_token 有效期 2h，提前 60s 预刷新（对齐 EasyBot / 官方）。 */
    private static final Duration TOKEN_TTL = Duration.ofSeconds(7200);

    private static final Duration TOKEN_REFRESH_AHEAD = Duration.ofSeconds(60);

    private final Logger log;
    private final HealthRegistry health;
    private final TokenProvider tokens;
    private final QqSender sender;
    private final QqGatewayClient gateway;
    /** 单条文本上限（UTF-8 字节，0 = 不分段）：官方未公开数字（仅错误码 40054007），默认保守值可配（D2/R7）。 */
    private final int maxTextBytes;
    /** 最多段数：被动回复受平台「每条源消息最多 5 次」约束，故取 5（超出的尾部截断 + 告警）。 */
    private static final int MAX_TEXT_PARTS = 5;

    public QqBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            QqPlatformConfig cfg) {
        this(serverLogger, scheduler, inbound, formatter, conversation, health, cfg, null);
    }

    /**
     * @param discovery 未绑定会话发现候选（可为 null；D11：候选进 status 提示）
     */
    public QqBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            QqPlatformConfig cfg,
            ImDiscoveryCandidates discovery) {
        if (serverLogger == null || scheduler == null || inbound == null || health == null) {
            throw new IllegalArgumentException("必需依赖不能为 null");
        }
        if (cfg == null || !cfg.usable()) {
            throw new IllegalArgumentException("QQ 平台需 enabled 且凭据齐备（usable）");
        }
        this.log = serverLogger.logger();
        this.health = health;
        java.net.Proxy proxy = resolveProxy(cfg);
        QqApiClient api = new QqApiClient(cfg.appId(), cfg.clientSecret(), proxy, log);
        this.tokens = new RefreshableTokenProvider(api::fetchAccessToken, TOKEN_TTL, TOKEN_REFRESH_AHEAD);
        this.sender = new QqSender(log, tokens, proxy);
        QqInboundProcessor processor =
                new QqInboundProcessor(log, scheduler, conversation, inbound, formatter, this::sendReply, discovery);
        this.gateway = new QqGatewayClient(
                serverLogger,
                ReconnectPolicy.defaults(),
                tokens,
                api,
                QqGatewayClient.INTENT_GROUP_AND_C2C,
                processor,
                new HealthListener(),
                proxy);
    }

    /** 生效代理解析（cfg.proxy 已合并全局段；null/DIRECT → 直连）。 */
    private static java.net.Proxy resolveProxy(QqPlatformConfig cfg) {
        ImProxyConfig proxy = cfg.proxy();
        return proxy == null ? java.net.Proxy.NO_PROXY : proxy.toProxy();
    }

    @Override
    public String platform() {
        return "qq";
    }

    @Override
    public void start() {
        health.setEnabled(HEALTH_KEY, true);
        gateway.start(); // 幂等；连接状态变化经 HealthListener 上报
    }

    @Override
    public void stop() {
        gateway.stop();
        health.setEnabled(HEALTH_KEY, false);
        health.setWsConnected(HEALTH_KEY, false);
    }

    @Override
    public void reconnectIfNeeded() {
        gateway.start(); // 已 FATAL → 重启；OPEN/CONNECTING → 无操作
    }

    @Override
    public void send(String target, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        QqTarget parsed = QqTarget.parse(target);
        if (parsed == null) {
            if (target != null && target.startsWith("qq:")) {
                log.warning("[qq] 无法识别的 target: " + target);
            }
            return;
        }
        if (parsed.isGroup()) {
            sendChunked(target, parsed.openid(), text, null, true);
        } else {
            sendChunked(target, parsed.openid(), text, null, false);
        }
    }

    /** 被动回复（msg_id = 来源消息，D14）；长文本按平台上限分段（每段递增 msg_seq）。 */
    private void sendReply(String chatType, String chatId, String text, String replyMsgId) {
        sendChunked(chatId, chatId, text, replyMsgId, QqTarget.CHAT_GROUP.equals(chatType));
    }

    /**
     * 按平台单条上限（UTF-8 字节）分段发送；超段数上限时截断并告警（D2）。
     *
     * <p>QQ 官方未公开单条文本上限（只给错误码 {@code 40054007 消息长度超限}），故默认保守（3KB）且可经
     * {@code im.yml → platforms.qq.max_text_bytes} 调优；被动回复每段携带递增 {@code msg_seq}（由 QqSender 维护）。</p>
     */
    private void sendChunked(String alertTarget, String chatId, String text, String replyMsgId, boolean group) {
        TextChunker.Result chunked = maxTextBytes <= 0
                ? new TextChunker.Result(java.util.List.of(text), false)
                : TextChunker.byUtf8Bytes(text, maxTextBytes, MAX_TEXT_PARTS);
        if (chunked.truncated()) {
            log.warning("[qq] 消息过长：已按 " + maxTextBytes + " 字节 × 最多 " + MAX_TEXT_PARTS + " 段发出，尾部截断"
                    + "（完整内容见服务器日志）；如仍报 40054007 请调小 im.yml platforms.qq.max_text_bytes: target=" + alertTarget);
        }
        for (String part : chunked.parts()) {
            if (group) {
                fire(sender.sendGroupMessage(chatId, part, replyMsgId), alertTarget);
            } else {
                fire(sender.sendDirectMessage(chatId, part, replyMsgId), alertTarget);
            }
        }
    }

    /** 投递结果分类处理：确定失败 → 健康告警；结果未知（响应超时，平台可能已投递）→ 仅 WARN，不污染健康。 */
    private void fire(CompletableFuture<QqSender.Outcome> future, String target) {
        future.whenComplete((outcome, err) -> {
            QqSender.Outcome result = err != null ? QqSender.Outcome.FAILED : outcome;
            if (result == QqSender.Outcome.SENT) {
                health.setLastError(HEALTH_KEY, null); // 成功即复位：lastError 显示的是「当前错误」而非历史错误
                return;
            }
            if (result == QqSender.Outcome.UNKNOWN) {
                // 请求已发出但响应超时：群内可能已收到（2026-09 线上实测）。不写 lastError（平台未故障）、不重试，
                // 不重复打日志——QqSender 已按 path 打过「投递响应超时（结果未知）」WARN（仅此处可见 target 形式）
                return;
            }
            health.setLastError(HEALTH_KEY, "QQ 发送失败 target=" + target + (err == null ? "" : " " + err));
            log.warning("[qq] 发送失败 target=" + target + (err == null ? "" : " " + err));
        });
    }

    /** target {@code qq:<chatType>:<openid>} 解析。 */
    private record QqTarget(String chatType, String openid) {
        static final String CHAT_GROUP = "group";

        static QqTarget parse(String target) {
            if (target == null || !target.startsWith("qq:")) {
                return null;
            }
            String rest = target.substring(3);
            int sep = rest.indexOf(':');
            if (sep <= 0 || sep == rest.length() - 1) {
                return null;
            }
            return new QqTarget(rest.substring(0, sep), rest.substring(sep + 1));
        }

        boolean isGroup() {
            return CHAT_GROUP.equals(chatType);
        }
    }

    /** 网关连接状态 → 健康（key builtin.qq）：回调线程直接写 volatile 字段，安全。 */
    private final class HealthListener implements GatewayStateListener {
        @Override
        public void onConnected() {
            health.setWsConnected(HEALTH_KEY, true);
            health.setLastError(HEALTH_KEY, null);
        }

        @Override
        public void onDisconnected(int code, String reason) {
            health.setWsConnected(HEALTH_KEY, false);
        }

        @Override
        public void onFatal(String message, Throwable cause) {
            health.setWsConnected(HEALTH_KEY, false);
            health.setLastError(HEALTH_KEY, "QQ 网关终止: " + message);
        }
    }
}
