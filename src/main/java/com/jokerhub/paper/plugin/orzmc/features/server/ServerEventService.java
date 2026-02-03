package com.jokerhub.paper.plugin.orzmc.features.server;

import com.destroystokyo.paper.exception.ServerException;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;

public final class ServerEventService {
    private final ExceptionAlertService exceptionAlertService;
    private final ServerFeedbackService feedbackService;
    private final ConfigService configService;
    private final Notifier notifier;

    public ServerEventService(ConfigService configService, OrzTextStyles styles, Notifier notifier) {
        this.configService = configService;
        this.notifier = notifier;
        this.exceptionAlertService = new ExceptionAlertService(configService, notifier);
        this.feedbackService = new ServerFeedbackService(configService, styles);
    }

    public void handleException(ServerException exception) {
        exceptionAlertService.notify(exception);
    }

    public void handleServerLoad(ServerLoadEvent event) {
        FileConfiguration templatesCfg = configService.getConfig("templates");
        TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
        String message = feedbackService.buildServerLoadMessage(event);
        MessageEnvelope env =
                TemplateService.renderEvent("server_load", templatesCfg, tpls, java.util.Map.of("message", message));
        notifier.event("server_load", env);
    }

    public void applyMaintenanceMotd(ServerListPingEvent event) {
        if (!WorldMaintenanceService.isRunningGlobal()) {
            return;
        }
        event.motd(feedbackService.buildMaintenanceMotd());
    }
}
