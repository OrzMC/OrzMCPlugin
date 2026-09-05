package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

/**
 * QQ 入站消息发送回调（builtin QQ adapter 下行回执）。
 *
 * <p>{@link QqInboundProcessor} 在收到业务回复信封后，将格式化分段逐条送回来源会话。实现方（S7 由
 * {@link QqSender} 承载）负责尽力发送、失败日志/健康告警与被动回复窗口（D14：带 msg_id）；发送不得阻塞
 * 服务器线程（QqSender 返回 future，fire-and-forget）。</p>
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
     */
    void sendReply(String chatType, String chatId, String text, String replyMsgId);
}
