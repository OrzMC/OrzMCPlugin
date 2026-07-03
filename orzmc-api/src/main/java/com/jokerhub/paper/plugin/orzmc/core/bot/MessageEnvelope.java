package com.jokerhub.paper.plugin.orzmc.core.bot;

/**
 * 消息信封，封装机器人消息的目标类型、内容及格式。
 *
 * <p>业务层通过此记录决定将消息发送到哪个目标（公开 / 私聊 / 频道），并以何种格式展示。</p>
 *
 * @param targetType  目标类型
 * @param message     消息内容
 * @param channelKey  频道标识（仅 targetType 为 {@code CHANNEL} 时有效）
 * @param format      消息格式
 */
public record MessageEnvelope(TargetType targetType, String message, String channelKey, Format format) {

    /**
     * 消息目标类型。
     */
    public enum TargetType {
        /** 公开回复（在发送消息的同一频道/群聊中回复）。 */
        PUBLIC,
        /** 私聊回复。 */
        PRIVATE,
        /** 指定频道发送。 */
        CHANNEL
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
        return new MessageEnvelope(TargetType.PUBLIC, message, null, Format.DEFAULT);
    }

    /**
     * 创建一条私聊消息。
     *
     * @param message 消息内容
     * @return 目标为 {@link TargetType#PRIVATE} 的信封
     */
    public static MessageEnvelope privateMessage(String message) {
        return new MessageEnvelope(TargetType.PRIVATE, message, null, Format.DEFAULT);
    }

    /**
     * 创建一条频道消息（默认格式）。
     *
     * @param channelKey 频道标识
     * @param message    消息内容
     * @return 目标为 {@link TargetType#CHANNEL} 的信封
     */
    public static MessageEnvelope channelMessage(String channelKey, String message) {
        return new MessageEnvelope(TargetType.CHANNEL, message, channelKey, Format.DEFAULT);
    }

    /**
     * 创建一条指定格式的频道消息。
     *
     * @param channelKey 频道标识
     * @param message    消息内容
     * @param format     消息格式
     * @return 目标为 {@link TargetType#CHANNEL} 的信封
     */
    public static MessageEnvelope channelMessage(String channelKey, String message, Format format) {
        return new MessageEnvelope(TargetType.CHANNEL, message, channelKey, format);
    }

    /**
     * 返回一个仅格式不同的新信封。
     *
     * @param format 新格式
     * @return 新信封，其余字段不变
     */
    public MessageEnvelope withFormat(Format format) {
        return new MessageEnvelope(targetType, message, channelKey, format);
    }

    /**
     * 返回一个仅目标类型不同的新信封。
     *
     * @param targetType 新目标类型
     * @return 新信封，其余字段不变
     */
    public MessageEnvelope withTargetType(TargetType targetType) {
        return new MessageEnvelope(targetType, message, channelKey, format);
    }

    /**
     * 返回一个仅频道标识不同的新信封。
     *
     * @param channelKey 新频道标识
     * @return 新信封，其余字段不变
     */
    public MessageEnvelope withChannelKey(String channelKey) {
        return new MessageEnvelope(targetType, message, channelKey, format);
    }
}
