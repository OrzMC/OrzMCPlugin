package com.jokerhub.paper.plugin.orzmc.core.ports.health;

import java.util.List;

/**
 * 只读的健康状态查询接口。
 *
 * <p>Feature 层通过此接口读取各服务的运行状态，而不依赖于具体实现。</p>
 */
public interface HealthStatus {

    /**
     * 单一服务的健康快照。
     *
     * @param enabled         服务是否启用
     * @param httpOk          HTTP 连接是否正常（HTTP/传输层健康，不含投递结果）
     * @param httpChecked     是否至少完成过一次 HTTP 请求
     * @param wsConnected     WebSocket 是否已连接
     * @param apiReady        API 是否就绪
     * @param lastError       最近一次 HTTP/传输层错误（投递失败见 delivery* 字段；无错误时为空字符串）
     * @param deliveryFailed  最近一次批量投递的失败目标数（0 = 无失败）
     * @param deliveryTotal   最近一次批量投递的目标总数（0 = 未知）
     * @param deliveryTargets 最近一次批量投递的失败目标列表（空 = 无失败）
     * @param lastUpdated     最近更新时间戳（毫秒）
     */
    record Entry(
            boolean enabled,
            boolean httpOk,
            boolean httpChecked,
            boolean wsConnected,
            boolean apiReady,
            String lastError,
            int deliveryFailed,
            int deliveryTotal,
            List<String> deliveryTargets,
            long lastUpdated) {

        /** Compatibility constructor for callers that do not track whether HTTP was attempted. */
        public Entry(
                boolean enabled,
                boolean httpOk,
                boolean wsConnected,
                boolean apiReady,
                String lastError,
                long lastUpdated) {
            this(enabled, httpOk, httpOk, wsConnected, apiReady, lastError, 0, 0, List.of(), lastUpdated);
        }
    }

    /**
     * 查询指定服务的健康状态。
     *
     * @param service 服务名称，如 "easybot"
     * @return 该服务的健康快照
     */
    Entry get(String service);
}
