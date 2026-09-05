package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Discord MESSAGE_CREATE 事件解析（builtin DC adapter，批次 5b；对齐 QQ/飞书/TG 解析层职责）。
 *
 * <p>从网关 op0 事件帧（JSON）解析文本用户消息为 {@link DiscordInboundMessage}。只处理文本
 * 用户消息（媒体/系统消息丢弃，仅文本 D6）；bot 来源保留 isBot 标记由 Processor 滤除（R4）。
 * 会话归类：DM 通道（channel_type=1 / 无 guild_id）→ {@code user}；服务器文本频道 → {@code group}。</p>
 */
public final class DiscordInboundParser {

    private DiscordInboundParser() {}

    /** 解析单条 MESSAGE_CREATE 事件帧；非消息事件 / 非文本 / 结构异常 → null。 */
    public static DiscordInboundMessage parseMessageCreate(String rawFrame) {
        if (rawFrame == null || rawFrame.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(rawFrame).getAsJsonObject();
            if (!root.has("t") || !"MESSAGE_CREATE".equals(root.get("t").getAsString())) {
                return null;
            }
            if (!root.has("d") || !root.get("d").isJsonObject()) {
                return null;
            }
            JsonObject d = root.getAsJsonObject("d");
            if (!d.has("channel_id") || !d.has("id")) {
                return null;
            }
            String channelId = str(d, "channel_id");
            String guildId = d.has("guild_id") && !d.get("guild_id").isJsonNull()
                    ? d.get("guild_id").getAsString()
                    : null;
            String msgId = str(d, "id");
            boolean dm = isDm(d, guildId);
            // 仅文本消息：无 content 或空 content（媒体等）丢弃
            if (!d.has("content") || !d.get("content").isJsonPrimitive()) {
                return null;
            }
            String text = d.get("content").getAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            // Discord @提及为 snowflake 标记（<@id> / <@!id> 用户、<@&id> 角色），剥除后命令文本才能被识别
            // （对齐 TG/飞书 @提及剥离：#324 同源问题，2026-09-06 真机发现）
            text = stripMentions(text);
            if (text.isEmpty()) {
                return null; // 纯 @提及无正文
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                return null; // 超长文本不入会话（防御；Discord 单条 2000，此处留富余）
            }
            JsonObject author = d.has("author") && d.get("author").isJsonObject() ? d.getAsJsonObject("author") : null;
            if (author == null || !author.has("id")) {
                return null;
            }
            String senderId = str(author, "id");
            String senderName = author.has("username") ? str(author, "username") : senderId;
            boolean isBot = author.has("bot")
                    && !author.get("bot").isJsonNull()
                    && author.get("bot").getAsBoolean();
            String chatType = dm ? DiscordInboundMessage.CHAT_TYPE_USER : DiscordInboundMessage.CHAT_TYPE_GROUP;
            return new DiscordInboundMessage(chatType, channelId, guildId, msgId, text, senderId, senderName, isBot);
        } catch (JsonSyntaxException | IllegalStateException | UnsupportedOperationException e) {
            return null; // 非 JSON / 结构异常 → 忽略
        }
    }

    /**
     * 剥除文本<b>开头</b>的连续 Discord 提及并 trim：{@code <@id>}（用户）、{@code <@!id>}（旧式昵称提及）、
     * {@code <@&id>}（角色提及）。群 @bot 命令形如 {@code <@151590...> $l}——剥除后命令文本才能被命令层识别
     * （对齐 TG/飞书 @提及剥离：#324 同源问题，2026-09-06 真机发现）。中间/结尾提及保留原样
     * （可能是引用他人/角色，命令解析以文本 {@code $} 开头为准）。
     */
    static String stripMentions(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("^(?:<@[!&]?\\d+>\\s*)+", "").trim();
    }

    /** DM 判定：channel_type=1（DM）或无 guild_id（非服务器消息）视为私聊。 */
    private static boolean isDm(JsonObject d, String guildId) {
        if (guildId != null) {
            return false;
        }
        // 双保险：部分消息对象带 channel_type，1 = DM
        if (d.has("channel_type")
                && !d.get("channel_type").isJsonNull()
                && d.get("channel_type").isJsonPrimitive()) {
            return d.get("channel_type").getAsInt() == 1;
        }
        return true;
    }

    private static String str(JsonObject o, String key) {
        if (o.has(key) && !o.get(key).isJsonNull() && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString();
        }
        return null;
    }

    /** 单条文本上限（Discord 官方 2000；防御截断点留富余）。 */
    static final int MAX_TEXT_LENGTH = 4000;
}
