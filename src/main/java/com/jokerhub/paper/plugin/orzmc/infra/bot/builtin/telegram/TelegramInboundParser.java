package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Telegram Update JSON 解析器（builtin Telegram adapter，批次 5a）。
 *
 * <p>从单条 Update 中提取 {@code update_id} + 消息（如有）。R8：即使 Update 无消息（非 message 事件、
 * 媒体/编辑等），调用方仍须推进 {@code offset=update_id+1}——本解析返回 {@link TelegramUpdate} 携带
 * updateId 供推进，消息为 null 时调用方只推进不处理。</p>
 */
public final class TelegramInboundParser {

    /** 单条文本上限（R7 对齐官方 4096；超出在解析层丢弃，防入站巨文）。 */
    static final int MAX_TEXT_CHARS = 4096;

    private TelegramInboundParser() {}

    /** 解析结果：updateId 恒有效（解析失败 → -1）；message 仅当为文本用户消息时非 null。 */
    public record TelegramUpdate(long updateId, TelegramInboundMessage message) {}

    /**
     * 解析单条 Update。
     *
     * @return updateId + 消息（可为 null：非消息/媒体/超限/自身 bot 消息——调用方仅推进 offset）
     */
    public static TelegramUpdate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new TelegramUpdate(-1, null);
        }
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            long updateId = root.has("update_id") ? root.get("update_id").getAsLong() : -1;
            TelegramInboundMessage message = parseMessage(root);
            return new TelegramUpdate(updateId, message);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return new TelegramUpdate(-1, null);
        }
    }

    private static TelegramInboundMessage parseMessage(JsonObject root) {
        // 仅处理 message（忽略 edited_message/channel_post/callback_query 等事件类型）
        if (!root.has("message") || !root.get("message").isJsonObject()) {
            return null;
        }
        JsonObject message = root.getAsJsonObject("message");
        if (!message.has("chat") || !message.get("chat").isJsonObject()) {
            return null;
        }
        JsonObject chat = message.getAsJsonObject("chat");
        if (!chat.has("type") || !chat.has("id")) {
            return null;
        }
        String chatType = normalizeChatType(chat.get("type").getAsString());
        if (chatType == null) {
            return null; // 非群/私聊（如 channel）不入会话
        }
        long chatId = chat.get("id").getAsLong();
        // 仅文本消息（媒体等无 text 字段丢弃）
        if (!message.has("text")) {
            return null;
        }
        String text = message.get("text").getAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() > MAX_TEXT_CHARS) {
            return null;
        }
        long msgId = message.has("message_id") ? message.get("message_id").getAsLong() : -1;
        if (msgId < 0) {
            return null;
        }
        // 发送者：from 缺失（channel_post 等）不处理；bot 来源滤除（R4 防回声环，由 processor 判定）
        if (!message.has("from") || !message.get("from").isJsonObject()) {
            return null;
        }
        JsonObject from = message.getAsJsonObject("from");
        if (!from.has("id")) {
            return null;
        }
        long senderId = from.get("id").getAsLong();
        boolean senderBot = from.has("is_bot") && from.get("is_bot").getAsBoolean();
        String trimmed = stripMentions(text);
        if (trimmed.isEmpty()) {
            return null;
        }
        return new TelegramInboundMessage(chatType, chatId, msgId, trimmed, senderId, senderBot);
    }

    /**
     * 剥离文本开头的 @提及 token（TG 群 @机器人触发时 text 为 {@code "@EasyBotTestBot $l"}，
     * 非纯命令——与飞书 @占位符同源问题，实测 #323 后群 @无回复）。
     *
     * <p>仅剥离开头连续 @token（如 {@code @bot} / {@code @bot $cmd}），中间/结尾 @ 保留（可能是玩家名）。
     * 剥离后 trim。纯 @提及无正文 → 空。</p>
     */
    private static String stripMentions(String text) {
        String t = text == null ? "" : text.trim();
        while (t.startsWith("@")) {
            // 跳过 @ 及后续非空白（username 可能含下划线/数字）
            int end = t.indexOf(' ');
            if (end < 0) {
                return ""; // 纯 @token 无正文
            }
            t = t.substring(end).trim();
        }
        return t;
    }

    private static String normalizeChatType(String type) {
        if (type == null) {
            return null;
        }
        // private → user（与 QQ/飞书 user 语义一致）；group/supergroup → group；channel 不入会话
        return switch (type) {
            case "private" -> TelegramInboundMessage.CHAT_TYPE_USER;
            case "group", "supergroup" -> TelegramInboundMessage.CHAT_TYPE_GROUP;
            default -> null;
        };
    }
}
