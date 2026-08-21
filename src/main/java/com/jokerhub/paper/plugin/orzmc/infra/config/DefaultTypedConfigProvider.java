package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ChatConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ExploitHardeningConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.LoginRateLimitConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PlayerNotifyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.RankColorsConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;
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
    public BotConfig bot() {
        return BotConfig.from(configService.getConfig("easybot"));
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
    public PlayerNotifyConfig playerNotify() {
        ConfigurationSection section = sectionOrLegacy("config", "player_notify", "player_notify.yml");
        return PlayerNotifyConfig.from(section);
    }

    @Override
    public IpWhitelist ipWhitelist() {
        ConfigurationSection section = sectionOrLegacy("config", "geoip", "ip_whitelist.yml");
        return IpWhitelist.from(section);
    }

    @Override
    public SecurityGuardConfig securityGuard() {
        ConfigurationSection section = sectionOrLegacy("config", "guard", "guard.yml");
        return SecurityGuardConfig.from(section);
    }

    @Override
    public ChatConfig chat() {
        ConfigurationSection section = sectionOrLegacy("config", "chat", "chat.yml");
        return ChatConfig.from(section);
    }

    @Override
    public LoginRateLimitConfig loginRateLimit() {
        ConfigurationSection section = sectionOrLegacy("config", "login_rate_limit", "login_rate_limit.yml");
        return LoginRateLimitConfig.from(section);
    }

    @Override
    public ExploitHardeningConfig exploitHardening() {
        ConfigurationSection section = sectionOrLegacy("config", "exploit_hardening", "exploit_hardening.yml");
        return ExploitHardeningConfig.from(section);
    }

    @Override
    public RankColorsConfig rankColors() {
        ConfigurationSection section = sectionOrLegacy("config", "rank_colors", "rank_colors.yml");
        return RankColorsConfig.from(section);
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
