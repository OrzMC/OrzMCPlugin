package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DiscordInboundParser 单测：MESSAGE_CREATE 群聊/DM 归类、bot 标记、媒体/超长/结构异常丢弃。 */
class DiscordInboundParserTest {

    /** 构造 MESSAGE_CREATE 网关帧（content 可 null → 无 content 字段）。 */
    private static String frame(
            String channelId, String guildId, String authorId, boolean isBot, String content, Integer channelType) {
        StringBuilder d = new StringBuilder("{\"id\":\"m1\",\"channel_id\":\"" + channelId + "\"");
        if (guildId != null) {
            d.append(",\"guild_id\":\"").append(guildId).append("\"");
        }
        if (channelType != null) {
            d.append(",\"channel_type\":").append(channelType);
        }
        d.append(",\"author\":{\"id\":\"").append(authorId).append("\",\"username\":\"alice\"");
        if (isBot) {
            d.append(",\"bot\":true");
        }
        d.append("}");
        if (content != null) {
            d.append(",\"content\":").append(jsonStr(content));
        }
        d.append("}");
        return "{\"op\":0,\"s\":5,\"t\":\"MESSAGE_CREATE\",\"d\":" + d + "}";
    }

    private static String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void guildTextChannel_groupSession() {
        DiscordInboundMessage m =
                DiscordInboundParser.parseMessageCreate(frame("111", "222", "333", false, "$help", 0));
        assertTrue(m != null);
        assertEquals(DiscordInboundMessage.CHAT_TYPE_GROUP, m.chatType());
        assertEquals("discord:group:111", m.target());
        assertEquals("222", m.guildId());
        assertEquals("$help", m.text());
        assertEquals("333", m.senderId());
        assertFalse(m.isBot());
    }

    @Test
    void dmMessage_userSession() {
        // 无 guild_id + channel_type=1 → DM → user 会话（target 用 author id）
        DiscordInboundMessage m = DiscordInboundParser.parseMessageCreate(frame("777", null, "333", false, "$h", 1));
        assertEquals(DiscordInboundMessage.CHAT_TYPE_USER, m.chatType());
        assertEquals("discord:user:333", m.target());
        assertNull(m.guildId());
    }

    @Test
    void dmWithoutChannelType_detectedByMissingGuildId() {
        DiscordInboundMessage m = DiscordInboundParser.parseMessageCreate(frame("777", null, "333", false, "hi", null));
        assertEquals(DiscordInboundMessage.CHAT_TYPE_USER, m.chatType());
        assertEquals("discord:user:333", m.target());
    }

    @Test
    void guildMention_strippedBeforeDispatch() {
        // Discord @提及 = snowflake 标记前缀（2026-09-06 真机发现，对齐 TG/飞书剥离）
        DiscordInboundMessage m = DiscordInboundParser.parseMessageCreate(
                frame("111", "222", "333", false, "<@1515905813489782835> $l", 0));
        assertEquals("$l", m.text(), "用户提及 <@id> 剥离");
    }

    @Test
    void legacyAndRoleMentions_stripped() {
        DiscordInboundMessage m = DiscordInboundParser.parseMessageCreate(
                frame("111", "222", "333", false, "<@!151590> <@&998877> $h", 0));
        assertEquals("$h", m.text(), "旧式 <@!id> 与角色 <@&id> 提及均剥离");
    }

    @Test
    void pureMention_dropped() {
        assertNull(
                DiscordInboundParser.parseMessageCreate(frame("111", "222", "333", false, "<@1515905813489782835>", 0)),
                "纯 @提及无正文 → 丢弃");
    }

    @Test
    void mentionMidText_keptAsIs() {
        // 中间提及保留（可能是引用他人；命令识别以 $ 开头为准，对齐 TG 只剥开头决策）
        DiscordInboundMessage m =
                DiscordInboundParser.parseMessageCreate(frame("111", "222", "333", false, "hi <@123> there", 0));
        assertEquals("hi <@123> there", m.text());
    }

    @Test
    void botAuthor_notFilteredByParserButFlagged() {
        DiscordInboundMessage m = DiscordInboundParser.parseMessageCreate(frame("111", "222", "999", true, "$l", 0));
        assertTrue(m != null && m.isBot(), "parser 保留 bot 标记，由 processor 滤除（R4）");
    }

    @Test
    void nonMessageCreate_returnsNull() {
        assertNull(DiscordInboundParser.parseMessageCreate("{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{}}"));
    }

    @Test
    void blankOrMissingContent_returnsNull() {
        assertNull(DiscordInboundParser.parseMessageCreate(frame("111", "222", "333", false, "", 0)));
        assertNull(DiscordInboundParser.parseMessageCreate(frame("111", "222", "333", false, "   ", 0)));
        assertNull(DiscordInboundParser.parseMessageCreate(frame("111", "222", "333", false, null, 0)));
    }

    @Test
    void overlongContent_returnsNull() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 4500; i++) {
            huge.append('a');
        }
        assertNull(DiscordInboundParser.parseMessageCreate(frame("111", "222", "333", false, huge.toString(), 0)));
    }

    @Test
    void malformedJson_returnsNull() {
        assertNull(DiscordInboundParser.parseMessageCreate("{not json"));
        assertNull(DiscordInboundParser.parseMessageCreate(null));
        assertNull(DiscordInboundParser.parseMessageCreate(""));
    }
}
