package com.jokerhub.paper.plugin.orzmc.infra.ws;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.Map;

public interface WebSocketClientFactory {
    WsClient create(
            ServerLogger server,
            String url,
            ThrottledLogger throttledLogger,
            int maxRetries,
            long baseRetryInterval,
            long maxRetryInterval,
            int jitterPercent,
            long stableResetMs,
            boolean logMessageEnabled,
            long logMessageThrottleMs,
            Map<String, String> httpHeaders,
            String heartbeatPayload,
            WebSocketEventListener listener,
            MessageHandler handler)
            throws Exception;
}
