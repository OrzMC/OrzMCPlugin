package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;

/**
 * builtin 通道未实现时的占位实现（方案 D3：失败停群 + 告警，不做自动回退）。
 *
 * <p>im.yml 选择 {@code backend: builtin} 但内置直连尚未落地（或初始化失败）时，
 * 由 {@link BotMessageServiceProvider} 返回本实现：setup 记录错误并置健康为停用，
 * 发送静默丢弃——群功能整体停用，等待管理员处理配置/日志。</p>
 */
public final class UnavailableBotMessageService implements BotMessageService {

    private static final String HEALTH_KEY = "im";
    private static final String MESSAGE =
            "IM backend=builtin 无可用平台（需在 im.yml platforms 下启用平台并配齐凭据，如 QQ 的 app_id/client_secret）。"
                    + "已停用群功能——请将 im.yml 的 backend 改回 easybot（默认兜底）或修复配置后重启。";

    private final ServerLogger logger;
    private final HealthRegistry healthRegistry;

    public UnavailableBotMessageService(ServerLogger logger, HealthRegistry healthRegistry) {
        this.logger = logger;
        this.healthRegistry = healthRegistry;
    }

    @Override
    public void setup() {
        logger.logger().severe(MESSAGE);
        healthRegistry.setEnabled(HEALTH_KEY, false);
    }

    @Override
    public void send(MessageEnvelope envelope) {
        // 通道不可用：静默丢弃（已有 setup 告警；不刷屏）
    }

    @Override
    public void tearDown() {
        healthRegistry.setEnabled(HEALTH_KEY, false);
    }
}
