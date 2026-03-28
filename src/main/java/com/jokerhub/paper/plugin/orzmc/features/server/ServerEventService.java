package com.jokerhub.paper.plugin.orzmc.features.server;

import com.destroystokyo.paper.exception.ServerException;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.ServerLoadEvent;

public final class ServerEventService {
    private final ExceptionAlertService exceptionAlertService;
    private final ServerFeedbackService feedbackService;
    private final WorldMaintenanceService maintenanceService;
    private final TypedConfigProvider configs;
    private final Notifier notifier;

    public ServerEventService(
            ServerFeedbackService feedbackService,
            WorldMaintenanceService maintenanceService,
            TypedConfigProvider configs,
            Notifier notifier) {
        this.configs = configs;
        this.notifier = notifier;
        this.exceptionAlertService = new ExceptionAlertService(configs, notifier);
        this.feedbackService = feedbackService;
        this.maintenanceService = maintenanceService;
    }

    public void handleException(ServerException exception) {
        exceptionAlertService.notify(exception);
    }

    public void handleServerLoad(ServerLoadEvent event) {
        String message = feedbackService.buildServerLoadMessage(event);
        MessageEnvelope env = configs.renderEvent("server_load", java.util.Map.of("message", message));
        notifier.event("server_load", env);
    }

    public void applyMaintenanceMotd(ServerListPingEvent event) {
        if (!maintenanceService.isRunning()) {
            return;
        }
        event.motd(feedbackService.buildMaintenanceMotd());
    }
}
