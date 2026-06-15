package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerLogger;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import org.bukkit.configuration.file.FileConfiguration;

public abstract class OrzBaseBot implements BotAdapter {
    public abstract boolean isEnable();

    public abstract void setup();

    public abstract void teardown();

    protected final ServerAccess server;
    protected final ServerLogger logger;
    protected final FileConfiguration botConfig;
    protected final HealthRegistry healthRegistry;

    protected OrzBaseBot(
            ServerAccess server, ServerLogger logger, ConfigService configService, HealthRegistry healthRegistry) {
        this.server = server;
        this.logger = logger;
        this.healthRegistry = healthRegistry;
        botConfig = configService.getConfig("bot");
    }

    @Override
    public void send(MessageEnvelope envelope) {
        if (envelope == null) return;
        if (envelope.targetType() == MessageEnvelope.TargetType.CHANNEL) {
            if (envelope.channelKey() != null && !envelope.channelKey().isEmpty()) {
                sendChannel(envelope.channelKey(), envelope.message());
            } else {
                sendPublic(envelope.message());
            }
            return;
        }
        if (envelope.targetType() == MessageEnvelope.TargetType.PRIVATE) {
            sendPrivate(envelope.message());
            return;
        }
        sendPublic(envelope.message());
    }

    protected abstract void sendPublic(String message);

    protected abstract void sendPrivate(String message);

    protected abstract void sendChannel(String channelKey, String message);
}
