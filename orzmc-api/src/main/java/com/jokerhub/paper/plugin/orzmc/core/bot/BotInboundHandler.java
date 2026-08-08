package com.jokerhub.paper.plugin.orzmc.core.bot;

import java.util.function.Consumer;

/**
 * 机器人入站消息处理接口。
 *
 * <p>EasyBot 网关适配器通过此接口将各平台原始消息传递给统一的业务处理层。</p>
 */
public interface BotInboundHandler {

    /**
     * 处理一条入站消息。
     *
     * @param message  消息原文
     * @param isAdmin  发送者是否为管理员
     * @param callback 回执回调，用于发送回复消息
     */
    default void handleMessage(String message, boolean isAdmin, Consumer<MessageEnvelope> callback) {
        handleMessage(message, isAdmin, null, callback);
    }

    /**
     * 处理一条入站消息（带发送者身份）。
     *
     * @param message    消息原文
     * @param isAdmin    发送者是否为管理员
     * @param senderName 发送者显示名（群昵称/平台名；网关未透传时为 null，接收方自行兜底）
     * @param callback   回执回调，用于发送回复消息
     */
    void handleMessage(String message, boolean isAdmin, String senderName, Consumer<MessageEnvelope> callback);
}
