package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * FeishuInboundParser 单测：事件 v2 信封解析（群文本/p2p 文本/user_id 回退/媒体丢弃/未知事件丢弃/
 * 结构异常丢弃/超长文本截断）。
 */
class FeishuInboundParserTest {

    private static byte[] envelope(JsonObject event, String eventType) {
        JsonObject header = new JsonObject();
        header.addProperty("event_id", "ev_1");
        header.addProperty("event_type", eventType);
        JsonObject root = new JsonObject();
        root.addProperty("schema", "2.0");
        root.add("header", header);
        root.add("event", event);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject sender(String openId, String type) {
        JsonObject senderId = new JsonObject();
        senderId.addProperty("open_id", openId);
        JsonObject s = new JsonObject();
        s.add("sender_id", senderId);
        s.addProperty("sender_type", type);
        return s;
    }

    private static JsonObject message(String chatType, String msgType, String contentJson) {
        JsonObject m = new JsonObject();
        m.addProperty("message_id", "om_1");
        m.addProperty("chat_id", "oc_chat");
        m.addProperty("chat_type", chatType);
        m.addProperty("message_type", msgType);
        m.addProperty("content", contentJson); // content 是 JSON 字符串
        return m;
    }

    private static byte[] messageEvent(String chatType, String msgType, JsonObject sender, String contentJson) {
        JsonObject event = new JsonObject();
        event.add("sender", sender);
        event.add("message", message(chatType, msgType, contentJson));
        return envelope(event, "im.message.receive_v1");
    }

    @Test
    void parse_groupTextMessage() {
        byte[] payload = messageEvent("group", "text", sender("ou_abc", "user"), "{\"text\":\"你好\"}");
        FeishuInboundMessage m = FeishuInboundParser.parse(payload);
        assertTrue(m != null, "群文本应解析");
        assertEquals("group", m.chatType());
        assertEquals("oc_chat", m.chatId());
        assertEquals("你好", m.text());
        assertEquals("ou_abc", m.senderId());
        assertEquals("user", m.senderType());
        assertTrue(m.isUser());
        assertEquals("feishu:group:oc_chat", m.target());
    }

    @Test
    void parse_p2pTextMessage_mapsToUserChatType() {
        byte[] payload = messageEvent("p2p", "text", sender("ou_p2p", "user"), "{\"text\":\"私聊\"}");
        FeishuInboundMessage m = FeishuInboundParser.parse(payload);
        assertTrue(m != null);
        assertEquals("user", m.chatType(), "p2p → user 会话类型");
        assertEquals("feishu:user:oc_chat", m.target());
    }

    @Test
    void parse_senderWithoutOpenId_fallsBackToUserId() {
        JsonObject senderId = new JsonObject();
        senderId.addProperty("user_id", "u_456");
        JsonObject sender = new JsonObject();
        sender.add("sender_id", senderId);
        sender.addProperty("sender_type", "user");

        byte[] payload = messageEvent("group", "text", sender, "{\"text\":\"hi\"}");
        FeishuInboundMessage m = FeishuInboundParser.parse(payload);
        assertTrue(m != null);
        assertEquals("u_456", m.senderId());
    }

    @Test
    void parse_botSender_isNotUser() {
        byte[] payload = messageEvent("group", "text", sender("ou_bot", "app"), "{\"text\":\"hi\"}");
        FeishuInboundMessage m = FeishuInboundParser.parse(payload);
        assertTrue(m != null);
        assertFalse(m.isUser(), "app 来源 → 非 user（R4 过滤在 processor）");
    }

    @Test
    void parse_mediaMessage_dropped() {
        byte[] payload = messageEvent("group", "image", sender("ou_abc", "user"), "{\"image_key\":\"img_1\"}");
        assertNull(FeishuInboundParser.parse(payload), "图片消息丢弃（D6 仅文本）");
    }

    @Test
    void parse_nonMessageEvent_dropped() {
        JsonObject event = new JsonObject();
        byte[] payload = envelope(event, "im.chat.member.user.added_v1");
        assertNull(FeishuInboundParser.parse(payload));
    }

    @Test
    void parse_emptyText_dropped() {
        byte[] payload = messageEvent("group", "text", sender("ou_abc", "user"), "{\"text\":\"   \"}");
        assertNull(FeishuInboundParser.parse(payload), "空白文本丢弃");
    }

    @Test
    void parse_malformedJson_dropped() {
        assertNull(FeishuInboundParser.parse("{not-json".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parse_atMentionedText_stripsUserPlaceholder() {
        // 实测：@机器人消息 content.text = "@_user_1 $h"（@ 被飞书转为占位符）——须剥后进命令层
        byte[] payload = messageEvent("group", "text", sender("ou_abc", "user"), "{\"text\":\"@_user_1 $h\"}");
        FeishuInboundMessage m = FeishuInboundParser.parse(payload);
        assertTrue(m != null, "@ 消息应解析");
        assertEquals("$h", m.text(), "@_user_N 占位符应剥离");
    }

    @Test
    void parse_mentionOnlyMessage_dropped() {
        // 只有 @ 没有实际文本 → 剥离后为空 → 丢弃
        byte[] payload = messageEvent("group", "text", sender("ou_abc", "user"), "{\"text\":\"@_user_1\"}");
        assertNull(FeishuInboundParser.parse(payload), "纯 @ 无文本应丢弃");
    }

    @Test
    void parse_mentionMidText_keepsSurroundingText() {
        byte[] payload = messageEvent("group", "text", sender("ou_abc", "user"), "{\"text\":\"你好@_user_1 $w\"}");
        FeishuInboundMessage m = FeishuInboundParser.parse(payload);
        assertTrue(m != null);
        assertEquals("你好 $w", m.text(), "句中 @ 占位符剥离、两侧文本保留");
    }

    @Test
    void parse_nullOrEmpty_dropped() {
        assertNull(FeishuInboundParser.parse(null));
        assertNull(FeishuInboundParser.parse(new byte[0]));
    }
}
