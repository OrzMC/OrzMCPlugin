package com.jokerhub.paper.plugin.orzmc.infra.bot;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import org.bukkit.configuration.file.FileConfiguration;

public abstract class OrzBaseBot implements BotAdapter {
    public abstract boolean isEnable();

    public abstract void setup();

    public abstract void teardown();

    protected final OrzMC plugin;
    protected final FileConfiguration botConfig;

    protected OrzBaseBot(OrzMC plugin, ConfigService configService) {
        this.plugin = plugin;
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
