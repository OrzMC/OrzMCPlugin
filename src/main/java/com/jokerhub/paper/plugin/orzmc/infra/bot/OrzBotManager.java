package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class OrzBotManager implements BotMessageService {
    private final OrzMC plugin;
    private final BotInboundHandler inboundHandler;
    private final ConfigService configService;
    private final ThrottledLogger throttledLogger;
    private List<BotAdapter> adapters;
    private final BotRouter router;
    private final AtomicBoolean setupRequested = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public OrzBotManager(
            OrzMC plugin,
            ConfigService configService,
            ThrottledLogger throttledLogger,
            BotInboundHandler inboundHandler) {
        this.plugin = plugin;
        this.configService = configService;
        this.throttledLogger = throttledLogger;
        this.inboundHandler = inboundHandler;
        this.adapters = Collections.emptyList();
        this.router = new BotRouter(throttledLogger);
    }

    @Override
    public void setup() {
        setupRequested.set(true);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                startIfRequested();
            } catch (Exception e) {
                plugin.getLogger().severe("OrzBotManager 初始化失败: " + e.getMessage());
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
                new OrzQQBot(plugin, configService, inboundHandler, new PlainMessageFormatter(), throttledLogger),
                new OrzDiscordBot(
                        plugin, configService, inboundHandler, new DiscordMessageFormatter(), throttledLogger),
                new OrzLarkBot(plugin, configService, new PlainMessageFormatter(), throttledLogger));
        router.setAdapters(adapters);
        router.setup();
    }

    @Override
    public void send(MessageEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        OrzMC.debugInfo(envelope.message());
        router.route(envelope);
    }

    @Override
    public void tearDown() {
        router.teardown();
        adapters = Collections.emptyList();
    }
}
