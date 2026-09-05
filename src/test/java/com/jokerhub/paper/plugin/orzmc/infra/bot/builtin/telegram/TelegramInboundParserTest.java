package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** TelegramInboundParser 单测：Update JSON → 归一消息（含 R8 update_id 推进、R4 bot 滤除、媒体丢弃）。 */
class TelegramInboundParserTest {

    @Test
    void parse_groupTextMessage() {
        String raw = "{\"update_id\":1001,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":11,\"is_bot\":false,\"first_name\":\"a\"},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\",\"title\":\"g\"},"
                + "\"text\":\"hi\"}}";
        TelegramInboundParser.TelegramUpdate u = TelegramInboundParser.parse(raw);
        assertEquals(1001, u.updateId());
        TelegramInboundMessage m = u.message();
        assertNotNull(m);
        assertEquals(TelegramInboundMessage.CHAT_TYPE_GROUP, m.chatType());
        assertEquals(-100L, m.chatId());
        assertEquals("hi", m.text());
        assertEquals(11L, m.senderId());
        assertFalse(m.senderBot());
        assertEquals("telegram:group:-100", m.target());
        assertTrue(m.isUser());
    }

    @Test
    void parse_privateChat_mapsToUserChatType() {
        String raw = "{\"update_id\":2,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":22,\"is_bot\":false,\"first_name\":\"b\"},"
                + "\"chat\":{\"id\":22,\"type\":\"private\"},"
                + "\"text\":\"$l\"}}";
        TelegramInboundParser.TelegramUpdate u = TelegramInboundParser.parse(raw);
        TelegramInboundMessage m = u.message();
        assertNotNull(m);
        assertEquals(TelegramInboundMessage.CHAT_TYPE_USER, m.chatType());
        assertEquals("telegram:user:22", m.target());
    }

    @Test
    void parse_supergroup_mapsToGroup() {
        String raw = "{\"update_id\":3,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":33,\"is_bot\":false},"
                + "\"chat\":{\"id\":-200,\"type\":\"supergroup\"},"
                + "\"text\":\"x\"}}";
        TelegramInboundMessage m = TelegramInboundParser.parse(raw).message();
        assertNotNull(m);
        assertEquals(TelegramInboundMessage.CHAT_TYPE_GROUP, m.chatType());
    }

    @Test
    void parse_botSender_isNotUser() {
        String raw = "{\"update_id\":4,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":99,\"is_bot\":true,\"first_name\":\"bot\"},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\"},"
                + "\"text\":\"hello\"}}";
        TelegramInboundMessage m = TelegramInboundParser.parse(raw).message();
        assertNotNull(m);
        assertTrue(m.senderBot());
        assertFalse(m.isUser()); // R4：bot 来源不进命令层
    }

    @Test
    void parse_mediaMessage_dropped() {
        // 无 text 字段（photo）→ 消息丢弃，但 update_id 仍有效供 offset 推进（R8）
        String raw = "{\"update_id\":5,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":11,\"is_bot\":false},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\"},"
                + "\"photo\":[{}]}}";
        TelegramInboundParser.TelegramUpdate u = TelegramInboundParser.parse(raw);
        assertEquals(5, u.updateId());
        assertNull(u.message());
    }

    @Test
    void parse_editedMessage_dropped() {
        String raw = "{\"update_id\":6,\"edited_message\":{\"message_id\":1,"
                + "\"from\":{\"id\":11,\"is_bot\":false},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\"},"
                + "\"text\":\"edited\"}}";
        TelegramInboundParser.TelegramUpdate u = TelegramInboundParser.parse(raw);
        assertEquals(6, u.updateId());
        assertNull(u.message(), "edited_message 不处理（仅 message 事件）");
    }

    @Test
    void parse_channelMessage_dropped() {
        String raw = "{\"update_id\":7,\"message\":{\"message_id\":1,"
                + "\"author_signature\":\"x\","
                + "\"chat\":{\"id\":-300,\"type\":\"channel\"},"
                + "\"text\":\"announce\"}}";
        TelegramInboundParser.TelegramUpdate u = TelegramInboundParser.parse(raw);
        assertEquals(7, u.updateId());
        assertNull(u.message(), "channel 不入会话");
    }

    @Test
    void parse_emptyText_dropped() {
        String raw = "{\"update_id\":8,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":11,\"is_bot\":false},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\"},"
                + "\"text\":\"   \"}}";
        assertNull(TelegramInboundParser.parse(raw).message());
    }

    @Test
    void parse_groupMentionPrefix_stripped() {
        // TG 群 @机器人触发：text 含 @bot 前缀（同飞书 @占位符问题）——须剥离才能以 $ 进命令层
        String raw = "{\"update_id\":9,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":11,\"is_bot\":false},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\"},"
                + "\"text\":\"@EasyBotTestBot $l\"}}";
        TelegramInboundMessage m = TelegramInboundParser.parse(raw).message();
        assertNotNull(m);
        assertEquals("$l", m.text(), "@提及剥离后剩命令本体");
    }

    @Test
    void parse_mentionOnly_noCommand_dropped() {
        String raw = "{\"update_id\":10,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":11,\"is_bot\":false},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\"},"
                + "\"text\":\"@EasyBotTestBot\"}}";
        assertNull(TelegramInboundParser.parse(raw).message(), "纯 @提及无正文 → 丢弃");
    }

    @Test
    void parse_inlineMentionNotAtStart_kept() {
        // 文本中间的 @（非触发位置）保留——只剥开头连续 @token
        String raw = "{\"update_id\":11,\"message\":{\"message_id\":1,"
                + "\"from\":{\"id\":11,\"is_bot\":false},"
                + "\"chat\":{\"id\":-100,\"type\":\"group\"},"
                + "\"text\":\"$l 给 @player 看看\"}}";
        TelegramInboundMessage m = TelegramInboundParser.parse(raw).message();
        assertNotNull(m);
        assertEquals("$l 给 @player 看看", m.text(), "中间 @ 保留");
    }

    @Test
    void parse_malformedJson_returnsInvalidUpdate() {
        TelegramInboundParser.TelegramUpdate u = TelegramInboundParser.parse("{not-json");
        assertEquals(-1, u.updateId());
        assertNull(u.message());
    }
}
