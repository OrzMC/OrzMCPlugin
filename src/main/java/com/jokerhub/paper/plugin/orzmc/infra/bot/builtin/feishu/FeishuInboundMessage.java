package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

/**
 * 飞书入站消息归一模型（builtin 飞书 adapter，事件→业务层前的平台无关形态，方案 §6）。
 *
 * <p>由 {@link FeishuInboundParser} 从事件 v2 信封 payload 解析；业务分发（会话门槛 / 线程调度 /
 * 回复 sink / 角色判定）由 {@link FeishuInboundProcessor} 完成。会话值语义与 QQ builtin / EasyBot
 * 通道一致：{@code target()} = {@code feishu:<chatType>:<chatId>}（群 {@code group} / 私聊 {@code user}），
 * 与 im_bindings 会话同构。</p>
 *
 * @param chatType 会话类型：{@code group}（群聊）或 {@code user}（p2p 单聊）
 * @param chatId 平台 chat_id（群/单聊均 {@code oc_*}，出站 receive_id 即此值）
 * @param msgId 事件消息 id（{@code om_*}；飞书无被动回复通道，ACK 语义由网关负责，此 id 仅日志/去重用）
 * @param text 文本内容（content 字段为 JSON 字符串，已解出 {@code {"text":...}} 并 trim；空/超限在解析层丢弃）
 * @param senderId 发送者 open_id（{@code ou_*}；缺失回退 user_id，用于角色查询/展示）
 * @param senderType 发送者类型（{@code user}/{@code app}/{@code bot} 等；非 user 入站应滤除，防回声环 R4）
 */
public record FeishuInboundMessage(
        String chatType, String chatId, String msgId, String text, String senderId, String senderType) {

    public static final String CHAT_TYPE_GROUP = "group";
    public static final String CHAT_TYPE_USER = "user";

    /** 是否是真实用户消息（R4：仅 user 来源进命令层；app/bot/未知一律滤除防回声环）。 */
    public boolean isUser() {
        return senderType != null && "user".equalsIgnoreCase(senderType);
    }

    /** 归一会话目标（对齐现有 target 语义：platform 前缀 + 会话类型 + chat_id）。 */
    public String target() {
        return "feishu:" + chatType + ":" + chatId;
    }
}
