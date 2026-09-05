package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.telegram;

import java.util.concurrent.CompletableFuture;

/**
 * Telegram 管理员判定（builtin TG adapter，批次 5a；测试可注入替身）。
 *
 * <p>实现方 {@link TelegramRoleResolver} 走 getChatAdministrators API（异步）；单聊恒非管理。</p>
 */
@FunctionalInterface
public interface TelegramAdminResolver {

    /** 指定入站消息的发送者是否管理员（异步；异常按非管理处理由调用方兜底）。 */
    CompletableFuture<Boolean> isAdmin(TelegramInboundMessage message);
}
