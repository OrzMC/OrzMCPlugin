package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 飞书入站事件解析（builtin 飞书 adapter，方案 §6；协议对照 EasyBot event.rs + 官方事件 v2 信封）。
 *
 * <p>网关透传的 payload 即事件 <b>v2 信封</b>：{@code {schema, header{event_id, event_type,...},
 * event{...}}}。本解析器只处理 {@code im.message.receive_v1}，把 {@code event} 内的
 * {@code sender{...}} / {@code message{...}} 归一为 {@link FeishuInboundMessage}：</p>
 * <ul>
 *   <li>文本：{@code message.content} 为 <b>JSON 字符串</b>（如 {@code {"text":"hello"}}），text 消息解出
 *       {@code text} 字段；非 text（图片/富文本等）按无文本丢弃（D6，仅文本入命令层）；</li>
 *   <li>会话：{@code message.chat_type} {@code group}/{@code p2p} → group/user；{@code chat_id}（oc_*）
 *       群/单聊均保留原值（出站 receive_id 即它）；</li>
 *   <li>发送者：{@code sender.sender_id.open_id} 优先、回退 {@code user_id}；{@code sender.sender_type}
 *       user/app/bot（R4 过滤在 processor）。</li>
 * </ul>
 */
public final class FeishuInboundParser {

    /** 消息文本单条上限（对齐 R7 保守值；飞书 text 实际容量远大于此，防御式截断防批量命令）。 */
    static final int MAX_TEXT_CHARS = 2000;

    /** 事件类型：消息接收（群/p2p 统一）。 */
    static final String EVENT_MESSAGE_RECEIVE_V1 = "im.message.receive_v1";

    private FeishuInboundParser() {}

    /**
     * 解析事件 v2 信封 → 归一消息。
     *
     * @param payload 事件 v2 信封 JSON（网关透传，非空）
     * @return 归一消息；非消息事件 / 媒体消息 / 结构异常 / 空文本 → null（调用方静默丢弃）
     */
    public static FeishuInboundMessage parse(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("event") || !root.get("event").isJsonObject()) {
                return null; // 信封缺 event（理论不发生）
            }
            JsonObject event = root.getAsJsonObject("event");
            if (!root.has("header") || !root.get("header").isJsonObject()) {
                return null;
            }
            String eventType = text(root.getAsJsonObject("header"), "event_type");
            if (!EVENT_MESSAGE_RECEIVE_V1.equals(eventType)) {
                return null; // 非消息事件（只进 processor 的日志计数）
            }
            return parseMessage(event);
        } catch (RuntimeException e) {
            return null; // 非 JSON / 结构异常：丢弃
        }
    }

    private static FeishuInboundMessage parseMessage(JsonObject event) {
        if (!event.has("sender")
                || !event.get("sender").isJsonObject()
                || !event.has("message")
                || !event.get("message").isJsonObject()) {
            return null;
        }
        JsonObject sender = event.getAsJsonObject("sender");
        JsonObject message = event.getAsJsonObject("message");
        String chatType = normalizeChatType(text(message, "chat_type"));
        String chatId = text(message, "chat_id");
        String msgId = text(message, "message_id");
        if (chatType == null || chatId == null || msgId == null) {
            return null;
        }
        // 仅文本消息（D6）：message_type=text 且 content 解出 text 字段
        if (!"text".equals(text(message, "message_type"))) {
            return null;
        }
        String contentText = extractText(text(message, "content"));
        if (contentText == null || contentText.isEmpty()) {
            return null;
        }
        // 剥离飞书 @ 占位符（@机器人/@成员在 text 中为 {@code @_user_N}，非用户真实输入）后 trim——
        // 否则 {@code @机器人 $h} 文本为 "@_user_1 $h" 无法以 $ 前缀进命令层（实测）
        String trimmed = contentText.replaceAll("@_user_\\d+", "").trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_TEXT_CHARS) {
            return null;
        }
        String senderId = resolveSenderId(sender);
        if (senderId == null) {
            return null;
        }
        return new FeishuInboundMessage(chatType, chatId, msgId, trimmed, senderId, text(sender, "sender_type"));
    }

    /** {@code group}/{@code p2p} → group/user；其它（如未知枚举）→ null（丢弃）。 */
    private static String normalizeChatType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "group" -> FeishuInboundMessage.CHAT_TYPE_GROUP;
            case "p2p" -> FeishuInboundMessage.CHAT_TYPE_USER;
            default -> null;
        };
    }

    /** content JSON 字符串中解 {@code text} 字段；非 JSON/缺 text → null。 */
    private static String extractText(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            return text(obj, "text");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** sender_id：open_id 优先（EasyBot event.rs 语义），回退 user_id；均无 → null（无法标识发送者）。 */
    private static String resolveSenderId(JsonObject sender) {
        if (!sender.has("sender_id") || !sender.get("sender_id").isJsonObject()) {
            return null;
        }
        JsonObject senderId = sender.getAsJsonObject("sender_id");
        String openId = text(senderId, "open_id");
        if (openId != null && !openId.isBlank()) {
            return openId;
        }
        String userId = text(senderId, "user_id");
        return userId == null || userId.isBlank() ? null : userId;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }
}
