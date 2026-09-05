package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.feishu;

/**
 * 飞书长连接入站事件回调（F3b 网关 → F4 归一/调度）。
 *
 * <p>payload 为事件 v2 信封 JSON 字节（{@code {schema, header{event_id,event_type,...}, event{...}}}）；
 * 回调发生在 WS 读线程——实现方不得触碰 Bukkit API（R12：入站经 ServerFacade.runSync 调度到服务器线程
 * 后再进命令层）。抛出 {@link RuntimeException} 会使网关回 ACK 500（平台重推，实现方以 event_id 去重防重复）。</p>
 */
@FunctionalInterface
public interface FeishuEventSink {

    /** 收到一个事件（已聚合分片；payload 非空）。 */
    void onEvent(byte[] payload);
}
