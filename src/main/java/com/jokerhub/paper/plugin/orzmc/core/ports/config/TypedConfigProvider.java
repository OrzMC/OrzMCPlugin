package com.jokerhub.paper.plugin.orzmc.core.ports.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import java.util.Map;

public interface TypedConfigProvider {
    TypedConfigs.BotConfig bot();

    TypedConfigs.MaintenanceConfig maintenance();

    TypedConfigs.WhitelistConfig whitelist();

    TypedConfigs.WhitelistKickMessage whitelistKickMessage();

    TypedConfigs.TemplateOptions templateOptions();

    TypedConfigs.Templates templates();

    TypedConfigs.TntConfig tnt();

    TypedConfigs.IpWhitelist ipWhitelist();

    MessageEnvelope renderEvent(String eventKey, Map<String, String> vars);

    MessageEnvelope renderTemplate(String templateKey, Map<String, String> vars, String fallback);

    String resolveTemplate(String templateKey, String fallback);
}
