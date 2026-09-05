package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * QQ 群/C2C 入站事件帧解析（纯解析，无 IO 无状态；协议字段对齐 EasyBot easybot-adapter-qq types.rs）。
 *
 * <p>识别 {@code GROUP_AT_MESSAGE_CREATE}（@机器人）/ {@code GROUP_MESSAGE_CREATE}（全量群消息，2026 新协议）/
 * {@code C2C_MESSAGE_CREATE}（私聊），归一为 {@link QqInboundMessage}：</p>
 * <ul>
 *   <li>群事件：author{member_openid, member_role(owner|admin|member), bot, username} + group_openid + id + content；</li>
 *   <li>C2C 事件：author{user_openid} + id + content（无角色字段，恒非管理）。</li>
 * </ul>
 *
 * <p>解析失败/非消息事件/空文本/超长文本一律返回 null（调用方丢弃）。文本上限对齐 EasyBot 通道
 * （8KB 内按原文进入，超限丢弃——入站文本截断会破坏命令解析）。</p>
 */
public final class QqInboundParser {

    public static final int MAX_TEXT_CHARS = 8 * 1024;

    private QqInboundParser() {}

    /**
     * @param type 事件类型（网关 op0 的 t 字段，READY/RESUMED 已被网关客户端内部消费）
     * @param rawFrame 原始网关帧 JSON（含 op/s/t/d）
     * @return 归一消息；非文本消息事件 / 缺字段 / 空文本或超长 / 解析失败 → null
     */
    public static QqInboundMessage parse(String type, String rawFrame) {
        if (type == null || rawFrame == null || rawFrame.isEmpty()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(rawFrame).getAsJsonObject();
            if (!root.has("d") || !root.get("d").isJsonObject()) {
                return null;
            }
            JsonObject d = root.getAsJsonObject("d");
            return switch (type) {
                case "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE" -> parseGroup(type, d);
                case "C2C_MESSAGE_CREATE" -> parseC2c(d);
                default -> null;
            };
        } catch (RuntimeException e) {
            return null; // 非 JSON / 结构异常：丢弃
        }
    }

    private static QqInboundMessage parseGroup(String type, JsonObject d) {
        String groupOpenId = text(d, "group_openid");
        String msgId = text(d, "id");
        if (groupOpenId == null
                || msgId == null
                || !d.has("author")
                || !d.get("author").isJsonObject()) {
            return null;
        }
        // 仅文本（D6）：GROUP_MESSAGE_CREATE 带 message_type，非 0（媒体）按无文本处理
        if ("GROUP_MESSAGE_CREATE".equals(type)
                && d.has("message_type")
                && d.get("message_type").isJsonPrimitive()
                && d.get("message_type").getAsInt() != 0) {
            return null;
        }
        JsonObject author = d.getAsJsonObject("author");
        String memberOpenId = text(author, "member_openid");
        if (memberOpenId == null) {
            return null;
        }
        String content = content(d);
        if (content == null) {
            return null;
        }
        return new QqInboundMessage(
                QqInboundMessage.CHAT_TYPE_GROUP,
                groupOpenId,
                msgId,
                content,
                memberOpenId,
                displayName(author, memberOpenId),
                text(author, "member_role"), // owner | admin | member；缺失 → null（非管理）
                boolValue(author, "bot"));
    }

    private static QqInboundMessage parseC2c(JsonObject d) {
        String msgId = text(d, "id");
        String content = content(d);
        if (msgId == null
                || content == null
                || !d.has("author")
                || !d.get("author").isJsonObject()) {
            return null;
        }
        JsonObject author = d.getAsJsonObject("author");
        String userOpenId = text(author, "user_openid");
        if (userOpenId == null) {
            return null;
        }
        return new QqInboundMessage(
                QqInboundMessage.CHAT_TYPE_USER,
                userOpenId,
                msgId,
                content,
                userOpenId,
                userOpenId, // C2C 无昵称（隐私限制），用 openid 兜底
                null, // C2C 无角色 → 非管理
                false); // C2C 无双向机器人判定字段，恒按非 bot
    }

    /** 消息文本：缺失/空/超长 → null（丢弃）。 */
    private static String content(JsonObject d) {
        String raw = text(d, "content");
        if (raw == null || raw.isEmpty() || raw.length() > MAX_TEXT_CHARS) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 显示名：群昵称优先，缺失/空回退发送者 openid（对齐 EasyBot）。 */
    private static String displayName(JsonObject author, String fallback) {
        String name = text(author, "username");
        return name == null || name.isBlank() ? fallback : name;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || value.isJsonNull()) {
            return null;
        }
        return value.getAsString();
    }

    private static boolean boolValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }
}
