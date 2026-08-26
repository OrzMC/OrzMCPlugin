package com.jokerhub.paper.plugin.orzmc.events;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.features.guide.GuideService;
import com.jokerhub.paper.plugin.orzmc.features.player.LoginAccessControlService;
import com.jokerhub.paper.plugin.orzmc.features.player.PlayerEventService;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
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
    private LoginAccessControlService loginAccessControlService;

    @Mock
    private PlayerEventService service;

    @Mock
    private GuideService guideService;

    @Mock
    private AsyncPlayerPreLoginEvent preLoginEvent;

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
    void setUp() {
        when(joinEvent.getPlayer()).thenReturn(player);
        when(quitEvent.getPlayer()).thenReturn(player);
        when(kickEvent.getPlayer()).thenReturn(player);

        listener = new OrzPlayerEvent(plugin, loginAccessControlService, guideService, service);
    }

    @Test
    void onPlayerPreLogin_delegatesToLoginAccessControl() {
        listener.onPlayerPreLogin(preLoginEvent);

        verify(loginAccessControlService).handlePreLogin(preLoginEvent);
        verifyNoInteractions(service, guideService);
    }

    @Test
    void onPlayerJoin_givesGuideAndNotifiesJoinState() {
        listener.onPlayerJoin(joinEvent);

        verify(guideService).giveIfFirstJoin(player);
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
    void onPlayerKickLeave_cancelled_doesNotNotify() {
        when(kickEvent.isCancelled()).thenReturn(true);

        listener.onPlayerKickLeave(kickEvent);

        verifyNoInteractions(service);
    }
}
