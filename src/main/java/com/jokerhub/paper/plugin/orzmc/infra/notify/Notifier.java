package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.NotifyPolicy;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.NotifyPolicy.Notifications;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class Notifier {
    private final ServerAccess server;
    private final ConfigService configService;
    private final BotMessageService botMessageService;
    private NotifierSink sink;

    public Notifier(ServerAccess server, ConfigService configService, BotMessageService botMessageService) {
        this.server = server;
        this.configService = configService;
        this.botMessageService = botMessageService;
        this.sink = new DefaultSink();
    }

    public void server(Component message) {
        sink.server(message);
    }

    public void event(String key, MessageEnvelope envelope) {
        sink.event(key, envelope);
    }

    public void registerSink(NotifierSink s) {
        sink = s == null ? sink : s;
    }

    private final class DefaultSink implements NotifierSink {
        @Override
        public void server(Component message) {
            server.server().sendMessage(message);
        }

        @Override
        public void event(String key, MessageEnvelope envelope) {
            routeEvent(key, envelope);
        }
    }

    public void routeEvent(String key, MessageEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        ConfigurationSection notificationsSection =
                configService.getConfig("templates").getConfigurationSection("notifications");
        if (notificationsSection == null) {
            FileConfiguration legacy = configService.loadFile("notifications.yml");
            notificationsSection = legacy != null ? legacy.getConfigurationSection("notifications") : null;
        }
        Notifications ns = Notifications.from(notificationsSection);
        NotifyPolicy p = ns.policies().getOrDefault(key, new NotifyPolicy(false, true, true, ""));
        if (p.publicEnabled()) {
            botMessageService.send(envelope.withTargetType(MessageEnvelope.TargetType.PUBLIC));
        }
        if (p.privateEnabled()) {
            botMessageService.send(envelope.withTargetType(MessageEnvelope.TargetType.PRIVATE));
        }
        String channelKey = p.channelKey();
        if (channelKey != null && !channelKey.isEmpty()) {
            botMessageService.send(
                    envelope.withTargetType(MessageEnvelope.TargetType.CHANNEL).withChannelKey(channelKey));
        }
    }
}
