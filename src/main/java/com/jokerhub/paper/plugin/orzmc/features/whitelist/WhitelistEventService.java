package com.jokerhub.paper.plugin.orzmc.features.whitelist;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage.WhitelistKickMessageItem;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class WhitelistEventService {
    /** whitelist_block 群通知限频周期（毫秒）：被拦尝试是高频噪音（实测 48 次被拦 → 40+ 条消息打爆 QQ 频控 40034100），
     * 固定周期内最多放行 1 条，窗口内多余丢弃（玩家踢出提示不受影响，仅群通知节流）。 */
    private static final long WHITELIST_BLOCK_THROTTLE_MS = 5000L;

    private final TypedConfigProvider configs;
    private final OrzTextStyles styles;
    private final Notifier notifier;
    private final ThrottledNotifier throttledNotifier;

    public WhitelistEventService(
            TypedConfigProvider configs, OrzTextStyles styles, Notifier notifier, ThrottledNotifier throttledNotifier) {
        this.configs = configs;
        this.styles = styles;
        this.notifier = notifier;
        this.throttledNotifier = throttledNotifier;
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
        // QQ 群号单一事实源：easybot.qq_group_id（whitelist.kick_message.qq_group_id 已废弃删除，2026-09-02）
        String qqGroupId = configs.bot().qqGroupId();
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
        // 节流：恶意脚本反复登录会在窗口内高频触发，固定周期内最多发 1 条，防 QQ 主动消息频控被打爆
        if (throttledNotifier.shouldRun("whitelist_block", WHITELIST_BLOCK_THROTTLE_MS)) {
            MessageEnvelope env = configs.renderEvent("whitelist_block", Map.of("message", playChatGroupMsg));
            notifier.event("whitelist_block", env);
        }
    }

    public void handleToggle(WhitelistToggleEvent event) {
        if (isEnableForceWhitelist() && !event.isEnabled()) {
            // 模板已提供「⚠️ 服务器异常 + 分割线」外壳，这里只传具体异常项
            String msg = "白名单关闭";
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
