package com.jokerhub.paper.plugin.orzmc.core.ports.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.IpWhitelist;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.MaintenanceConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.Templates;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;
import java.util.Map;

public interface TypedConfigProvider {
    BotConfig bot();

    MaintenanceConfig maintenance();

    WhitelistConfig whitelist();

    WhitelistKickMessage whitelistKickMessage();

    TemplateOptions templateOptions();

    Templates templates();

    TntConfig tnt();

    IpWhitelist ipWhitelist();

    MessageEnvelope renderEvent(String eventKey, Map<String, String> vars);

    MessageEnvelope renderTemplate(String templateKey, Map<String, String> vars, String fallback);

    String resolveTemplate(String templateKey, String fallback);
}
