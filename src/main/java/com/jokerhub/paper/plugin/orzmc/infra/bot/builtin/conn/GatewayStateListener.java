package com.jokerhub.paper.plugin.orzmc.infra.bot.builtin.conn;

/**
 * 网关连接状态回调（builtin IM 骨架，供健康聚合 / 告警观察，S7 将桥接 HealthRegistry 多平台 key）。
 *
 * <p>回调发生在线程上下文：onConnected 在 WS 读线程、onDisconnected/onFatal 在 WS 读线程或调度线程，
 * 实现方不得在回调内触碰 Bukkit API（对齐红线 R12：入站事件须另行经 ServerFacade.runSync 调度）。</p>
 */
public interface GatewayStateListener {

    /** 连接建立（每次成功建连都会回调，含自动重连成功）。 */
    default void onConnected() {}

    /** 连接断开（网络断/服务端关闭；网关将按策略自动重连）。 */
    default void onDisconnected(int code, String reason) {}

    /** 不可恢复终止：连续重连失败达上限 / 鉴权失败且令牌不可刷新；网关停止自动重连直至手动 restart。 */
    default void onFatal(String message, Throwable cause) {}
}
