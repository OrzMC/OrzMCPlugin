package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.BotInboundHandler;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;

public final class BotMessageServiceProvider {
    private BotMessageServiceProvider() {}

    public static BotMessageService create(
            ServerLogger logger,
            ConfigService configService,
            ThrottledLogger throttledLogger,
            BotInboundHandler inboundHandler,
            HealthRegistry healthRegistry) {
        return new OrzEasyBot(
                logger, configService, inboundHandler, new PlainMessageFormatter(), throttledLogger, healthRegistry);
    }
}
