package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotInboundDispatcher;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImMessageRouter;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 飞书入站事件处理器（builtin 飞书 adapter，方案 §6 / R4 / R6 / R12）。
 *
 * <p>实现 {@link FeishuEventSink}（F3b 网关客户端回调，payload=事件 v2 信封），把消息事件接到业务层：</p>
 * <ol>
 *   <li><b>解析归一</b>：{@link FeishuInboundParser} → {@link FeishuInboundMessage}（失败/媒体/空文本丢弃）；</li>
 *   <li><b>源过滤（R4）</b>：滤除非 user 来源（sender_type=app/bot 等，防回声环）；</li>
 *   <li><b>入站限频（R6）</b>：100/s 滑动窗口，超限丢弃防批量命令（对齐 EasyBot/QQ 通道语义）；</li>
 *   <li><b>会话门槛（fail-closed）</b>：来源会话必须 ∈ 绑定 adminGroup/playerGroup/adminDm（共用
 *       {@link ImMessageRouter#isInboundAllowed}）；未绑定会话只进控制台日志（节流），不向陌生会话回消息（D11）；</li>
 *   <li><b>线程调度（R12）</b>：门槛判定在 WS 回调线程完成（无 Bukkit API），通过后经
 *       {@link ServerScheduler#runSync} 调度到服务器线程；<b>管理员判定异步</b>（飞书事件无角色，
 *       FeishuAdminResolver 内部查 chats API + 缓存，async 返回），结果回来后再次 runSync 进命令层
 *       ——两次 runSync 均为非阻塞入队，服务器线程绝不等待网络（对齐 AGENTS Folia 红线）；</li>
 *   <li><b>回复回执</b>：业务回复信封按格式分段后经 {@link FeishuReplySink} 送回来源会话（以 chat_id 直发）。</li>
 * </ol>
 */
public final class FeishuInboundProcessor implements FeishuEventSink {

    /** 入站事件速率上限（对齐 EasyBot 通道 100/s，R6）。 */
    private static final int MAX_EVENTS_PER_SECOND = 100;
    /** 未绑定会话日志节流：同一条消息最频繁每 30s 提示一次。 */
    private static final long UNBOUND_LOG_INTERVAL_MS = 30_000;

    private final Logger log;
    private final ServerScheduler scheduler;
    private final Supplier<ImConversation> conversation;
    private final BotInboundHandler inbound;
    private final MessageFormatter formatter;
    private final FeishuAdminResolver adminResolver;
    private final FeishuReplySink outbound;
    /** 未绑定会话发现候选（可为 null：不记录；D11 候选仅 status 提示用）。 */
    private final ImDiscoveryCandidates discovery;

    private final AtomicLong rateWindowStart = new AtomicLong();
    private final AtomicInteger rateWindowCount = new AtomicInteger();
    private volatile long lastUnboundLogMs;

    public FeishuInboundProcessor(
            Logger log,
            ServerScheduler scheduler,
            Supplier<ImConversation> conversation,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            FeishuAdminResolver adminResolver,
            FeishuReplySink outbound) {
        this(log, scheduler, conversation, inbound, formatter, adminResolver, outbound, null);
    }

    public FeishuInboundProcessor(
            Logger log,
            ServerScheduler scheduler,
            Supplier<ImConversation> conversation,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            FeishuAdminResolver adminResolver,
            FeishuReplySink outbound,
            ImDiscoveryCandidates discovery) {
        if (log == null || scheduler == null || conversation == null || inbound == null || adminResolver == null) {
            throw new IllegalArgumentException("log/scheduler/conversation/inbound/adminResolver must not be null");
        }
        this.log = log;
        this.scheduler = scheduler;
        this.conversation = conversation;
        this.inbound = inbound;
        this.formatter = formatter;
        this.adminResolver = adminResolver;
        this.outbound = outbound;
        this.discovery = discovery;
    }

    @Override
    public void onEvent(byte[] payload) {
        if (!allowInboundEvent()) {
            log.warning("[feishu] 入站事件超过速率限制，已丢弃");
            return;
        }
        FeishuInboundMessage message = FeishuInboundParser.parse(payload);
        if (message == null) {
            return;
        }
        if (!message.isUser()) {
            return; // R4：滤除 app/bot 消息（防回声环）
        }
        ImConversation conv = conversation.get();
        if (!ImMessageRouter.isInboundAllowed(conv, message.target())) {
            logUnbound(message);
            return; // D11：未绑定会话只进控制台日志，不打扰陌生会话
        }
        // R12：WS 回调线程只做解析/门槛（无 Bukkit API）；命令层必须落在服务器线程。
        // 管理员判定异步（chats API + 缓存）→ 结果回来后二次 runSync 进命令层。
        scheduler.runSync(() -> dispatchAfterRoleResolve(message));
    }

    /** 服务器线程：发起异步角色判定，结果回来后再次调度进命令层（全程不阻塞服务器线程等网络）。 */
    private void dispatchAfterRoleResolve(FeishuInboundMessage message) {
        try {
            adminResolver.isAdmin(message).whenComplete((isAdmin, error) -> {
                boolean admin = error == null && Boolean.TRUE.equals(isAdmin);
                if (error != null) {
                    log.warning("[feishu] 角色判定异常（按非管理处理）: " + error);
                }
                scheduler.runSync(() -> dispatch(message, admin));
            });
        } catch (RuntimeException e) {
            log.warning("[feishu] 角色判定发起异常（按非管理处理）: " + e);
            scheduler.runSync(() -> dispatch(message, false));
        }
    }

    private void dispatch(FeishuInboundMessage message, boolean isAdmin) {
        try {
            BotInboundDispatcher.dispatch(inbound, message.text(), isAdmin, message.senderId(), replySink(message));
        } catch (RuntimeException e) {
            log.warning("[feishu] 入站消息处理异常: " + e);
        }
    }

    /** 回复回执：业务信封 → 格式分段 → 逐条送回来源会话（chat_id 直发）。 */
    private java.util.function.Consumer<MessageEnvelope> replySink(FeishuInboundMessage message) {
        return envelope -> {
            if (envelope == null) {
                return;
            }
            MessageEnvelope.Format format =
                    envelope.format() == null ? MessageEnvelope.Format.DEFAULT : envelope.format();
            try {
                for (String part : formatter.format(envelope.message(), format)) {
                    outbound.sendReply(message.chatId(), part);
                }
            } catch (RuntimeException e) {
                log.warning("[feishu] 回复发送异常: " + e);
            }
        };
    }

    private void logUnbound(FeishuInboundMessage message) {
        if (discovery != null) {
            discovery.record(message.target()); // 候选供 status（D11）
        }
        long now = System.currentTimeMillis();
        if (now - lastUnboundLogMs >= UNBOUND_LOG_INTERVAL_MS) {
            lastUnboundLogMs = now;
            // 只进控制台日志（D11）：提示管理员用 /config im bind 绑定该会话，并直接给出可复制命令
            StringBuilder sb = new StringBuilder("[feishu] 未绑定会话消息 " + message.target());
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

    private boolean allowInboundEvent() {
        long now = System.currentTimeMillis();
        long windowStart = rateWindowStart.get();
        if (windowStart == 0L || now - windowStart >= 1000L) {
            if (rateWindowStart.compareAndSet(windowStart, now)) {
                rateWindowCount.set(0);
            }
        }
        return rateWindowCount.incrementAndGet() <= MAX_EVENTS_PER_SECOND;
    }
}
