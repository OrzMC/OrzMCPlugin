package com.jokerhub.paper.plugin.orzmc.infra.ws;

import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.Map;

public class DefaultWebSocketClientFactory implements WebSocketClientFactory {
    @Override
    public WsClient create(
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
            throws Exception {
        return new RobustWebSocketClient(
                server,
                url,
                throttledLogger,
                maxRetries,
                baseRetryInterval,
                maxRetryInterval,
                jitterPercent,
                stableResetMs,
                logMessageEnabled,
                logMessageThrottleMs,
                httpHeaders,
                heartbeatPayload,
                listener) {
            @Override
            protected void handleMessage(String message) {
                if (handler != null) handler.handle(message);
            }
        };
    }
}
