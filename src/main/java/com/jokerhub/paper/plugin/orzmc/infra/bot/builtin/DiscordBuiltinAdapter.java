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
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord.DiscordApiClient;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord.DiscordGatewayClient;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord.DiscordInboundMessage;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord.DiscordInboundProcessor;
import com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord.DiscordRoleResolver;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.DiscordPlatformConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ImProxyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.net.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Discord 平台适配器（builtin，批次 5b）：出站网关连接（DiscordGatewayClient）+ 下行发送（REST）
 * + 入站处理（DiscordInboundProcessor）的组合根 + 健康上报（key {@code builtin.discord}）。
 *
 * <p>凭据（{@link DiscordPlatformConfig}）仅在本类构造时消费一次；token 为长期静态（无刷新语义，
 * 4004/401=配置错误 → 网关 fatal 停止自动重连）。代理（D13）：REST 与 Gateway WS 均经
 * {@link ImProxyConfig} 解析的 {@link Proxy} 透传（discord.com 国内不可达时必须配；直连则 NO_PROXY）。</p>
 */
public final class DiscordBuiltinAdapter implements BuiltinPlatform {

    public static final String HEALTH_KEY = "builtin.discord";

    /** 官方单条文本上限：content 最多 2000 字符。 */
    private static final int MAX_TEXT_CHARS = 2000;
    /** 一次通知/回复最多段数（避免超长内容刷屏 + 触发 Discord 频控）。 */
    private static final int MAX_TEXT_PARTS = 5;

    private final Logger log;
    private final HealthRegistry health;
    private final DiscordApiClient api;
    private final DiscordInboundProcessor processor;
    private final DiscordGatewayClient gateway;

    public DiscordBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            DiscordPlatformConfig cfg) {
        this(serverLogger, scheduler, inbound, formatter, conversation, health, cfg, null);
    }

    /**
     * @param discovery 未绑定会话发现候选（可为 null；D11：候选进 status 提示）
     */
    public DiscordBuiltinAdapter(
            ServerLogger serverLogger,
            ServerScheduler scheduler,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            Supplier<ImConversation> conversation,
            HealthRegistry health,
            DiscordPlatformConfig cfg,
            ImDiscoveryCandidates discovery) {
        if (serverLogger == null || scheduler == null || inbound == null || health == null) {
            throw new IllegalArgumentException("必需依赖不能为 null");
        }
        if (cfg == null || !cfg.usable()) {
            throw new IllegalArgumentException("discord 平台需 enabled 且 token 齐备（usable）");
        }
        this.log = serverLogger.logger();
        this.health = health;
        Proxy proxy = resolveProxy(cfg);
        this.api = new DiscordApiClient(cfg.token(), DiscordApiClient.DEFAULT_API_BASE, proxy, log);
        this.processor = new DiscordInboundProcessor(
                log,
                scheduler,
                conversation,
                inbound,
                formatter,
                new DiscordRoleResolver(log, api),
                this::sendReply,
                discovery);
        this.gateway = new DiscordGatewayClient(
                serverLogger, ReconnectPolicy.defaults(), api, cfg.token(), processor, new HealthListener());
    }

    /** 生效代理解析（cfg.proxy 已合并全局段；null/DIRECT → 直连）。 */
    private static Proxy resolveProxy(DiscordPlatformConfig cfg) {
        ImProxyConfig proxy = cfg.proxy();
        return proxy == null ? Proxy.NO_PROXY : proxy.toProxy();
    }

    @Override
    public String platform() {
        return "discord";
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
        DiscordTarget parsed = DiscordTarget.parse(target);
        if (parsed == null) {
            if (target != null && target.startsWith("discord:")) {
                log.warning("[discord] 无法识别的 target: " + target);
            }
            return;
        }
        if (parsed.isGroup()) {
            sendChunked(parsed.id(), text, target);
        } else {
            // 私聊出站：user id → DM 通道 → 发消息（全链异步，服务器线程不等网络）
            fire(
                    api.ensureDmChannelAsync(parsed.id()).thenCompose(dmId -> {
                        if (dmId == null) {
                            log.warning("[discord] DM 通道建立失败，无法投递 target=" + target);
                            return CompletableFuture.completedFuture(false);
                        }
                        return api.sendChannelMessageAsync(dmId, text);
                    }),
                    target);
        }
    }

    /** 被动回复（来源频道直发：群聊/DM 的 channel_id 均可用 sendChannelMessageAsync）。 */
    private void sendReply(DiscordInboundMessage source, String text) {
        sendChunked(source.channelId(), text, source.channelId());
    }

    /**
     * 按官方单条上限（content 2000 字符）分段发送（D2）：整条超限会被 400 拒绝 = 通知丢失。
     * 最多 {@link #MAX_TEXT_PARTS} 段，超出部分截断并告警。
     */
    private void sendChunked(String channelId, String text, String alertTarget) {
        fire(sendChunkedFuture(channelId, text, alertTarget), alertTarget);
    }

    /** 分段并逐段异步投递（返回 future 供私聊链路 thenCompose 串联）。 */
    private CompletableFuture<Boolean> sendChunkedFuture(String channelId, String text, String alertTarget) {
        TextChunker.Result chunked = TextChunker.byChars(text, MAX_TEXT_CHARS, MAX_TEXT_PARTS);
        if (chunked.truncated()) {
            log.warning("[discord] 消息过长：已分段发出 " + chunked.parts().size() + " 段（每段 ≤ " + MAX_TEXT_CHARS
                    + " 字符），尾部截断；channel=" + channelId);
        }
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (String part : chunked.parts()) {
            chain = chain.thenCompose(ok -> api.sendChannelMessageAsync(channelId, part));
        }
        return chain;
    }

    /** 尽力一次：失败/异常 → 健康告警（D7：不重试）；异步完成回调（不阻塞调用线程）。 */
    private void fire(CompletableFuture<Boolean> future, String target) {
        future.whenComplete((ok, err) -> {
            if (err != null || !Boolean.TRUE.equals(ok)) {
                health.setLastError(HEALTH_KEY, "discord 发送失败 target=" + target + (err == null ? "" : " " + err));
                log.warning("[discord] 发送失败 target=" + target + (err == null ? "" : " " + err));
            } else {
                health.setLastError(HEALTH_KEY, null); // 成功即复位（lastError 语义 = 当前错误，非历史错误）
            }
        });
    }

    /** target {@code discord:<chatType>:<id>} 解析（group=频道 id / user=用户 id）。 */
    private record DiscordTarget(String chatType, String id) {
        static final String CHAT_GROUP = "group";

        static DiscordTarget parse(String target) {
            if (target == null || !target.startsWith("discord:")) {
                return null;
            }
            String rest = target.substring("discord:".length());
            int sep = rest.indexOf(':');
            if (sep <= 0 || sep == rest.length() - 1) {
                return null;
            }
            String chatType = rest.substring(0, sep);
            // 仅群/单聊两类（防任意值）
            if (!DiscordInboundMessage.CHAT_TYPE_GROUP.equals(chatType)
                    && !DiscordInboundMessage.CHAT_TYPE_USER.equals(chatType)) {
                return null;
            }
            return new DiscordTarget(chatType, rest.substring(sep + 1));
        }

        boolean isGroup() {
            return CHAT_GROUP.equals(chatType);
        }
    }

    /** 网关连接状态 → 健康（key builtin.discord）：回调线程直接写 volatile 字段，安全。 */
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
            health.setLastError(HEALTH_KEY, "discord 网关终止: " + message);
        }
    }
}
