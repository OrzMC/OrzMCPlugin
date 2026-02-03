package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;

public final class BotMessageServiceProvider {
    private BotMessageServiceProvider() {}

    public static BotMessageService create(
            OrzMC plugin,
            ConfigService configService,
            ThrottledLogger throttledLogger,
            BotInboundHandler inboundHandler) {
        return new OrzBotManager(plugin, configService, throttledLogger, inboundHandler);
    }
}
