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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;

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
            kickMsgBuilder.append(styles.playerName(player.getName()).decorate(TextDecoration.BOLD)).append(Component.space()).append(styles.warn("不在服务器白名单中，请先加入QQ群:")).append(Component.space()).append(styles.success(qqPlayerGroupId).decorate(TextDecoration.BOLD)).append(Component.space()).append(styles.warn("，联系管理员添加白名单"));
        }
        String discordServerLink = botConfig.getString("discord_server_link");
        if (discordServerLink != null && !discordServerLink.isEmpty()) {
            if (!kickMsgBuilder.build().equals(Component.empty())) {
                kickMsgBuilder.append(Component.newline()).append(Component.newline());
            }
            kickMsgBuilder.append(styles.info("you can also join the discord server: ")).append(Component.text(discordServerLink).color(NamedTextColor.BLUE).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(discordServerLink)));
        }
        TextComponent whitelistKickMessage = buildKickMessage(configService.getConfig("whitelist"));
        if (!whitelistKickMessage.equals(Component.empty())) {
            if (!kickMsgBuilder.build().equals(Component.empty())) {
                kickMsgBuilder.append(Component.newline()).append(Component.newline());
            }
            kickMsgBuilder.append(whitelistKickMessage);
        }
        if (!kickMsgBuilder.build().equals(Component.empty())) {
            event.kickMessage(kickMsgBuilder.build());
        }

        String playChatGroupMsg = player.getName() + " 尝试加入服务器，被白名单拦截";
        FileConfiguration templatesCfg = configService.getConfig("templates");
        TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
        MessageEnvelope env = TemplateService.renderEvent("whitelist_block", templatesCfg, tpls, Map.of("message", playChatGroupMsg));
        notifier.event("whitelist_block", env);
    }

    public void handleToggle(WhitelistToggleEvent event) {
        if (isEnableForceWhitelist() && !event.isEnabled()) {
            String msg = "‼️服务器白名单异常关闭";
            FileConfiguration templatesCfg = configService.getConfig("templates");
            TypedConfigs.Templates tpls = TypedConfigs.Templates.from(templatesCfg);
            MessageEnvelope env = TemplateService.renderEvent("whitelist_toggle_alert", templatesCfg, tpls, Map.of("message", msg));
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

    private TextComponent buildKickMessage(FileConfiguration whitelistConfig) {
        if (whitelistConfig == null) {
            return Component.empty();
        }
        ConfigurationSection section = whitelistConfig.getConfigurationSection("kick_message");
        if (section == null) {
            return Component.empty();
        }
        String title = section.getString("title", "");
        List<Map<?, ?>> ups = section.getMapList("ups");
        TextComponent.Builder builder = Component.text();
        boolean hasContent = false;
        if (!title.isEmpty()) {
            builder.append(Component.text(title).decorate(TextDecoration.BOLD));
            hasContent = true;
        }
        if (!ups.isEmpty()) {
            for (Map<?, ?> raw : ups.subList(0, 5)) {
                if (raw == null) {
                    continue;
                }
                String name = raw.get("name") == null ? "" : String.valueOf(raw.get("name"));
                String platform = raw.get("platform") == null ? "" : String.valueOf(raw.get("platform"));
                if (name.isEmpty() && platform.isEmpty()) {
                    continue;
                }
                if (hasContent) {
                    builder.append(Component.newline());
                }
                if (!name.isEmpty()) {
                    TextComponent platformComponent = Component.empty();
                    if (!platform.isEmpty()) {
                        platformComponent = Component.text(platform).append(Component.text(":").append(Component.space()));
                    }
                    builder.append(Component.newline())
                            .append(platformComponent)
                            .append(Component.text(name).decorate(TextDecoration.BOLD).color(styles.colorPlayer()));
                }
                hasContent = true;
            }
        }
        return hasContent ? builder.build() : Component.empty();
    }
}
