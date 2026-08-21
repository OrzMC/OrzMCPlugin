package com.jokerhub.paper.plugin.orzmc.core.ports.config;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
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
import java.util.Map;

public interface TypedConfigProvider {
    BotConfig bot();

    MaintenanceConfig maintenance();

    WhitelistConfig whitelist();

    WhitelistKickMessage whitelistKickMessage();

    TemplateOptions templateOptions();

    Templates templates();

    TntConfig tnt();

    PlayerNotifyConfig playerNotify();

    IpWhitelist ipWhitelist();

    SecurityGuardConfig securityGuard();

    ChatConfig chat();

    LoginRateLimitConfig loginRateLimit();

    /** 已知漏洞加固（P2-3）配置。 */
    ExploitHardeningConfig exploitHardening();

    /** 玩家名颜色（按权限等级）配置。 */
    RankColorsConfig rankColors();

    MessageEnvelope renderEvent(String eventKey, Map<String, String> vars);

    MessageEnvelope renderTemplate(String templateKey, Map<String, String> vars, String fallback);

    String resolveTemplate(String templateKey, String fallback);
}
