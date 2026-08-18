package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.LoginRateLimitConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginRateLimitEventServiceTest {

    private LoginRateLimitService limiter;
    private TypedConfigProvider configs;
    private Notifier notifier;
    private OrzTextStyles styles;
    private LoginRateLimitEventService service;

    @BeforeEach
    void setUp() throws Exception {
        LoginRateLimitConfig config = new LoginRateLimitConfig(true, 5, 3, true, "登录过于频繁，请稍后再试");
        limiter = mock(LoginRateLimitService.class);
        configs = mock(TypedConfigProvider.class);
        when(configs.loginRateLimit()).thenReturn(config);
        when(configs.renderTemplate(anyString(), anyMap(), anyString()))
                .thenReturn(MessageEnvelope.publicMessage("login_rate_limit_alert"));
        notifier = mock(Notifier.class);
        styles = mock(OrzTextStyles.class);
        when(styles.warn(anyString())).thenReturn(Component.text("登录过于频繁，请稍后再试"));
        service = new LoginRateLimitEventService(limiter, configs, notifier, styles);
    }

    // ---- 频率超限 ----

    @Test
    void rateLimited_disallowAndNotifyAdmin() throws Exception {
        when(limiter.isRateLimited("1.2.3.4")).thenReturn(true);
        AsyncPlayerPreLoginEvent event = preLoginEvent("1.2.3.4", "alice");
        service.onPlayerPreLogin(event);
        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("login_rate_limit_alert"), any(MessageEnvelope.class));
        // 频率超限后不再检查并发
        verify(limiter, never()).isConcurrencyReached(anyString());
    }

    // ---- 并发超限 ----

    @Test
    void concurrencyReached_disallowAndNotifyAdmin() throws Exception {
        when(limiter.isConcurrencyReached("1.2.3.4")).thenReturn(true);
        AsyncPlayerPreLoginEvent event = preLoginEvent("1.2.3.4", "alice");
        service.onPlayerPreLogin(event);
        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier).event(eq("login_rate_limit_alert"), any(MessageEnvelope.class));
    }

    // ---- 正常登录 ----

    @Test
    void normalLogin_allowed_noDisallowNoNotify() throws Exception {
        AsyncPlayerPreLoginEvent event = preLoginEvent("1.2.3.4", "alice");
        service.onPlayerPreLogin(event);
        verify(event, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), any(Component.class));
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
    }

    // ---- 告警开关 ----

    @Test
    void notifyAdminsDisabled_disallowWithoutNotify() throws Exception {
        when(configs.loginRateLimit()).thenReturn(new LoginRateLimitConfig(true, 5, 3, false, "登录过于频繁，请稍后再试"));
        when(limiter.isRateLimited("1.2.3.4")).thenReturn(true);
        AsyncPlayerPreLoginEvent event = preLoginEvent("1.2.3.4", "alice");
        service.onPlayerPreLogin(event);
        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
    }

    // ---- 总开关 ----

    @Test
    void disabledConfig_noop() throws Exception {
        when(configs.loginRateLimit()).thenReturn(new LoginRateLimitConfig(false, 5, 3, true, "登录过于频繁，请稍后再试"));
        AsyncPlayerPreLoginEvent event = preLoginEvent("1.2.3.4", "alice");
        service.onPlayerPreLogin(event);
        verify(event, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), any(Component.class));
        verify(limiter, never()).isRateLimited(anyString());
        verify(limiter, never()).isConcurrencyReached(anyString());
        verify(notifier, never()).event(anyString(), any(MessageEnvelope.class));
    }

    // ---- 异常输入 ----

    @Test
    void nullAddress_noop() {
        AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
        when(event.getAddress()).thenReturn(null);
        service.onPlayerPreLogin(event);
        verify(event, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), any(Component.class));
        verify(limiter, never()).isRateLimited(anyString());
    }

    // ---- 并发登记/注销 ----

    @Test
    void onPlayerJoin_registersConcurrency() throws Exception {
        PlayerJoinEvent event = joinEvent("alice", "1.2.3.4");
        service.onPlayerJoin(event);
        verify(limiter).onPlayerJoin("1.2.3.4", "alice");
    }

    @Test
    void onPlayerJoin_nullSocket_noop() {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        Player player = mock(Player.class);
        when(player.getAddress()).thenReturn(null);
        when(event.getPlayer()).thenReturn(player);
        service.onPlayerJoin(event);
        verify(limiter, never()).onPlayerJoin(anyString(), anyString());
    }

    @Test
    void onPlayerQuit_removesConcurrency() {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("alice");
        when(event.getPlayer()).thenReturn(player);
        service.onPlayerQuit(event);
        verify(limiter).onPlayerQuit("alice");
    }

    private static AsyncPlayerPreLoginEvent preLoginEvent(String ip, String playerName) throws Exception {
        AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
        when(event.getAddress()).thenReturn(InetAddress.getByName(ip));
        com.destroystokyo.paper.profile.PlayerProfile profile =
                mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getName()).thenReturn(playerName);
        when(event.getPlayerProfile()).thenReturn(profile);
        return event;
    }

    private static PlayerJoinEvent joinEvent(String playerName, String ip) throws Exception {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(playerName);
        when(player.getAddress()).thenReturn(new InetSocketAddress(InetAddress.getByName(ip), 25565));
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}
