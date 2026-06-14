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
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.file.FileConfiguration;

class OrzBotManager implements BotMessageService {
    private final ServerAccess server;
    private final ServerScheduler scheduler;
    private final ServerLogger logger;
    private final BotInboundHandler inboundHandler;
    private final ConfigService configService;
    private final ThrottledLogger throttledLogger;
    private final HealthRegistry healthRegistry;
    private List<BotAdapter> adapters;
    private final BotRouter router;
    private final AtomicBoolean setupRequested = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean qqReconnectInFlight = new AtomicBoolean(false);

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
        FileConfiguration botCfg = configService.getConfig("bot");
        boolean qqEnabled = botCfg.getBoolean("enable_qq_bot");
        String wsServer = botCfg.getString("qq_bot_ws_server");
        if (!qqEnabled || wsServer == null || wsServer.isEmpty()) {
            return;
        }
        if (healthRegistry.getRaw("qq").wsConnected) {
            return;
        }
        if (!qqReconnectInFlight.compareAndSet(false, true)) {
            return;
        }
        scheduler.runAsync(() -> {
            try {
                startIfRequested();
                HealthRegistry.Status qq = healthRegistry.getRaw("qq");
                if (qq.wsConnected) {
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
                qqReconnectInFlight.set(false);
            }
        });
    }

    @Override
    public void tearDown() {
        router.teardown();
        adapters = Collections.emptyList();
    }
}
