package com.jokerhub.paper.plugin.orzmc.features.server;

import com.destroystokyo.paper.exception.ServerException;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.templates.ExceptionFormatter;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateService;
import org.bukkit.configuration.file.FileConfiguration;

public final class ExceptionAlertService {
    private final ConfigService configService;
    private final Notifier notifier;

    public ExceptionAlertService(ConfigService configService, Notifier notifier) {
        this.configService = configService;
        this.notifier = notifier;
    }

    public void notify(ServerException exception) {
        FileConfiguration templatesCfg = configService.getConfig("templates");
        TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
        MessageEnvelope env = TemplateService.renderEvent(
                "exception_alert",
                templatesCfg,
                tpls,
                java.util.Map.of(
                        "message", exception.toString(), "stack_summary", ExceptionFormatter.summarize(exception)));
        notifier.event("exception_alert", env);
    }
}
