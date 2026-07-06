package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Bot WebSocket 重连管理器。
 *
 * <p>由 {@code /bot} 命令触发，检查各 Bot 适配器的 WebSocket 连接状态，
 * 断开时调用 {@code setupWebSocketClient()} 重建连接。
 * 支持 QQ Bot（旧 bot.yml）和 EasyBot（easybot.yml）。</p>
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
     * 检查所有 Bot 适配器的连接状态，断开时尝试重连。
     *
     * @param adapters 当前 bot 适配器列表
     * @param onReconnect 重连前调用的回调（如 {@code startIfRequested}）
     */
    public void tryReconnectIfDisconnected(List<BotAdapter> adapters, Runnable onReconnect) {
        if (!reconnectInFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            onReconnect.run();
            tryReconnectQq(adapters);
            tryReconnectEasyBot(adapters);
        } finally {
            reconnectInFlight.set(false);
        }
    }

    private void tryReconnectQq(List<BotAdapter> adapters) {
        FileConfiguration botCfg = configService.getConfig("bot");
        if (!botCfg.getBoolean("enable_qq_bot")) {
            return;
        }
        String wsServer = botCfg.getString("qq_bot_ws_server");
        if (wsServer == null || wsServer.isEmpty()) {
            return;
        }
        if (healthRegistry.getRaw("qq").wsConnected) {
            return;
        }
        healthRegistry.setLastError("qq", "reconnecting...");
        for (BotAdapter adapter : adapters) {
            if (adapter instanceof OrzQQBot qqBot) {
                qqBot.setupWebSocketClient();
                if (!healthRegistry.getRaw("qq").wsConnected) {
                    healthRegistry.setLastError("qq", "QQ WS reconnect failed");
                }
                return;
            }
        }
    }

    private void tryReconnectEasyBot(List<BotAdapter> adapters) {
        if (!healthRegistry.getRaw("easybot").enabled) {
            return;
        }
        if (healthRegistry.getRaw("easybot").wsConnected) {
            return;
        }
        healthRegistry.setLastError("easybot", "reconnecting...");
        for (BotAdapter adapter : adapters) {
            if (adapter instanceof OrzEasyBot easyBot) {
                easyBot.setupWebSocketClient();
                if (!healthRegistry.getRaw("easybot").wsConnected) {
                    healthRegistry.setLastError("easybot", "EasyBot WS reconnect failed");
                }
                return;
            }
        }
    }
}
