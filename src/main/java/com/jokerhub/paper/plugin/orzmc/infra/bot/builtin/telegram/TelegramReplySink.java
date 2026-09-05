package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

/**
 * Telegram 回复回执（builtin TG adapter，批次 5a）：把业务回复文本送回来源会话。
 *
 * <p>TG 无被动回复通道语义——以 chat_id 直发 sendMessage（与飞书同构，均由 Sender 实现
 * 文本分段上限 4096）。</p>
 */
@FunctionalInterface
public interface TelegramReplySink {

    /** 向指定会话发送回复文本。 */
    void sendReply(long chatId, String text);
}
