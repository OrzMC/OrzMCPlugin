package com.jokerhub.paper.plugin.orzmc.features.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.Format;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.TargetType;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.BotConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistConfig;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.WhitelistKickMessage.WhitelistKickMessageItem;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WhitelistEventServiceTest extends ServiceTestBase {

    private TypedConfigProvider configs;
    private OrzTextStyles styles;
    private Notifier notifier;
    private WhitelistEventService service;

    @BeforeEach
    void setUp() {
        configs = mock(TypedConfigProvider.class);
        styles = mock(OrzTextStyles.class);
        notifier = mock(Notifier.class);
        ThrottledNotifier throttledNotifier = new ThrottledNotifier();

        WhitelistConfig whitelistCfg = mock(WhitelistConfig.class);
        BotConfig botConfig = mock(BotConfig.class);
        WhitelistKickMessage kickMsg = mock(WhitelistKickMessage.class);
        WhitelistKickMessageItem item = mock(WhitelistKickMessageItem.class);

        when(configs.whitelist()).thenReturn(whitelistCfg);
        when(whitelistCfg.forceWhitelist()).thenReturn(true);
        when(configs.bot()).thenReturn(botConfig);
        when(botConfig.discordServerLink()).thenReturn("https://discord.gg/test");
        when(botConfig.qqGroupId()).thenReturn("123456");
        when(configs.whitelistKickMessage()).thenReturn(kickMsg);
        when(kickMsg.title()).thenReturn("联系管理员");
        when(kickMsg.ups()).thenReturn(List.of(item));
        when(item.name()).thenReturn("服主");
        when(item.platform()).thenReturn("QQ");
        when(styles.playerName(anyString())).thenReturn(Component.text("player"));
        when(styles.warn(anyString())).thenReturn(Component.text("warn"));
        when(styles.success(anyString())).thenReturn(Component.text("success"));
        when(styles.info(anyString())).thenReturn(Component.text("info"));
        when(styles.colorPlayer()).thenReturn(net.kyori.adventure.text.format.NamedTextColor.GOLD);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.PUBLIC, "msg", Format.DEFAULT));

        service = new WhitelistEventService(configs, styles, notifier, throttledNotifier);
    }

    @Test
    void handleVerify_nullPlayerName_returnsEarly() {
        ProfileWhitelistVerifyEvent event = mock(ProfileWhitelistVerifyEvent.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn(null);

        service.handleVerify(event);

        verify(event, never()).kickMessage(any());
        verify(notifier, never()).event(anyString(), any());
    }

    @Test
    void handleVerify_alreadyWhitelisted_returnsEarly() {
        ProfileWhitelistVerifyEvent event = mock(ProfileWhitelistVerifyEvent.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("Alice");
        when(event.isWhitelisted()).thenReturn(true);

        service.handleVerify(event);

        verify(event, never()).kickMessage(any());
        verify(notifier, never()).event(anyString(), any());
    }

    @Test
    void handleVerify_notWhitelisted_sendsKickAndNotification() {
        ProfileWhitelistVerifyEvent event = mock(ProfileWhitelistVerifyEvent.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("Alice");
        when(event.isWhitelisted()).thenReturn(false);

        service.handleVerify(event);

        verify(event).kickMessage(any(Component.class));
        verify(notifier).event(eq("whitelist_block"), any(MessageEnvelope.class));
    }

    @Test
    void handleVerify_repeatedBlocksWithinWindow_sendsSingleNotification() {
        // 回归：恶意脚本反复登录被拦（实测 48 次 → 40+ 条消息打爆 QQ 频控），
        // 限频窗口内只放行 1 条群通知；踢出提示每次都设置不受影响
        ProfileWhitelistVerifyEvent event = mock(ProfileWhitelistVerifyEvent.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("Alice");
        when(event.isWhitelisted()).thenReturn(false);

        service.handleVerify(event);
        service.handleVerify(event);
        service.handleVerify(event);

        verify(event, times(3)).kickMessage(any(Component.class));
        verify(notifier, times(1)).event(eq("whitelist_block"), any(MessageEnvelope.class));
    }

    @Test
    void handleVerify_noQqGroup_onlyDiscord() {
        BotConfig botConfig = configs.bot();
        when(botConfig.qqGroupId()).thenReturn(null);

        ProfileWhitelistVerifyEvent event = mock(ProfileWhitelistVerifyEvent.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("Bob");
        when(event.isWhitelisted()).thenReturn(false);

        service.handleVerify(event);

        verify(event).kickMessage(any(Component.class));
        verify(notifier).event(eq("whitelist_block"), any(MessageEnvelope.class));
    }

    @Test
    void handleVerify_noDiscordLink_onlyQqGroup() {
        BotConfig botConfig = configs.bot();
        when(botConfig.discordServerLink()).thenReturn(null);

        ProfileWhitelistVerifyEvent event = mock(ProfileWhitelistVerifyEvent.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("Charlie");
        when(event.isWhitelisted()).thenReturn(false);

        service.handleVerify(event);

        verify(event).kickMessage(any(Component.class));
        verify(notifier).event(eq("whitelist_block"), any(MessageEnvelope.class));
    }

    @Test
    void handleToggle_forceWhitelist_disablingEvent_sendsAlert() {
        WhitelistToggleEvent event = mock(WhitelistToggleEvent.class);
        when(event.isEnabled()).thenReturn(false);

        service.handleToggle(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(configs).renderEvent(eq("whitelist_toggle_alert"), vars.capture());
        assertEquals("白名单关闭", vars.getValue().get("message"));
        verify(notifier).event(eq("whitelist_toggle_alert"), any(MessageEnvelope.class));
    }

    @Test
    void handleToggle_forceWhitelist_enablingEvent_sendsNoAlert() {
        WhitelistToggleEvent event = mock(WhitelistToggleEvent.class);
        when(event.isEnabled()).thenReturn(true);

        service.handleToggle(event);

        verify(notifier, never()).event(anyString(), any());
    }
}
