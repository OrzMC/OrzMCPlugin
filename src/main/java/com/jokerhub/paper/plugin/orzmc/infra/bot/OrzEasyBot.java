package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EasyBotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.ws.DefaultWebSocketClientFactory;
import com.jokerhub.paper.plugin.orzmc.infra.ws.WebSocketClientFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * EasyBot IM Gateway 适配器。
 *
 * <p>单一适配器处理所有平台（QQ / Telegram / Discord / 飞书 / 微信），
 * EasyBot 已屏蔽各平台协议差异，业务层只需感知 {@code platform}、{@code text}、{@code sender.role}、{@code chat_id}。
 *
 * <p>入站：单一 WebSocket 连接接收所有平台的事件。
 * 出站：根据 {@link MessageEnvelope.TargetType} 和 {@link EasyBotConfig} 的路由规则确定目标。
 *
 * <p>路由规则：
 * <ul>
 *   <li>PUBLIC → 遍历所有平台的 {@code player_group}（空则降级 {@code admin_group}）</li>
 *   <li>PRIVATE → 遍历所有平台的 {@code admin_dm}</li>
 * </ul>
 */
public class OrzEasyBot implements BotMessageService {

    private static final String HEALTH_KEY = "easybot";

    private final ConfigService configService;
    private final MessageFormatter formatter;
    private final HealthRegistry healthRegistry;
    private final HttpSender httpSender;
    private final WebSocketLifecycle wsLifecycle;
    private final InboundEventParser inboundParser;

    // ---- 构造器 -----------------------------------------------------------

    public OrzEasyBot(
            ServerLogger logger,
            ConfigService configService,
            BotInboundHandler inboundHandler,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger,
            HealthRegistry healthRegistry) {
        this.configService = configService;
        this.formatter = formatter;
        this.healthRegistry = healthRegistry;
        this.httpSender = new HttpSender(logger, throttledLogger, healthRegistry);
        this.wsLifecycle = new WebSocketLifecycle(
                logger,
                throttledLogger,
                healthRegistry,
                new DefaultWebSocketClientFactory(),
                this::processInboundEvent);
        this.inboundParser = new InboundEventParser(
                logger,
                configService,
                inboundHandler,
                formatter,
                throttledLogger,
                healthRegistry,
                httpSender,
                wsLifecycle);
    }

    /** 测试用构造器，允许注入模拟的 {@link WebSocketClientFactory}。 */
    OrzEasyBot(
            ServerLogger logger,
            ConfigService configService,
            BotInboundHandler inboundHandler,
            MessageFormatter formatter,
            ThrottledLogger throttledLogger,
            HealthRegistry healthRegistry,
            WebSocketClientFactory wsFactory) {
        this.configService = configService;
        this.formatter = formatter;
        this.healthRegistry = healthRegistry;
        this.httpSender = new HttpSender(logger, throttledLogger, healthRegistry);
        this.wsLifecycle = new WebSocketLifecycle(
                logger,
                throttledLogger,
                healthRegistry,
                wsFactory == null ? new DefaultWebSocketClientFactory() : wsFactory,
                this::processInboundEvent);
        this.inboundParser = new InboundEventParser(
                logger,
                configService,
                inboundHandler,
                formatter,
                throttledLogger,
                healthRegistry,
                httpSender,
                wsLifecycle);
    }

    public boolean isEnable() {
        EasyBotConfig cfg = loadConfig();
        return cfg.enabled();
    }

    @Override
    public void setup() {
        reloadConfig();
    }

    @Override
    public void tearDown() {
        wsLifecycle.shutdown();
        healthRegistry.setEnabled(HEALTH_KEY, false);
        healthRegistry.setWsConnected(HEALTH_KEY, false);
        healthRegistry.setHttpChecked(HEALTH_KEY, false);
        healthRegistry.setLastError(HEALTH_KEY, null);
        healthRegistry.setDelivery(HEALTH_KEY, 0, 0, List.of());
    }

    @Override
    public void tryReconnectIfDisconnected() {
        wsLifecycle.tryReconnect(loadConfig());
    }

    @Override
    public void reloadConfig() {
        wsLifecycle.reconcile(loadConfig());
    }

    /**
     * 出站消息路由。
     *
     * <p>根据 {@link MessageEnvelope.TargetType} 确定目标并发送：
     * <ul>
     *   <li>PUBLIC → 各平台 {@code player_group}（空则降级 {@code admin_group}）</li>
     *   <li>PRIVATE → 各平台 {@code admin_dm}（空则跳过）</li>
     * </ul>
     */
    @Override
    public void send(MessageEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        EasyBotConfig cfg = loadConfig();
        if (!cfg.enabled()) {
            return;
        }
        MessageEnvelope.Format fmt = envelope.format() == null ? MessageEnvelope.Format.DEFAULT : envelope.format();
        List<String> parts = formatter.format(envelope.message(), fmt);

        if (envelope.targetType() == null) {
            return;
        }
        switch (envelope.targetType()) {
            case PUBLIC -> sendPublic(cfg, parts);
            case PRIVATE -> sendPrivate(cfg, parts);
        }
    }

    // ---- 出站路由 ----------------------------------------------------------

    private void sendPublic(EasyBotConfig cfg, List<String> parts) {
        List<String> targets = ImMessageRouter.publicTargets(toConversations(cfg));
        if (!targets.isEmpty()) {
            httpSender.sendBatch(cfg, targets, parts);
        }
    }

    private void sendPrivate(EasyBotConfig cfg, List<String> parts) {
        List<String> targets = ImMessageRouter.privateTargets(toConversations(cfg));
        if (!targets.isEmpty()) {
            httpSender.sendBatch(cfg, targets, parts);
        }
    }

    /** EasyBotConfig 平台段 → 中性会话模型（供共享路由层使用，顺序与配置插入序一致）。 */
    private static List<ImConversation> toConversations(EasyBotConfig cfg) {
        List<ImConversation> result = new ArrayList<>();
        for (var entry : cfg.platforms().entrySet()) {
            EasyBotConfig.PlatformEntry p = entry.getValue();
            result.add(new ImConversation(p.enabled(), p.adminGroup(), p.playerGroup(), p.adminDm()));
        }
        return result;
    }

    // ---- WebSocket 生命周期（测试钩子）-------------------------------------

    void setupWebSocketClient() {
        wsLifecycle.reconcile(loadConfig());
    }

    void shutdownWebSocketClient() {
        wsLifecycle.shutdown();
    }

    // ---- 入站消息处理 -------------------------------------------------------

    /** 入站事件入口（WebSocket 消息经此转发到 {@link InboundEventParser}）。 */
    void processInboundEvent(String jsonString) {
        inboundParser.process(jsonString);
    }

    // ---- 辅助方法 ----------------------------------------------------------

    private EasyBotConfig loadConfig() {
        return EasyBotConfig.from(configService.getConfig("easybot"));
    }
}
