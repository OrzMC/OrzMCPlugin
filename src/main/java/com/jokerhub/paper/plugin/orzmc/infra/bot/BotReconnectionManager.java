package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * QQ WebSocket 重连管理器。
 *
 * <p>将重连策略和并发控制从 {@link OrzBotManager} 中提取，降低其认知复杂度。</p>
 */
public final class BotReconnectionManager {

    private final ConfigService configService;
    private final HealthRegistry healthRegistry;
    private final AtomicBoolean reconnectInFlight = new AtomicBoolean(false);

    public BotReconnectionManager(ConfigService configService, HealthRegistry healthRegistry) {
        this.configService = configService;
        this.healthRegistry = healthRegistry;
    }

    /**
     * 检查是否需要重连 QQ WebSocket。
     *
     * @param adapters 当前 bot 适配器列表，从中查找 OrzQQBot 实例进行重连
     * @param onReconnect 重连前调用的回调（如 startIfRequested）
     */
    public void tryReconnectIfDisconnected(List<BotAdapter> adapters, Runnable onReconnect) {
        FileConfiguration botCfg = configService.getConfig("bot");
        boolean qqEnabled = botCfg.getBoolean("enable_qq_bot");
        String wsServer = botCfg.getString("qq_bot_ws_server");
        if (!qqEnabled || wsServer == null || wsServer.isEmpty()) {
            return;
        }
        if (healthRegistry.getRaw("qq").wsConnected) {
            return;
        }
        if (!reconnectInFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            onReconnect.run();
            if (healthRegistry.getRaw("qq").wsConnected) {
                return;
            }
            for (BotAdapter adapter : adapters) {
                if (adapter instanceof OrzQQBot qqBot) {
                    if (!healthRegistry.getRaw("qq").wsConnected) {
                        qqBot.setupWebSocketClient();
                    }
                    return;
                }
            }
        } catch (Exception e) {
            healthRegistry.setLastError("qq", e.toString());
        } finally {
            reconnectInFlight.set(false);
        }
    }
}
