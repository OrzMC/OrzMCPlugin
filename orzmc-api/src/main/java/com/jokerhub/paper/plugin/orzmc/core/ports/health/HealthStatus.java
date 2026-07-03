package com.jokerhub.paper.plugin.orzmc.core.ports.health;

/**
 * 只读的健康状态查询接口。
 *
 * <p>Feature 层通过此接口读取各服务的运行状态，而不依赖于具体实现。</p>
 */
public interface HealthStatus {

    /**
     * 单一服务的健康快照。
     *
     * @param enabled     服务是否启用
     * @param httpOk      HTTP 连接是否正常
     * @param wsConnected WebSocket 是否已连接
     * @param apiReady    API 是否就绪
     * @param lastError   最近一次错误信息（无错误时为空字符串）
     * @param lastUpdated 最近更新时间戳（毫秒）
     */
    record Entry(
            boolean enabled,
            boolean httpOk,
            boolean wsConnected,
            boolean apiReady,
            String lastError,
            long lastUpdated) {}

    /**
     * 查询指定服务的健康状态。
     *
     * @param service 服务名称，如 "qq"、"discord"、"lark"
     * @return 该服务的健康快照
     */
    Entry get(String service);
}
