package com.jokerhub.paper.plugin.orzmc.features.server;

import com.destroystokyo.paper.exception.ServerException;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.templates.ExceptionFormatter;

public final class ExceptionAlertService {
    private final TypedConfigProvider configs;
    private final Notifier notifier;

    public ExceptionAlertService(TypedConfigProvider configs, Notifier notifier) {
        this.configs = configs;
        this.notifier = notifier;
    }

    public void notify(ServerException exception) {
        MessageEnvelope env = configs.renderEvent(
                "exception_alert",
                java.util.Map.of(
                        "message", exception.toString(), "stack_summary", ExceptionFormatter.summarize(exception)));
        notifier.event("exception_alert", env);
    }
}
