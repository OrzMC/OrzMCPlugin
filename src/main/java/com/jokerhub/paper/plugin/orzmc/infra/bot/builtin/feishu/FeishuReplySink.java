package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

/**
 * 飞书入站回复回调（builtin 飞书 adapter 下行回执）。
 *
 * <p>{@link FeishuInboundProcessor} 在收到业务回复信封后，将格式化分段逐条送回来源会话。实现方（F4b 由
 * {@link FeishuSender} 承载）负责尽力发送、失败日志/健康告警；飞书无被动回复 msg_id 通道（D14 为 QQ 语义），
 * 直接以 chat_id 发送。发送不得阻塞服务器线程（返回 future，fire-and-forget）。</p>
 */
@FunctionalInterface
public interface FeishuReplySink {

    /** 向来源会话发送一条回复。 */
    void sendReply(String chatId, String text);
}
