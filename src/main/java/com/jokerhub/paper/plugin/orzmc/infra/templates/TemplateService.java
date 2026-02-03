package com.jokerhub.paper.plugin.orzmc.infra.templates;

import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

public final class TemplateService {
    private TemplateService() {}

    public static MessageEnvelope renderEvent(
            String eventKey,
            FileConfiguration templatesCfg,
            TypedConfigs.Templates templates,
            Map<String, String> vars) {
        String template = templateForEvent(eventKey, templates);
        return TemplateRenderer.renderEnvelope(eventKey, template, vars, templatesCfg);
    }

    private static String templateForEvent(String eventKey, TypedConfigs.Templates templates) {
        if (eventKey == null || eventKey.isEmpty()) {
            return "";
        }
        if ("player_join".equals(eventKey)) return templates.playerJoin();
        if ("player_quit".equals(eventKey)) return templates.playerQuit();
        if ("player_kick".equals(eventKey)) return templates.playerKick();
        if ("exception_alert".equals(eventKey)) return templates.exceptionAlert();
        if ("geoip_block".equals(eventKey)) return templates.geoipBlock();
        if ("tnt_alert".equals(eventKey)) return templates.tntAlert();
        if ("maintenance_backup_stage".equals(eventKey)) return templates.maintenanceBackupStage();
        if ("maintenance_backup_done".equals(eventKey)) return templates.maintenanceBackupDone();
        if ("maintenance_backup_error".equals(eventKey)) return templates.maintenanceBackupError();
        if ("maintenance_optimize_stage".equals(eventKey)) return templates.maintenanceOptimizeStage();
        if ("maintenance_optimize_done".equals(eventKey)) return templates.maintenanceOptimizeDone();
        if ("maintenance_optimize_error".equals(eventKey)) return templates.maintenanceOptimizeError();
        if ("server_maintenance_hint".equals(eventKey))
            return templates.serverMaintenanceHint() + "\n--------------------\n{motd}";
        if ("server_load".equals(eventKey)) return templates.serverLoad();
        if ("server_stop".equals(eventKey)) return templates.serverStop();
        if ("whitelist_block".equals(eventKey)) return templates.whitelistBlock();
        if ("whitelist_toggle_alert".equals(eventKey)) return templates.whitelistToggleAlert();
        return "";
    }
}
