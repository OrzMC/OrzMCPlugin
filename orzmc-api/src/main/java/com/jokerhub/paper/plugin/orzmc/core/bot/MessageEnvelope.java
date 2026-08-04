package com.jokerhub.paper.plugin.orzmc.core.bot;

/**
 * 消息信封，封装机器人消息的目标类型、内容及格式。
 *
 * <p>业务层通过此记录决定将消息发送到公开目标或管理员私聊目标，并以何种格式展示。</p>
 *
 * @param targetType  目标类型
 * @param message     消息内容
 * @param format      消息格式
 */
public record MessageEnvelope(TargetType targetType, String message, Format format) {

    /**
     * 消息目标类型。
     */
    public enum TargetType {
        /** 公开回复（发送到配置的 PUBLIC 会话）。 */
        PUBLIC,
        /** 私聊回复。 */
        PRIVATE,
    }

    /**
     * 消息格式。
     */
    public enum Format {
        /** 默认格式（由机器人适配器自行决定）。 */
        DEFAULT,
        /** 纯文本（无格式）。 */
        PLAIN,
        /** 代码块格式。 */
        CODE_BLOCK
    }

    /**
     * 创建一条公开回复消息。
     *
     * @param message 消息内容
     * @return 目标为 {@link TargetType#PUBLIC} 的信封
     */
    public static MessageEnvelope publicMessage(String message) {
        return new MessageEnvelope(TargetType.PUBLIC, message, Format.DEFAULT);
    }

    /**
     * 创建一条私聊消息。
     *
     * @param message 消息内容
     * @return 目标为 {@link TargetType#PRIVATE} 的信封
     */
    public static MessageEnvelope privateMessage(String message) {
        return new MessageEnvelope(TargetType.PRIVATE, message, Format.DEFAULT);
    }

    /**
     * 返回一个仅格式不同的新信封。
     *
     * @param format 新格式
     * @return 新信封，其余字段不变
     */
    public MessageEnvelope withFormat(Format format) {
        return new MessageEnvelope(targetType, message, format);
    }

    /**
     * 返回一个仅目标类型不同的新信封。
     *
     * @param targetType 新目标类型
     * @return 新信封，其余字段不变
     */
    public MessageEnvelope withTargetType(TargetType targetType) {
        return new MessageEnvelope(targetType, message, format);
    }
}
