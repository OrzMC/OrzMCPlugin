package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

/**
 * QQ 入站消息发送回调（builtin QQ adapter 下行回执）。
 *
 * <p>{@link QqInboundProcessor} 在收到业务回复信封后，将格式化分段逐条送回来源会话。实现方（S7 由
 * {@link QqSender} 承载）负责尽力发送、失败日志/健康告警与被动回复窗口（D14：带 msg_id）；发送不得阻塞
 * 服务器线程（QqSender 返回 future，fire-and-forget）。</p>
 *
 * <p><b>为什么回执要携带来源消息的接收时刻：</b>被动回复的有效窗口是群 5 分钟 / 单聊 60 分钟，
 * 超窗后平台返回 {@code 40034005 msg_id 已过期}。{@code $b}/{@code $o} 这类分钟级任务的完成回执必然超窗，
 * 所以窗口判定必须发生在<b>发送前</b>（由 {@link QqSender} 据 {@code receivedAt} 改走主动消息），
 * 不能靠平台报错兜底（2026-11-03 备份 25 分钟→完成通知丢失事故）。</p>
 */
@FunctionalInterface
public interface QqReplySink {

    /**
     * 向来源会话发送一条回复。
     *
     * @param chatType 会话类型（group / user）
     * @param chatId 平台 openid
     * @param text 已格式化的文本分段
     * @param replyMsgId 被动回复来源消息 id（无则 null）
     * @param receivedAt 来源入站消息的接收时刻（用于被动回复窗口判定；{@code null} = 不判定）
     */
    void sendReply(String chatType, String chatId, String text, String replyMsgId, java.time.Instant receivedAt);
}
