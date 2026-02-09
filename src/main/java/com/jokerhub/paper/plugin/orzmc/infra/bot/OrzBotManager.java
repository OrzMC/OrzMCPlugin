package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class OrzBotManager implements BotMessageService {
    private final ServerAccess server;
    private final ServerScheduler scheduler;
    private final ServerLogger logger;
    private final BotInboundHandler inboundHandler;
    private final ConfigService configService;
    private final ThrottledLogger throttledLogger;
    private List<BotAdapter> adapters;
    private final BotRouter router;
    private final AtomicBoolean setupRequested = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public OrzBotManager(
            ServerAccess server,
            ServerScheduler scheduler,
            ServerLogger logger,
            ConfigService configService,
            ThrottledLogger throttledLogger,
            BotInboundHandler inboundHandler) {
        this.server = server;
        this.scheduler = scheduler;
        this.logger = logger;
        this.configService = configService;
        this.throttledLogger = throttledLogger;
        this.inboundHandler = inboundHandler;
        this.adapters = Collections.emptyList();
        this.router = new BotRouter(throttledLogger);
    }

    @Override
    public void setup() {
        setupRequested.set(true);
        scheduler.runAsync(() -> {
            try {
                startIfRequested();
            } catch (Exception e) {
                logger.logger().severe("OrzBotManager 初始化失败: " + e.getMessage());
            }
        });
    }

    void startIfRequested() {
        if (!setupRequested.get()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        adapters = List.of(
                new OrzQQBot(
                        server, logger, configService, inboundHandler, new PlainMessageFormatter(), throttledLogger),
                new OrzDiscordBot(
                        server,
                        logger,
                        scheduler,
                        configService,
                        inboundHandler,
                        new DiscordMessageFormatter(),
                        throttledLogger),
                new OrzLarkBot(server, logger, configService, new PlainMessageFormatter(), throttledLogger));
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
    public void tearDown() {
        router.teardown();
        adapters = Collections.emptyList();
    }
}
