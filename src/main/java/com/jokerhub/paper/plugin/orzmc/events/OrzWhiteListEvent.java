package com.jokerhub.paper.plugin.orzmc.events;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;

public class OrzWhiteListEvent extends OrzBaseListener {
    public OrzWhiteListEvent(OrzMC plugin) {
        super(plugin);
    }

    private boolean isEnableForceWhitelist() {
        try {
            return plugin.configManager.getConfig("whitelist").getBoolean("force_whitelist");
        } catch (Exception e) {
            return true;
        }
    }

    @EventHandler
    public void onWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        PlayerProfile player = event.getPlayerProfile();
        if (player.getName() == null) {
            return;
        }
        if (event.isWhitelisted()) {
            return;
        }
        TextComponent.Builder kickMsgBuilder = Component.text();
        FileConfiguration botConfig = plugin.configManager.getConfig("bot");
        String qqPlayerGroupId = botConfig.getString("qq_player_group_id", botConfig.getString("qq_group_id"));
        if (qqPlayerGroupId != null && !qqPlayerGroupId.isEmpty()) {
            if (!kickMsgBuilder.build().equals(Component.empty())) {
                kickMsgBuilder.append(Component.newline()).append(Component.newline());
            }
            kickMsgBuilder
                    .append(OrzTextStyles.playerName(player.getName()).decorate(TextDecoration.BOLD))
                    .append(Component.space())
                    .append(OrzTextStyles.warn("不在服务器白名单中，请先加入QQ群:"))
                    .append(Component.space())
                    .append(OrzTextStyles.warn(qqPlayerGroupId))
                    .append(Component.space())
                    .append(OrzTextStyles.info("，联系管理员添加白名单"));
        }
        String discordServerLink = botConfig.getString("discord_server_link");
        if (discordServerLink != null && !discordServerLink.isEmpty()) {
            if (!kickMsgBuilder.build().equals(Component.empty())) {
                kickMsgBuilder.append(Component.newline()).append(Component.newline());
            }
            kickMsgBuilder
                    .append(OrzTextStyles.info("you can also join the discord server: "))
                    .append(Component.text(discordServerLink)
                            .color(NamedTextColor.BLUE)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(discordServerLink)));
        }
        if (!kickMsgBuilder.build().equals(Component.empty())) {
            event.kickMessage(kickMsgBuilder.build());
        }

        // 通知玩家群
        String playChatGroupMsg = player.getName() + " 尝试加入服务器，被白名单拦截";
        Notifier.event("whitelist_block", playChatGroupMsg);
    }

    @EventHandler
    public void onWhitelistToggled(WhitelistToggleEvent event) {
        if (isEnableForceWhitelist() && !event.isEnabled()) {
            Notifier.event("whitelist_toggle_alert", "‼️服务器白名单异常关闭");
        }
    }
}
