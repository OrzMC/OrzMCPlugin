package com.jokerhub.paper.plugin.orzmc.features.whitelist;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.infra.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.TypedConfigs;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateService;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;

public final class WhitelistEventService {
    private final ConfigService configService;
    private final OrzTextStyles styles;
    private final Notifier notifier;

    public WhitelistEventService(ConfigService configService, OrzTextStyles styles, Notifier notifier) {
        this.configService = configService;
        this.styles = styles;
        this.notifier = notifier;
    }

    public void handleVerify(ProfileWhitelistVerifyEvent event) {
        PlayerProfile player = event.getPlayerProfile();
        if (player.getName() == null) {
            return;
        }
        if (event.isWhitelisted()) {
            return;
        }
        TextComponent.Builder kickMsgBuilder = Component.text();
        FileConfiguration botConfig = configService.getConfig("bot");
        String qqPlayerGroupId = botConfig.getString("qq_player_group_id", botConfig.getString("qq_group_id"));
        if (qqPlayerGroupId != null && !qqPlayerGroupId.isEmpty()) {
            if (!kickMsgBuilder.build().equals(Component.empty())) {
                kickMsgBuilder.append(Component.newline()).append(Component.newline());
            }
            kickMsgBuilder
                    .append(styles.playerName(player.getName()).decorate(TextDecoration.BOLD))
                    .append(Component.space())
                    .append(styles.warn("不在服务器白名单中，请先加入QQ群:"))
                    .append(Component.space())
                    .append(styles.warn(qqPlayerGroupId))
                    .append(Component.space())
                    .append(styles.info("，联系管理员添加白名单"));
        }
        String discordServerLink = botConfig.getString("discord_server_link");
        if (discordServerLink != null && !discordServerLink.isEmpty()) {
            if (!kickMsgBuilder.build().equals(Component.empty())) {
                kickMsgBuilder.append(Component.newline()).append(Component.newline());
            }
            kickMsgBuilder
                    .append(styles.info("you can also join the discord server: "))
                    .append(Component.text(discordServerLink)
                            .color(NamedTextColor.BLUE)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(discordServerLink)));
        }
        if (!kickMsgBuilder.build().equals(Component.empty())) {
            event.kickMessage(kickMsgBuilder.build());
        }

        String playChatGroupMsg = player.getName() + " 尝试加入服务器，被白名单拦截";
        FileConfiguration templatesCfg = configService.getConfig("templates");
        TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
        MessageEnvelope env =
                TemplateService.renderEvent("whitelist_block", templatesCfg, tpls, Map.of("message", playChatGroupMsg));
        notifier.event("whitelist_block", env);
    }

    public void handleToggle(WhitelistToggleEvent event) {
        if (isEnableForceWhitelist() && !event.isEnabled()) {
            String msg = "‼️服务器白名单异常关闭";
            FileConfiguration templatesCfg = configService.getConfig("templates");
            TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
            MessageEnvelope env =
                    TemplateService.renderEvent("whitelist_toggle_alert", templatesCfg, tpls, Map.of("message", msg));
            notifier.event("whitelist_toggle_alert", env);
        }
    }

    private boolean isEnableForceWhitelist() {
        try {
            return configService.getConfig("whitelist").getBoolean("force_whitelist");
        } catch (Exception e) {
            return true;
        }
    }
}
