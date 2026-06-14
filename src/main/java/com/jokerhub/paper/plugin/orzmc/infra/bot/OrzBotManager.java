package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class OrzBotManager implements BotMessageService {

    private enum State {
        IDLE,
        SETUP_REQUESTED,
        STARTED
    }

    private final ServerAccess server;
    private final ServerScheduler scheduler;
    private final ServerLogger logger;
    private final BotInboundHandler inboundHandler;
    private final ConfigService configService;
    private final ThrottledLogger throttledLogger;
    private final HealthRegistry healthRegistry;
    private final BotReconnectionManager reconnectionManager;
    private List<BotAdapter> adapters;
    private final BotRouter router;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    public OrzBotManager(
            ServerAccess server,
            ServerScheduler scheduler,
            ServerLogger logger,
            ConfigService configService,
            ThrottledLogger throttledLogger,
            BotInboundHandler inboundHandler,
            HealthRegistry healthRegistry) {
        this.server = server;
        this.scheduler = scheduler;
        this.logger = logger;
        this.configService = configService;
        this.throttledLogger = throttledLogger;
        this.inboundHandler = inboundHandler;
        this.healthRegistry = healthRegistry;
        this.reconnectionManager = new BotReconnectionManager(configService, healthRegistry);
        this.adapters = Collections.emptyList();
        this.router = new BotRouter(throttledLogger);
    }

    @Override
    public void setup() {
        state.set(State.SETUP_REQUESTED);
        scheduler.runAsync(() -> {
            try {
                startIfRequested();
            } catch (Exception e) {
                logger.logger().severe("OrzBotManager 初始化失败: " + e.getMessage());
            }
        });
    }

    void startIfRequested() {
        if (!state.compareAndSet(State.SETUP_REQUESTED, State.STARTED)) {
            return;
        }
        adapters = List.of(
                new OrzQQBot(
                        server,
                        logger,
                        configService,
                        inboundHandler,
                        new PlainMessageFormatter(),
                        throttledLogger,
                        healthRegistry),
                new OrzDiscordBot(
                        server,
                        logger,
                        scheduler,
                        configService,
                        inboundHandler,
                        new DiscordMessageFormatter(),
                        throttledLogger,
                        healthRegistry),
                new OrzLarkBot(
                        server, logger, configService, new PlainMessageFormatter(), throttledLogger, healthRegistry));
        router.setAdapters(adapters);
        router.setup();
    }

    @Override
    public void send(MessageEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        throttledLogger.info("bot", envelope.message());
        router.route(envelope);
    }

    @Override
    public void tryReconnectQqWsIfDisconnected() {
        scheduler.runAsync(() -> reconnectionManager.tryReconnectIfDisconnected(adapters, this::startIfRequested));
    }

    @Override
    public void tearDown() {
        router.teardown();
        adapters = Collections.emptyList();
    }
}
