package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.GatewayStateListener;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn.ReconnectPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuApiClient;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuGatewayClient;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuInboundMessage;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuInboundProcessor;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuRoleResolver;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu.FeishuSender;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.RefreshableTokenProvider;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.token.TokenProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.FeishuPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ImProxyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 飞书平台适配器（builtin，聚合 F2–F4 产物）：出站网关连接（FeishuGatewayClient）+ 下行发送（FeishuSender）
 * + 入站处理（FeishuInboundProcessor，含 FeishuRoleResolver 角色判定）的组合根 + 健康上报
 * （key {@code builtin.feishu}）。
 *
 * <p>凭据（{@link FeishuPlatformConfig}）仅在本类构造时消费一次；tenant_access_token 经 RefreshableTokenProvider
 * （fetch=飞书 auth 端点）自动预刷新/鉴权失败强换。飞书长连接无独立 WS token（端点引导即鉴权）——网关鉴权
 * 失败走退避并由健康降级，本类只把连接状态桥接进 {@link HealthRegistry}（线程安全，回调线程可直接写）。</p>
 */
public final class FeishuBuiltinAdapter implements BuiltinPlatform {

    public static final String HEALTH_KEY = "builtin.feishu";
    /** 飞书 tenant_access_token 有效期 2h，提前 60s 预刷新（对齐 EasyBot / 官方）。 */
    private static final Duration TOKEN_TTL = Duration.ofSeconds(7200);

    private static final Duration TOKEN_REFRESH_AHEAD = Duration.ofSeconds(60);

    private final Logger log;
    private final HealthRegistry health;
    private final FeishuSender sender;
    private final FeishuGatewayClient gateway;

    public FeishuBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            FeishuPlatformConfig cfg) {
        this(serverLogger, scheduler, inbound, formatter, conversation, health, cfg, null);
    }

    /**
     * @param discovery 未绑定会话发现候选（可为 null；D11：候选进 status 提示）
     */
    public FeishuBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            FeishuPlatformConfig cfg,
            ImDiscoveryCandidates discovery) {
        if (serverLogger == null || scheduler == null || inbound == null || health == null) {
            throw new IllegalArgumentException("必需依赖不能为 null");
        }
        if (cfg == null || !cfg.usable()) {
            throw new IllegalArgumentException("飞书平台需 enabled 且凭据齐备（usable）");
        }
        this.log = serverLogger.logger();
        this.health = health;
        java.net.Proxy proxy = resolveProxy(cfg);
        FeishuApiClient api = new FeishuApiClient(cfg.appId(), cfg.appSecret(), proxy, log);
        TokenProvider tokens = new RefreshableTokenProvider(api::fetchTenantToken, TOKEN_TTL, TOKEN_REFRESH_AHEAD);
        this.sender = new FeishuSender(log, tokens, proxy);
        FeishuInboundProcessor processor = new FeishuInboundProcessor(
                log,
                scheduler,
                conversation,
                inbound,
                formatter,
                new FeishuRoleResolver(log, api, tokens),
                this::sendReply,
                discovery);
        this.gateway = new FeishuGatewayClient(
                serverLogger, ReconnectPolicy.defaults(), api, processor, new HealthListener(), proxy);
    }

    /** 生效代理解析（cfg.proxy 已合并全局段；null/DIRECT → 直连）。 */
    private static java.net.Proxy resolveProxy(FeishuPlatformConfig cfg) {
        ImProxyConfig proxy = cfg.proxy();
        return proxy == null ? java.net.Proxy.NO_PROXY : proxy.toProxy();
    }

    @Override
    public String platform() {
        return "feishu";
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
        String chatId = parseChatId(target);
        if (chatId == null) {
            if (target != null && target.startsWith("feishu:")) {
                log.warning("[feishu] 无法识别的 target: " + target);
            }
            return;
        }
        fire(sender.sendMessage(chatId, text), chatId);
    }

    /** 被动回复（chat_id 直发，无 msg_id 通道——飞书语义）。 */
    private void sendReply(String chatId, String text) {
        fire(sender.sendMessage(chatId, text), chatId);
    }

    /** 尽力一次：失败/异常 → 健康告警（D7：不重试）。 */
    private void fire(CompletableFuture<Boolean> future, String target) {
        future.whenComplete((ok, err) -> {
            if (err != null || Boolean.FALSE.equals(ok)) {
                health.setLastError(HEALTH_KEY, "飞书发送失败 target=" + target + (err == null ? "" : " " + err));
                log.warning("[feishu] 发送失败 target=" + target + (err == null ? "" : " " + err));
            }
        });
    }

    /** target {@code feishu:<chatType>:<chatId>} → chatId；非 feishu 前缀/格式错误 → null。 */
    private static String parseChatId(String target) {
        if (target == null || !target.startsWith("feishu:")) {
            return null;
        }
        String rest = target.substring("feishu:".length());
        int sep = rest.indexOf(':');
        if (sep <= 0 || sep == rest.length() - 1) {
            return null;
        }
        String chatId = rest.substring(sep + 1);
        String chatType = rest.substring(0, sep);
        // 仅群/单聊两类（防任意值）：解析校验用——出站均以 chat_id 投递
        if (!FeishuInboundMessage.CHAT_TYPE_GROUP.equals(chatType)
                && !FeishuInboundMessage.CHAT_TYPE_USER.equals(chatType)) {
            return null;
        }
        return chatId;
    }

    /** 网关连接状态 → 健康（key builtin.feishu）：回调线程直接写 volatile 字段，安全。 */
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
            health.setLastError(HEALTH_KEY, "飞书网关终止: " + message);
        }
    }
}
