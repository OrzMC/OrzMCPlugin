package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** QqInboundParser 纯解析单测：群 @/全量、C2C、角色归一、bot 标记、媒体/缺字段/超限丢弃。 */
class QqInboundParserTest {

    private static final String GROUP_FRAME =
            "{\"op\":0,\"s\":2,\"t\":\"%s\",\"d\":{\"id\":\"mid-1\",\"group_openid\":\"G-1\","
                    + "\"content\":\"$help 我\",\"author\":{\"member_openid\":\"M-1\",\"member_role\":\"%s\","
                    + "\"username\":\"%s\",\"bot\":%s}}}";

    @Test
    void groupAtMessage_normalizesOwnerRole() {
        QqInboundMessage msg = QqInboundParser.parse(
                "GROUP_AT_MESSAGE_CREATE", String.format(GROUP_FRAME, "GROUP_AT_MESSAGE_CREATE", "owner", "群主", false));

        assertEquals(QqInboundMessage.CHAT_TYPE_GROUP, msg.chatType());
        assertEquals("G-1", msg.chatId());
        assertEquals("qq:group:G-1", msg.target());
        assertEquals("mid-1", msg.msgId());
        assertEquals("$help 我", msg.text());
        assertEquals("M-1", msg.senderId());
        assertEquals("群主", msg.senderName());
        assertEquals("owner", msg.role());
        assertTrue(msg.isAdmin());
        assertFalse(msg.isBot());
    }

    @Test
    void groupMemberRole_isNotAdmin_failClosed() {
        QqInboundMessage msg = QqInboundParser.parse(
                "GROUP_AT_MESSAGE_CREATE",
                String.format(GROUP_FRAME, "GROUP_AT_MESSAGE_CREATE", "member", "玩家", false));

        assertFalse(msg.isAdmin(), "member 角色非管理（fail-closed）");
    }

    @Test
    void unknownRole_orMissingRole_fallsBackToNonAdmin() {
        QqInboundMessage unknown = QqInboundParser.parse(
                "GROUP_AT_MESSAGE_CREATE", String.format(GROUP_FRAME, "GROUP_AT_MESSAGE_CREATE", "owner", "", false));
        // 无 username 时显示名回退 sender openid
        assertEquals("M-1", unknown.senderName());

        // 角色字段缺失（如旧协议群事件）→ 非管理；无昵称 → openid 兜底
        QqInboundMessage noRole = QqInboundParser.parse(
                "GROUP_AT_MESSAGE_CREATE",
                "{\"d\":{\"id\":\"m\",\"group_openid\":\"G\",\"content\":\"hi\","
                        + "\"author\":{\"member_openid\":\"M\"}}}");
        assertFalse(noRole.isAdmin(), "角色缺失按非管理");
        assertNull(noRole.role());
        assertEquals("M", noRole.senderName());
    }

    @Test
    void groupFullMessage_normalizesAndMediaTypeDropped() {
        QqInboundMessage text = QqInboundParser.parse(
                "GROUP_MESSAGE_CREATE",
                "{\"d\":{\"id\":\"m2\",\"group_openid\":\"G-1\",\"content\":\"全量\",\"message_type\":0,"
                        + "\"author\":{\"member_openid\":\"M-2\",\"member_role\":\"admin\",\"bot\":false}}}");
        assertEquals("qq:group:G-1", text.target());
        assertTrue(text.isAdmin(), "admin 角色为管理");

        QqInboundMessage media = QqInboundParser.parse(
                "GROUP_MESSAGE_CREATE",
                "{\"d\":{\"id\":\"m3\",\"group_openid\":\"G-1\",\"content\":\"<img/>\",\"message_type\":1,"
                        + "\"author\":{\"member_openid\":\"M-2\",\"bot\":false}}}");
        assertNull(media, "媒体消息（message_type!=0）仅文本不支持（D6）");
    }

    @Test
    void botAuthor_markedForSourceFilter() {
        QqInboundMessage msg = QqInboundParser.parse(
                "GROUP_AT_MESSAGE_CREATE", String.format(GROUP_FRAME, "GROUP_AT_MESSAGE_CREATE", "member", "", true));

        assertTrue(msg.isBot(), "author.bot=true 应标记（R4 滤除）");
    }

    @Test
    void c2cMessage_normalizesWithoutRole() {
        QqInboundMessage msg = QqInboundParser.parse(
                "C2C_MESSAGE_CREATE",
                "{\"d\":{\"id\":\"c-1\",\"content\":\"私聊\",\"author\":{\"user_openid\":\"U-1\"}}}");

        assertEquals(QqInboundMessage.CHAT_TYPE_USER, msg.chatType());
        assertEquals("qq:user:U-1", msg.target());
        assertEquals("U-1", msg.senderId());
        assertNull(msg.role());
        assertFalse(msg.isAdmin(), "C2C 无角色恒非管理");
        assertFalse(msg.isBot());
    }

    @Test
    void unsupportedOrMalformedFrames_returnNull() {
        assertNull(QqInboundParser.parse("AT_MESSAGE_CREATE", "{\"d\":{}}")); // 频道消息不在支持范围
        assertNull(QqInboundParser.parse(null, "{}"));
        assertNull(QqInboundParser.parse("GROUP_AT_MESSAGE_CREATE", null));
        assertNull(QqInboundParser.parse("GROUP_AT_MESSAGE_CREATE", "not-json"));
        assertNull(QqInboundParser.parse("GROUP_AT_MESSAGE_CREATE", "[]")); // 非对象
        assertNull(QqInboundParser.parse("GROUP_AT_MESSAGE_CREATE", "{\"op\":0}")); // 缺 d
        assertNull(QqInboundParser.parse(
                "GROUP_AT_MESSAGE_CREATE",
                "{\"d\":{\"id\":\"m\",\"group_openid\":\"G\",\"content\":\"\",\"author\":{}}}")); // 空文本
        assertNull(
                QqInboundParser.parse("C2C_MESSAGE_CREATE", "{\"d\":{\"id\":\"c\",\"author\":{}}}")); // C2C 缺 content
    }

    @Test
    void oversizedText_isDropped() {
        String big = "a".repeat(QqInboundParser.MAX_TEXT_CHARS + 1);
        assertNull(QqInboundParser.parse(
                "GROUP_AT_MESSAGE_CREATE",
                "{\"d\":{\"id\":\"m\",\"group_openid\":\"G\",\"content\":\"" + big
                        + "\",\"author\":{\"member_openid\":\"M\"}}}"));
    }
}
