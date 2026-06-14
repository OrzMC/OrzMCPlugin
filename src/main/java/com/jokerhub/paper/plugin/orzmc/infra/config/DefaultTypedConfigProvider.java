package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateRenderer;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateService;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;

public final class DefaultTypedConfigProvider implements TypedConfigProvider {
    private final ConfigService configService;

    public DefaultTypedConfigProvider(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public BotConfig bot() {
        return BotConfig.from(configService.getConfig("bot"));
    }

    @Override
    public MaintenanceConfig maintenance() {
        ConfigurationSection section = sectionOrLegacy("config", "maintenance", "maintenance.yml");
        return MaintenanceConfig.from(section);
    }

    @Override
    public WhitelistConfig whitelist() {
        ConfigurationSection section = sectionOrLegacy("config", "whitelist", "whitelist.yml");
        return WhitelistConfig.from(section);
    }

    @Override
    public WhitelistKickMessage whitelistKickMessage() {
        ConfigurationSection section = sectionOrLegacy("config", "whitelist", "whitelist.yml");
        return WhitelistKickMessage.from(section);
    }

    @Override
    public TemplateOptions templateOptions() {
        return TemplateOptions.from(configService.getConfig("templates"));
    }

    @Override
    public Templates templates() {
        return Templates.from(configService.getConfig("templates"));
    }

    @Override
    public TntConfig tnt() {
        ConfigurationSection section = sectionOrLegacy("config", "tnt", "tnt.yml");
        return TntConfig.from(section);
    }

    @Override
    public IpWhitelist ipWhitelist() {
        ConfigurationSection section = sectionOrLegacy("config", "geoip", "ip_whitelist.yml");
        return IpWhitelist.from(section);
    }

    @Override
    public MessageEnvelope renderEvent(String eventKey, Map<String, String> vars) {
        FileConfiguration templatesCfg = configService.getConfig("templates");
        Templates tpls = Templates.from(templatesCfg);
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
