package com.jokerhub.paper.plugin.orzmc.events;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import com.jokerhub.paper.plugin.orzmc.features.security.BlacklistService;
import com.jokerhub.paper.plugin.orzmc.features.security.GeoIpAccessService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class OrzPlayerEventTest extends ServiceTestBase {

    @Mock
    private OrzMC plugin;

    @Mock
    private GeoIpAccessService geoIpAccessService;

    @Mock
    private BlacklistService blacklistService;

    @Mock
    private PlayerEventService service;

    @Mock
    private GuideService guideService;

    @Mock
    private OrzTextStyles styles;

    @Mock
    private WorldMaintenanceService maintenanceService;

    @Mock
    private AsyncPlayerPreLoginEvent event;

    @Mock
    private PlayerProfile profile;

    @Mock
    private Player player;

    @Mock
    private PlayerJoinEvent joinEvent;

    @Mock
    private PlayerQuitEvent quitEvent;

    @Mock
    private PlayerKickEvent kickEvent;

    private OrzPlayerEvent listener;

    @BeforeEach
    void setUp() throws Exception {
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        when(event.getAddress()).thenReturn(InetAddress.getByName("1.2.3.4"));
        when(event.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("player1");
        when(maintenanceService.isRunning()).thenReturn(false);
        when(blacklistService.isBlocked(anyString())).thenReturn(false);
        when(styles.warn(anyString())).thenReturn(Component.text("warn"));
        when(styles.error(anyString())).thenReturn(Component.text("error"));
        when(joinEvent.getPlayer()).thenReturn(player);
        when(quitEvent.getPlayer()).thenReturn(player);
        when(kickEvent.getPlayer()).thenReturn(player);

        listener = new OrzPlayerEvent(
                plugin, geoIpAccessService, blacklistService, service, guideService, styles, maintenanceService);
    }

    @Test
    void onPlayerPreLogin_maintenance_disallowsAndSkipsChecks() {
        when(maintenanceService.isRunning()).thenReturn(true);

        listener.onPlayerPreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verifyNoInteractions(geoIpAccessService, blacklistService, service);
    }

    @Test
    void onPlayerPreLogin_alreadyDisallowed_returnsEarly() {
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);

        listener.onPlayerPreLogin(event);

        verifyNoInteractions(geoIpAccessService, blacklistService, service);
        verify(event, never()).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void onPlayerPreLogin_blacklisted_disallowsAndSkipsGeoIp() {
        when(blacklistService.isBlocked("1.2.3.4")).thenReturn(true);

        listener.onPlayerPreLogin(event);

        verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
        verifyNoInteractions(geoIpAccessService, service);
    }

    @Test
    void onPlayerPreLogin_emptyIp_skipsGeoIp() {
        InetAddress emptyAddr = mock(InetAddress.class);
        when(emptyAddr.getHostAddress()).thenReturn("");
        when(event.getAddress()).thenReturn(emptyAddr);

        listener.onPlayerPreLogin(event);

        verify(blacklistService).isBlocked("");
        verifyNoInteractions(geoIpAccessService, service);
        verify(event, never()).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    void onPlayerPreLogin_geoIpBlocked_forwardsDecision() {
        when(geoIpAccessService.decide("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(
                        new GeoIpAccessService.Decision(false, "US", List.of("CN"), "{}")));

        listener.onPlayerPreLogin(event);

        verify(service)
                .handleGeoIpPreLogin(
                        eq(event), eq("player1"), eq("1.2.3.4"), any(), eq(GeoIpAccessService.DECISION_TIMEOUT_MS));
    }

    @Test
    void onPlayerPreLogin_geoIpAllowed_forwardsDecision() {
        when(geoIpAccessService.decide("1.2.3.4"))
                .thenReturn(CompletableFuture.completedFuture(
                        new GeoIpAccessService.Decision(true, "CN", List.of("CN"), "{}")));

        listener.onPlayerPreLogin(event);

        verify(service)
                .handleGeoIpPreLogin(
                        eq(event), eq("player1"), eq("1.2.3.4"), any(), eq(GeoIpAccessService.DECISION_TIMEOUT_MS));
    }

    @Test
    void onPlayerJoin_notifiesJoinState() {
        listener.onPlayerJoin(joinEvent);

        verify(service).notifyPlayerState(player, PlayerEventService.PlayerState.JOIN);
    }

    @Test
    void onPlayerQuit_notifiesQuitState() {
        listener.onPlayerQuit(quitEvent);

        verify(service).notifyPlayerState(player, PlayerEventService.PlayerState.QUIT);
    }

    @Test
    void onPlayerKickLeave_notCancelled_notifiesKick() {
        when(kickEvent.isCancelled()).thenReturn(false);

        listener.onPlayerKickLeave(kickEvent);

        verify(service).notifyPlayerState(player, PlayerEventService.PlayerState.KICK);
    }

    @Test
    void onPlayerKickLeave_cancelled_skipsNotification() {
        when(kickEvent.isCancelled()).thenReturn(true);

        listener.onPlayerKickLeave(kickEvent);

        // 被取消的踢人：玩家仍在线上，不产生「被踢」通知
        verify(service, never()).notifyPlayerState(any(), any());
    }
}
