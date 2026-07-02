package com.jokerhub.paper.plugin.orzmc.features.whitelist;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage.WhitelistKickMessageItem;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class WhitelistEventService {
    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final Notifier notifier;

    public WhitelistEventService(TypedConfigProvider configs, OrzTextStyles styles, Notifier notifier) {
        this.configs = configs;
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
        WhitelistKickMessage kickMsg = configs.whitelistKickMessage();
        String qqGroupId = kickMsg.qqGroupId();
        if (qqGroupId == null || qqGroupId.isEmpty()) {
            qqGroupId = configs.bot().qqGroupId();
        }
        if (qqGroupId != null && !qqGroupId.isEmpty()) {
            if (!kickMsgBuilder.build().equals(Component.empty())) {
                kickMsgBuilder.append(Component.newline()).append(Component.newline());
            }
            kickMsgBuilder
                    .append(styles.playerName(player.getName()).decorate(TextDecoration.BOLD))
                    .append(Component.space())
                    .append(styles.warn("不在服务器白名单中，请先加入QQ群:"))
                    .append(Component.space())
                    .append(styles.success(qqGroupId).decorate(TextDecoration.BOLD))
                    .append(Component.space())
                    .append(styles.warn("，联系管理员添加白名单"));
        }
        String discordServerLink = configs.bot().discordServerLink();
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
        TextComponent whitelistKickMessage = buildKickMessage(configs.whitelistKickMessage());
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
        MessageEnvelope env = configs.renderEvent("whitelist_block", Map.of("message", playChatGroupMsg));
        notifier.event("whitelist_block", env);
    }

    public void handleToggle(WhitelistToggleEvent event) {
        if (isEnableForceWhitelist() && !event.isEnabled()) {
            String msg = "‼️服务器白名单异常关闭";
            MessageEnvelope env = configs.renderEvent("whitelist_toggle_alert", Map.of("message", msg));
            notifier.event("whitelist_toggle_alert", env);
        }
    }

    private boolean isEnableForceWhitelist() {
        try {
            return configs.whitelist().forceWhitelist();
        } catch (Exception e) {
            return true;
        }
    }

    private TextComponent buildKickMessage(WhitelistKickMessage kickMessage) {
        String title = kickMessage.title();
        List<WhitelistKickMessageItem> ups = kickMessage.ups();
        TextComponent.Builder builder = Component.text();
        boolean hasContent = false;
        if (!title.isEmpty()) {
            builder.append(Component.text(title).decorate(TextDecoration.BOLD));
            hasContent = true;
        }
        if (!ups.isEmpty()) {
            int limit = Math.min(5, ups.size());
            for (WhitelistKickMessageItem item : ups.subList(0, limit)) {
                String name = item.name();
                String platform = item.platform();
                if (name.isEmpty() && platform.isEmpty()) {
                    continue;
                }
                if (hasContent) {
                    builder.append(Component.newline());
                }
                if (!name.isEmpty()) {
                    TextComponent platformComponent = Component.empty();
                    if (!platform.isEmpty()) {
                        platformComponent = Component.text(platform)
                                .append(Component.text(":").append(Component.space()));
                    }
                    builder.append(platformComponent)
                            .append(Component.text(name)
                                    .decorate(TextDecoration.BOLD)
                                    .color(styles.colorPlayer()));
                }
                hasContent = true;
            }
        }
        return hasContent ? builder.build() : Component.empty();
    }
}
