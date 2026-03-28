package com.jokerhub.paper.plugin.orzmc.core.bot;

public record MessageEnvelope(TargetType targetType, String message, String channelKey, Format format) {
    public enum TargetType {
        PUBLIC,
        PRIVATE,
        CHANNEL
    }

    public enum Format {
        DEFAULT,
        PLAIN,
        CODE_BLOCK
    }

    public static MessageEnvelope publicMessage(String message) {
        return new MessageEnvelope(TargetType.PUBLIC, message, null, Format.DEFAULT);
    }

    public static MessageEnvelope privateMessage(String message) {
        return new MessageEnvelope(TargetType.PRIVATE, message, null, Format.DEFAULT);
    }

    public static MessageEnvelope channelMessage(String channelKey, String message) {
        return new MessageEnvelope(TargetType.CHANNEL, message, channelKey, Format.DEFAULT);
    }

    public static MessageEnvelope channelMessage(String channelKey, String message, Format format) {
        return new MessageEnvelope(TargetType.CHANNEL, message, channelKey, format);
    }

    public MessageEnvelope withFormat(Format format) {
        return new MessageEnvelope(targetType, message, channelKey, format);
    }

    public MessageEnvelope withTargetType(TargetType targetType) {
        return new MessageEnvelope(targetType, message, channelKey, format);
    }

    public MessageEnvelope withChannelKey(String channelKey) {
        return new MessageEnvelope(targetType, message, channelKey, format);
    }
}
