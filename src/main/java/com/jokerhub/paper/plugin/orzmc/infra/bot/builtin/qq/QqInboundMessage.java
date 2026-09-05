package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.qq;

/**
 * QQ 入站消息归一模型（builtin QQ adapter，事件→业务层前的平台无关形态，方案 §6）。
 *
 * <p>由 {@link QqInboundParser} 从 op0 网关事件帧解析；业务分发（会话门槛 / 线程调度 /
 * 回复 sink）由 {@link QqInboundProcessor} 完成。会话值语义与 EasyBot 通道一致：
 * {@code target()} = {@code qq:<chatType>:<openid>}（群 {@code group} / 私聊 {@code user}），
 * 与 im_bindings 会话 / 现 easybot.yml 目标同构——切 backend 无需改绑定。</p>
 *
 * @param chatType 会话类型：{@code group}（群）或 {@code user}（C2C 私聊）
 * @param chatId 平台原生 openid（group_openid / user_openid）
 * @param msgId 事件消息 id（被动回复带回 msg_id，D14）
 * @param text 文本内容（已 trim；空/超限在解析层丢弃，仅文本 D6）
 * @param senderId 发送者 openid（member_openid / user_openid）
 * @param senderName 发送者显示名（群昵称；缺失回退 senderId）
 * @param role 群角色 {@code owner|admin|member}；C2C 恒为 null
 * @param isBot 发送者是否为机器人账号（true 时入站应滤除，防回声环 R4）
 */
public record QqInboundMessage(
        String chatType,
        String chatId,
        String msgId,
        String text,
        String senderId,
        String senderName,
        String role,
        boolean isBot) {

    public static final String CHAT_TYPE_GROUP = "group";
    public static final String CHAT_TYPE_USER = "user";

    /** 归一会话目标（对齐现有 target 语义：platform 前缀 + 会话类型 + openid）。 */
    public String target() {
        return "qq:" + chatType + ":" + chatId;
    }

    /**
     * 管理员判定 fail-closed：仅角色 owner/admin 视为管理员；member/缺失/未知一律按非管理员
     * （沿用 InboundEventParser 语义：平台官方 role 即权威，判断不了即降级为非管理员）。
     */
    public boolean isAdmin() {
        return role != null && ("owner".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role));
    }
}
