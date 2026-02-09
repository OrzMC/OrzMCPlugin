package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateService;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

public final class DefaultTypedConfigProvider implements TypedConfigProvider {
    private final ConfigService configService;

    public DefaultTypedConfigProvider(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public TypedConfigs.BotConfig bot() {
        return TypedConfigs.BotConfig.from(configService.getConfig("bot"));
    }

    @Override
    public TypedConfigs.MaintenanceConfig maintenance() {
        return TypedConfigs.MaintenanceConfig.from(configService.getConfig("maintenance"));
    }

    @Override
    public TypedConfigs.WhitelistConfig whitelist() {
        return TypedConfigs.WhitelistConfig.from(configService.getConfig("whitelist"));
    }

    @Override
    public TypedConfigs.WhitelistKickMessage whitelistKickMessage() {
        return TypedConfigs.WhitelistKickMessage.from(configService.getConfig("whitelist"));
    }

    @Override
    public TypedConfigs.TemplateOptions templateOptions() {
        return TypedConfigs.TemplateOptions.from(configService.getConfig("templates"));
    }

    @Override
    public TypedConfigs.Templates templates() {
        return TypedConfigs.Templates.from(configService.getConfig("templates"));
    }

    @Override
    public TypedConfigs.TntConfig tnt() {
        return TypedConfigs.TntConfig.from(configService.getConfig("tnt"));
    }

    @Override
    public TypedConfigs.IpWhitelist ipWhitelist() {
        return TypedConfigs.IpWhitelist.from(configService.getConfig("ip_whitelist"));
    }

    @Override
    public MessageEnvelope renderEvent(String eventKey, Map<String, String> vars) {
        FileConfiguration templatesCfg = configService.getConfig("templates");
        TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
        return TemplateService.renderEvent(eventKey, templatesCfg, tpls, vars);
    }

    @Override
    public MessageEnvelope renderTemplate(String templateKey, Map<String, String> vars, String fallback) {
        FileConfiguration templatesCfg = configService.getConfig("templates");
        String template = TemplateRenderer.resolveTemplate(templateKey, templatesCfg, fallback);
        return TemplateRenderer.renderEnvelope(templateKey, template, vars, templatesCfg);
    }

    @Override
    public String resolveTemplate(String templateKey, String fallback) {
        return TemplateRenderer.resolveTemplate(templateKey, configService.getConfig("templates"), fallback);
    }
}
