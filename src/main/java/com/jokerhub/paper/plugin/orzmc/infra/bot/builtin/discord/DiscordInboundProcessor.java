package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotInboundDispatcher;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImMessageRouter;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Discord 入站消息处理器（builtin DC adapter，批次 5b；对齐 QQ/飞书/TG 通道语义 R4/R6/R12）。
 *
 * <p>由 {@link DiscordGatewayClient} 把 MESSAGE_CREATE 事件经 sink 送入（{@link #onMessage}）：</p>
 * <ol>
 *   <li><b>源过滤（R4）</b>：bot 来源滤除（防回声环）；</li>
 *   <li><b>会话门槛（fail-closed）</b>：来源会话必须 ∈ 绑定 adminGroup/playerGroup/adminDm（共用
 *       {@link ImMessageRouter#isInboundAllowed}）；未绑定只进控制台日志（节流），不向陌生会话回消息（D11）；</li>
 *   <li><b>线程调度（R12）</b>：门槛在 WS 读线程判定（无 Bukkit API），通过后经 {@link ServerScheduler#runSync}
 *       调度到服务器线程；管理员判定异步（Discord REST + 缓存）→ 结果回来二次 runSync
 *       ——两次 runSync 均非阻塞入队，服务器线程绝不等待网络（对齐 AGENTS Folia 红线）；</li>
 *   <li><b>回复回执</b>：业务回复信封经 {@link DiscordReplySink} 送回来源频道（群聊/DM 均按 channel_id 直发）。</li>
 * </ol>
 */
public final class DiscordInboundProcessor implements DiscordEventSink {

    private final Logger log;
    private final ServerScheduler scheduler;
    private final Supplier<ImConversation> conversation;
    private final BotInboundHandler inbound;
    private final MessageFormatter formatter;
    private final DiscordAdminResolver roleResolver;
    private final DiscordReplySink outbound;
    private final ImDiscoveryCandidates discovery;
    /** 未绑定会话日志节流：同一条消息最频繁每 30s 提示一次。 */
    private static final long UNBOUND_LOG_INTERVAL_MS = 30_000;

    private volatile long lastUnboundLogMs;

    public DiscordInboundProcessor(
            Logger log,
            ServerScheduler scheduler,
            Supplier<ImConversation> conversation,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            DiscordAdminResolver roleResolver,
            DiscordReplySink outbound) {
        this(log, scheduler, conversation, inbound, formatter, roleResolver, outbound, null);
    }

    public DiscordInboundProcessor(
            Logger log,
            ServerScheduler scheduler,
            Supplier<ImConversation> conversation,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            DiscordAdminResolver roleResolver,
            DiscordReplySink outbound,
            ImDiscoveryCandidates discovery) {
        if (log == null || scheduler == null || conversation == null || inbound == null || roleResolver == null) {
            throw new IllegalArgumentException("log/scheduler/conversation/inbound/roleResolver must not be null");
        }
        this.log = log;
        this.scheduler = scheduler;
        this.conversation = conversation;
        this.inbound = inbound;
        this.formatter = formatter;
        this.roleResolver = roleResolver;
        this.outbound = outbound;
        this.discovery = discovery;
    }

    /** WS 读线程回调（DiscordEventSink）：MESSAGE_CREATE → 解析 → 门槛/调度处理。 */
    @Override
    public void onGatewayEvent(String type, String rawFrame) {
        if (!"MESSAGE_CREATE".equals(type)) {
            return; // 网关生命周期事件（READY/RESUMED）由 GatewayClient 消费，其余业务事件暂不处理
        }
        onMessage(DiscordInboundParser.parseMessageCreate(rawFrame));
    }

    /** 处理一条已解析文本用户消息（解析/源过滤已在 gateway 侧完成，此处做门槛+调度）。 */
    public void onMessage(DiscordInboundMessage message) {
        if (message == null) {
            return;
        }
        if (message.isBot()) {
            return; // R4：bot 来源滤除（防回声环）
        }
        ImConversation conv = conversation.get();
        if (!ImMessageRouter.isInboundAllowed(conv, message.target())) {
            logUnbound(message);
            return; // D11：未绑定会话只进控制台日志
        }
        // R12：WS 线程只做门槛；命令层落服务器线程。角色判定异步（REST + 缓存）→ 二次 runSync。
        scheduler.runSync(() -> dispatchAfterRoleResolve(message));
    }

    /** 服务器线程：发起异步角色判定，结果回来后再次调度进命令层（全程不阻塞服务器线程等网络）。 */
    private void dispatchAfterRoleResolve(DiscordInboundMessage message) {
        try {
            CompletableFuture<Boolean> role = roleResolver.isAdmin(message);
            role.whenComplete((admin, error) -> {
                boolean isAdmin = error == null && Boolean.TRUE.equals(admin);
                if (error != null) {
                    log.warning("[discord] 角色判定异常（按非管理处理）: " + error);
                }
                scheduler.runSync(() -> dispatch(message, isAdmin));
            });
        } catch (RuntimeException e) {
            log.warning("[discord] 角色判定发起异常（按非管理处理）: " + e);
            scheduler.runSync(() -> dispatch(message, false));
        }
    }

    private void dispatch(DiscordInboundMessage message, boolean isAdmin) {
        try {
            BotInboundDispatcher.dispatch(inbound, message.text(), isAdmin, message.senderId(), replySink(message));
        } catch (RuntimeException e) {
            log.warning("[discord] 入站消息处理异常: " + e);
        }
    }

    /** 回复回执：业务信封 → 格式分段 → 逐条送回来源会话（来源频道直发）。 */
    private java.util.function.Consumer<MessageEnvelope> replySink(DiscordInboundMessage message) {
        return envelope -> {
            if (envelope == null) {
                return;
            }
            MessageEnvelope.Format format =
                    envelope.format() == null ? MessageEnvelope.Format.DEFAULT : envelope.format();
            try {
                for (String part : formatter.format(envelope.message(), format)) {
                    outbound.sendReply(message, part);
                }
            } catch (RuntimeException e) {
                log.warning("[discord] 回复发送异常: " + e);
            }
        };
    }

    private void logUnbound(DiscordInboundMessage message) {
        if (discovery != null) {
            discovery.record(message.target()); // 候选供 status（D11）
        }
        long now = System.currentTimeMillis();
        if (now - lastUnboundLogMs >= UNBOUND_LOG_INTERVAL_MS) {
            lastUnboundLogMs = now;
            StringBuilder sb = new StringBuilder("[discord] 未绑定会话消息 " + message.target());
            java.util.List<String> cmds = ImDiscoveryCandidates.bindCommands(message.target());
            if (cmds.isEmpty()) {
                sb.append("（绑定见 /config im bind，候选入 status）");
            } else {
                sb.append("，绑定命令（复制执行任一条即完成，即时生效；admin_group=管理群 / player_group=玩家群 / admin_dm=管理员私聊）:");
                for (String c : cmds) {
                    sb.append("\n  ").append(c);
                }
                sb.append("\n绑定后本会话自动从 status 候选清除");
            }
            log.info(sb.toString());
        }
    }
}
