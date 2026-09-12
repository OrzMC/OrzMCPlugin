package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotInboundDispatcher;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImConversation;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImDiscoveryCandidates;
import com.jokerhub.paper.plugin.orzmc.infra.bot.ImMessageRouter;
import com.jokerhub.paper.plugin.orzmc.infra.bot.InboundDedup;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * QQ 入站事件处理器（builtin QQ adapter，方案 §6 / R4 / R6 / R12）。
 *
 * <p>实现 {@link QqEventSink}（S4 网关客户端回调），把 QQ op0 消息事件接到业务层：</p>
 * <ol>
 *   <li><b>解析归一</b>：{@link QqInboundParser} → {@link QqInboundMessage}（失败/媒体/空文本丢弃，D6）；</li>
 *   <li><b>源过滤（R4）</b>：滤除机器人账号消息（author.bot，防回声环）；</li>
 *   <li><b>重放去重</b>：同一 {@code msg_id} 在 5 分钟窗口内只处理一次（断线走 op6 RESUME 会话续传时平台会重放
 *       遗漏事件，{@code $e}/{@code $b}/{@code $o} 等非幂等命令不得重复执行）；</li>
 *   <li><b>入站限频（R6）</b>：沿用 EasyBot 通道语义（100/s 滑动窗口），超限丢弃防批量命令；</li>
 *   <li><b>会话门槛（fail-closed）</b>：来源会话必须 ∈ 绑定 adminGroup/playerGroup/adminDm（与 EasyBot 通道
 *       共用 {@link ImMessageRouter#isInboundAllowed}）；未绑定会话只进控制台日志（节流），不向陌生会话回消息（D11）；</li>
 *   <li><b>线程调度（R12）</b>：门槛判定在 WS 回调线程完成（无 Bukkit API），通过后经
 *       {@link ServerScheduler#runSync} 调度到服务器线程再进命令层（Folia/Paper 统一由装配方注入实现）；</li>
 *   <li><b>回复回执</b>：业务回复信封按格式分段后经 {@link QqReplySink} 送回来源会话（被动回复带 msg_id，D14）。</li>
 * </ol>
 */
public final class QqInboundProcessor implements QqEventSink {

    /** 入站事件速率上限（对齐 EasyBot 通道 100/s，R6）。 */
    private static final int MAX_EVENTS_PER_SECOND = 100;
    /**
     * 未绑定会话「日志提示」节流窗口：**按会话（target）各自计时**——同一会话 30s 内重复消息只提示一次，
     * 不同会话互不影响（此前为全局单时间戳，30s 内只有第一个会话能打印绑定命令，管理员会漏看；
     * 候选本身始终记录，见 {@code /config im status}）。
     */
    private static final long UNBOUND_LOG_INTERVAL_MS = 30_000;
    /** 消息 id 去重窗口（ms）：断线走 op6 RESUME 会话续传，平台会重放遗漏事件。 */
    private static final long DEDUP_TTL_MS = 300_000L;

    private final Logger log;
    private final ServerScheduler scheduler;
    private final Supplier<ImConversation> conversation;
    private final BotInboundHandler inbound;
    private final MessageFormatter formatter;
    private final QqReplySink outbound;
    /** 未绑定会话发现候选（可为 null：不记录；D11 候选仅 status 提示用）。 */
    private final ImDiscoveryCandidates discovery;

    private final AtomicLong rateWindowStart = new AtomicLong();
    private final AtomicInteger rateWindowCount = new AtomicInteger();
    private final ThrottledNotifier unboundLogThrottle = new ThrottledNotifier();
    private final InboundDedup dedup = new InboundDedup(DEDUP_TTL_MS);

    public QqInboundProcessor(
            Logger log,
            ServerScheduler scheduler,
            Supplier<ImConversation> conversation,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            QqReplySink outbound) {
        this(log, scheduler, conversation, inbound, formatter, outbound, null);
    }

    public QqInboundProcessor(
            Logger log,
            ServerScheduler scheduler,
            Supplier<ImConversation> conversation,
            BotInboundHandler inbound,
            MessageFormatter formatter,
            QqReplySink outbound,
            ImDiscoveryCandidates discovery) {
        if (log == null || scheduler == null || conversation == null || inbound == null) {
            throw new IllegalArgumentException("log/scheduler/conversation/inbound must not be null");
        }
        this.log = log;
        this.scheduler = scheduler;
        this.conversation = conversation;
        this.inbound = inbound;
        this.formatter = formatter;
        this.outbound = outbound;
        this.discovery = discovery;
    }

    @Override
    public void onGatewayEvent(String type, String rawFrame) {
        if (!allowInboundEvent()) {
            log.warning("[qq] 入站事件超过速率限制，已丢弃");
            return;
        }
        QqInboundMessage message = QqInboundParser.parse(type, rawFrame);
        if (message == null) {
            return;
        }
        if (message.isBot()) {
            return; // R4：滤除机器人消息（防回声环）
        }
        if (!dedup.firstSeen(message.msgId())) {
            // 重放（op6 RESUME 会话续传）/ 平台重复投递：同一消息只处理一次，避免 $e/$b/$o/$r 重复执行
            log.fine("[qq] 重复消息已忽略（重放去重）: id=" + message.msgId());
            return;
        }
        ImConversation conv = conversation.get();
        if (!ImMessageRouter.isInboundAllowed(conv, message.target())) {
            logUnbound(message);
            return; // D11：未绑定会话只进控制台日志，不打扰陌生会话
        }
        // R12：WS 回调线程只做解析/门槛（无 Bukkit API）；命令层必须落在服务器线程
        scheduler.runSync(() -> dispatch(message));
    }

    private void dispatch(QqInboundMessage message) {
        try {
            BotInboundDispatcher.dispatch(
                    inbound, message.text(), message.isAdmin(), message.senderName(), replySink(message));
        } catch (RuntimeException e) {
            log.warning("[qq] 入站消息处理异常: " + e);
        }
    }

    /** 回复回执：业务信封 → 格式分段 → 逐条送回来源会话（被动通道带 msg_id，D14）。 */
    private java.util.function.Consumer<MessageEnvelope> replySink(QqInboundMessage message) {
        return envelope -> {
            if (envelope == null) {
                return;
            }
            MessageEnvelope.Format format =
                    envelope.format() == null ? MessageEnvelope.Format.DEFAULT : envelope.format();
            try {
                for (String part : formatter.format(envelope.message(), format)) {
                    outbound.sendReply(message.chatType(), message.chatId(), part, message.msgId());
                }
            } catch (RuntimeException e) {
                log.warning("[qq] 回复发送异常: " + e);
            }
        };
    }

    private void logUnbound(QqInboundMessage message) {
        if (discovery != null) {
            discovery.record(message.target()); // 候选供 status（D11）：每次事件都记，日志节流不影响
        }
        if (unboundLogThrottle.shouldRun(message.target(), UNBOUND_LOG_INTERVAL_MS)) {
            // 只进控制台日志（D11）：提示管理员用 /config im bind 绑定该会话，并直接给出可复制命令
            StringBuilder sb = new StringBuilder("[qq] 未绑定会话消息 " + message.target());
            java.util.List<String> cmds = ImDiscoveryCandidates.bindCommands(message.target());
            if (cmds.isEmpty()) {
                sb.append("（绑定见 /config im bind，候选入 status）");
            } else {
                sb.append("，绑定命令（复制执行任一条即完成，即时生效；admin_group=管理群 / player_group=玩家群 / admin_dm=管理员私聊）:");
                for (String c : cmds) {
                    sb.append("\n  ").append(c);
                }
                sb.append("\n绑定后本会话自动从 status 候选清除；其他未绑定会话见 /config im status");
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
