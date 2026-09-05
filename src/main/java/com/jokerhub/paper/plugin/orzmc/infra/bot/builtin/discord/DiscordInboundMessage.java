package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

/**
 * Discord 入站消息归一模型（builtin Discord adapter，批次 5b；事件→业务层前的平台无关形态）。
 *
 * <p>由 {@link DiscordInboundParser} 从 MESSAGE_CREATE 事件帧解析；业务分发（会话门槛 / 线程调度 /
 * 回复 sink）由 {@link DiscordInboundProcessor} 完成。会话值语义与 QQ/飞书/Telegram builtin 一致：
 * {@code target()} = {@code discord:<chatType>:<id>}——</p>
 * <ul>
 *   <li>群聊（服务器文本频道，含子频道/帖子）：{@code discord:group:<channel_id>}——绑定/投递以<b>频道</b>
 *       为粒度（服务器内各频道独立成会话，channel_id 为全局唯一 snowflake）；</li>
 *   <li>私聊（DM）：{@code discord:user:<author_id>}——以<b>用户 id</b> 为粒度（与 QQ/TG user 语义一致；
 *       出站 DM 经 REST 建/取 DM channel 后投递，见 {@link DiscordApiClient#ensureDmChannel}）。</li>
 * </ul>
 *
 * @param chatType 会话类型：{@code group}（服务器文本频道）或 {@code user}（DM 私聊）
 * @param channelId 来源频道 id（DM 时为 DM channel id；群聊回复/出站均以此为投递目标）
 * @param guildId 来源服务器 id（DM 为 null；角色判定用）
 * @param msgId 消息 id（保留字段，当前无被动回复引用语义）
 * @param text 文本内容（已 trim；空/超限在解析层丢弃，仅文本 D6）
 * @param senderId 发送者用户 id
 * @param senderName 发送者显示名（username；缺失回退 senderId）
 * @param isBot 发送者是否为 bot（true 时入站应滤除，防回声环 R4）
 */
public record DiscordInboundMessage(
        String chatType,
        String channelId,
        String guildId,
        String msgId,
        String text,
        String senderId,
        String senderName,
        boolean isBot) {

    public static final String CHAT_TYPE_GROUP = "group";
    public static final String CHAT_TYPE_USER = "user";

    /** 归一会话目标（对齐现有 target 语义：platform 前缀 + 会话类型 + 会话 id）。 */
    public String target() {
        String id = CHAT_TYPE_USER.equals(chatType) ? senderId : channelId;
        return "discord:" + chatType + ":" + id;
    }
}
