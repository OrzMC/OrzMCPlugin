package com.jokerhub.paper.plugin.orzmc.infra.bot;

/**
 * IM 中性会话模型（与具体 driver 解耦）。
 *
 * <p>EasyBotDriver（easybot.yml platforms）与 BuiltinDriver（im_bindings.yml）都归一为
 * 本模型，由 {@link ImMessageRouter} 统一做 PUBLIC/PRIVATE 目标解析——业务语义一致，
 * 切换 backend 无需改会话值（方案：docs/dev/im-gateway-inhouse.md §5）。</p>
 *
 * @param enabled     是否启用该平台会话
 * @param adminGroup  管理群目标（PUBLIC 降级目标 / 入站门槛之一）
 * @param playerGroup 玩家群目标（空则 PUBLIC 降级到 adminGroup）
 * @param adminDm     管理员私聊目标（PRIVATE；空则不投递）
 */
public record ImConversation(boolean enabled, String adminGroup, String playerGroup, String adminDm) {

    /**
     * PUBLIC 消息目标：优先 playerGroup，为空则降级 adminGroup（可能与现状一致返回空串）。
     */
    public String publicTarget() {
        if (playerGroup != null && !playerGroup.isEmpty()) {
            return playerGroup;
        }
        return adminGroup == null ? "" : adminGroup;
    }
}
