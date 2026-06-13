package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateService;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
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
        ConfigurationSection section = sectionOrLegacy("config", "maintenance", "maintenance.yml");
        return TypedConfigs.MaintenanceConfig.from(section);
    }

    @Override
    public TypedConfigs.WhitelistConfig whitelist() {
        ConfigurationSection section = sectionOrLegacy("config", "whitelist", "whitelist.yml");
        return TypedConfigs.WhitelistConfig.from(section);
    }

    @Override
    public TypedConfigs.WhitelistKickMessage whitelistKickMessage() {
        ConfigurationSection section = sectionOrLegacy("config", "whitelist", "whitelist.yml");
        return TypedConfigs.WhitelistKickMessage.from(section);
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
        ConfigurationSection section = sectionOrLegacy("config", "tnt", "tnt.yml");
        return TypedConfigs.TntConfig.from(section);
    }

    @Override
    public TypedConfigs.IpWhitelist ipWhitelist() {
        ConfigurationSection section = sectionOrLegacy("config", "geoip", "ip_whitelist.yml");
        return TypedConfigs.IpWhitelist.from(section);
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

    /**
     * Read a ConfigurationSection from the merged config, with fallback to old individual file.
     * Delegates to ConfigManager for the actual lookup logic.
     * Returns null if neither path has data.
     */
    private ConfigurationSection sectionOrLegacy(String mergedConfigName, String section, String legacyFileName) {
        return configService.sectionOrLegacy(mergedConfigName, section, legacyFileName);
    }
}
