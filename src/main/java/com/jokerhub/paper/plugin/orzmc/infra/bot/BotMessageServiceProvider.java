package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;

public final class BotMessageServiceProvider {
    private BotMessageServiceProvider() {}

    public static BotMessageService create(
            ServerAccess server,
            ServerLogger logger,
            ServerScheduler scheduler,
            ConfigService configService,
            ThrottledLogger throttledLogger,
            BotInboundHandler inboundHandler,
            HealthRegistry healthRegistry) {
        return new OrzBotManager(
                server, scheduler, logger, configService, throttledLogger, inboundHandler, healthRegistry);
    }
}
