package com.jokerhub.paper.plugin.orzmc.infra.notify;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerAccess;
import com.jokerhub.paper.plugin.orzmc.infra.bot.BotMessageService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TemplateKeys;
import net.kyori.adventure.text.Component;

public final class Notifier {
    private final ServerAccess server;
    private final BotMessageService botMessageService;
    private NotifierSink sink;

    public Notifier(ServerAccess server, BotMessageService botMessageService) {
        this.server = server;
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
        if (key == null || envelope == null) {
            return;
        }
        MessageEnvelope.TargetType target =
                switch (key) {
                    case TemplateKeys.EXCEPTION_ALERT,
                            TemplateKeys.MAINTENANCE_BACKUP_ERROR,
                            TemplateKeys.MAINTENANCE_OPTIMIZE_ERROR,
                            TemplateKeys.COMMAND_GUARD_BLOCKED,
                            TemplateKeys.SECURITY_AUDIT,
                            TemplateKeys.LOGIN_RATE_LIMIT_ALERT,
                            TemplateKeys.EXPLOIT_BLOCKED,
                            TemplateKeys.IP_BLACKLIST_BLOCK -> MessageEnvelope.TargetType.PRIVATE;
                    default -> MessageEnvelope.TargetType.PUBLIC;
                };
        botMessageService.send(envelope.withTargetType(target));
    }
}
