package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

/**
 * Discord 回复回执（builtin DC adapter，批次 5b）：把业务回复文本送回来源会话。
 *
 * <p>Discord 无被动回复窗口语义——回复均以「来源频道」为目标（群聊=频道 id，DM=DM channel id），
 * 一条 {@code POST /channels/{id}/messages} 即可（与 QQ/飞书/TG 的 chat_id 直发同构）。</p>
 */
@FunctionalInterface
public interface DiscordReplySink {

    /** 向来源会话（频道）发送回复文本。 */
    void sendReply(DiscordInboundMessage source, String text);
}
