package com.jokerhub.paper.plugin.orzmc.infra.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ChatConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.EntityTeleportConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.ExploitHardeningConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.GamemodeCorrectionConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.LoginRateLimitConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PlayerNotifyConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PrisonConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.RankColorsConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.UpdateConfig;
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
        return MaintenanceConfig.from(section("maintenance"));
    }

    @Override
    public WhitelistConfig whitelist() {
        return WhitelistConfig.from(section("whitelist"));
    }

    @Override
    public WhitelistKickMessage whitelistKickMessage() {
        return WhitelistKickMessage.from(section("whitelist"));
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
        return TntConfig.from(section("tnt"));
    }

    @Override
    public PlayerNotifyConfig playerNotify() {
        return PlayerNotifyConfig.from(section("player_notify"));
    }

    @Override
    public IpWhitelist ipWhitelist() {
        return IpWhitelist.from(section("geoip"));
    }

    @Override
    public SecurityGuardConfig securityGuard() {
        return SecurityGuardConfig.from(section("guard"));
    }

    @Override
    public ChatConfig chat() {
        return ChatConfig.from(section("chat"));
    }

    @Override
    public LoginRateLimitConfig loginRateLimit() {
        return LoginRateLimitConfig.from(section("login_rate_limit"));
    }

    @Override
    public ExploitHardeningConfig exploitHardening() {
        return ExploitHardeningConfig.from(section("exploit_hardening"));
    }

    @Override
    public RankColorsConfig rankColors() {
        return RankColorsConfig.from(section("rank_colors"));
    }

    @Override
    public GamemodeCorrectionConfig gamemodeCorrection() {
        return GamemodeCorrectionConfig.from(section("gamemode-correction"));
    }

    @Override
    public PrisonConfig prison() {
        return PrisonConfig.from(section("prison"));
    }

    @Override
    public UpdateConfig update() {
        return UpdateConfig.from(section("update"));
    }

    @Override
    public EntityTeleportConfig entityTeleport() {
        // entity_teleport 两键在 config.yml 根级（合并重构后未段化），不走 section(name)
        return EntityTeleportConfig.from(configService.getConfig("config"));
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

    /** 从合并 {@code config.yml} 读取指定分段；缺失返回 {@code null}（由各 TypedConfig 默认值兜底）。 */
    private ConfigurationSection section(String name) {
        FileConfiguration cfg = configService.getConfig("config");
        return cfg == null ? null : cfg.getConfigurationSection(name);
    }
}
