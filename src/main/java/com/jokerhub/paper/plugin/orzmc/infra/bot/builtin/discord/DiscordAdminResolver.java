package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.discord;

import java.util.concurrent.CompletableFuture;

/**
 * Discord 管理员判定（builtin DC adapter，批次 5b；测试可注入替身）。
 *
 * <p>实现方 {@link DiscordRoleResolver} 走 Discord REST API（guild owner + 成员角色权限，异步）；DM 恒非管理。</p>
 */
@FunctionalInterface
public interface DiscordAdminResolver {

    /** 指定入站消息的发送者是否管理员（异步；异常按非管理处理由调用方兜底）。 */
    CompletableFuture<Boolean> isAdmin(DiscordInboundMessage message);
}
