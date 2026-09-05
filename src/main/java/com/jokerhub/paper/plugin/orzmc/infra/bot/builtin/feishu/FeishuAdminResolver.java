package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

import java.util.concurrent.CompletableFuture;

/**
 * 飞书发送者管理员判定（builtin 飞书 adapter，方案 §6 角色判定）。
 *
 * <p>飞书消息事件<b>不含</b>发送者角色（区别于 QQ 事件自带 member_role）——需查询
 * {@code GET /im/v1/chats/{chat_id}}（owner_id + user_manager_id_list）判定，实现方负责：
 * 角色缓存（TTL）/ 查询失败降级（非管理员，fail-closed）/ 异步（不得阻塞服务器线程）。</p>
 */
@FunctionalInterface
public interface FeishuAdminResolver {

    /**
     * 异步判定发送者是否群主/管理员。
     *
     * @return 是否管理（群内 owner/manager）；单聊恒 false；查询失败/未知恒 false（fail-closed）
     */
    CompletableFuture<Boolean> isAdmin(FeishuInboundMessage message);
}
