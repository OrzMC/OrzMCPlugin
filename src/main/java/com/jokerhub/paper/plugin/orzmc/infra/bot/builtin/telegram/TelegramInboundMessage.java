package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

/**
 * Telegram 入站消息归一模型（builtin Telegram adapter，批次 5a；Update → 业务层前形态）。
 *
 * <p>由 {@link TelegramInboundParser} 从 getUpdates 返回的 Update JSON 解析；业务分发（会话门槛 /
 * 线程调度 / 回复 sink / 角色判定）由 {@link TelegramInboundProcessor} 完成。会话值语义与 QQ/飞书
 * builtin 一致：{@code target()} = {@code telegram:<chatType>:<chatId>}（群 {@code group} / 私聊
 * {@code user}），与 im_bindings 会话同构。</p>
 *
 * @param chatType 会话类型：{@code group}（群/超级群）或 {@code user}（private 私聊）
 * @param chatId 会话 chat_id（群为负数、私聊为正 user id；出站 sendMessage chat_id 即此值）
 * @param msgId 消息 message_id（进程内去重用）
 * @param text 文本内容（仅 text 消息；媒体/无文本在解析层丢弃）
 * @param senderId 发送者 user id
 * @param senderBot 发送者是否 bot（R4：bot 来源滤除，防回声环）
 */
public record TelegramInboundMessage(
        String chatType, long chatId, long msgId, String text, long senderId, boolean senderBot) {

    public static final String CHAT_TYPE_GROUP = "group";
    public static final String CHAT_TYPE_USER = "user";

    /** 是否是真实用户消息（R4：非 bot 来源才进命令层）。 */
    public boolean isUser() {
        return !senderBot;
    }

    /** 归一会话目标（对齐现有 target 语义：platform 前缀 + 会话类型 + chat_id）。 */
    public String target() {
        return "telegram:" + chatType + ":" + chatId;
    }
}
