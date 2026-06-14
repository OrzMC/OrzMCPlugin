package com.jokerhub.paper.plugin.orzmc.features.teleport;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TeleportBowEventServiceTest extends ServiceTestBase {

    @Mock
    private TeleportBowService bowService;

    @Mock
    private ProjectileHitEvent hitEvent;

    @Mock
    private EntityShootBowEvent shootEvent;

    private TeleportBowEventService service;

    @BeforeEach
    void setUp() {
        service = new TeleportBowEventService(bowService);
    }

    @Test
    void handleProjectileHit_nonArrow_doesNothing() {
        Projectile proj = mock(Projectile.class);
        when(hitEvent.getEntity()).thenReturn(proj);

        service.handleProjectileHit(hitEvent);

        verifyNoInteractions(bowService);
    }

    @Test
    void handleProjectileHit_arrowNonPlayerShooter_doesNothing() {
        Arrow arrow = mock(Arrow.class);
        when(hitEvent.getEntity()).thenReturn(arrow);
        when(arrow.getShooter()).thenReturn(mock(org.bukkit.projectiles.ProjectileSource.class));

        service.handleProjectileHit(hitEvent);

        verifyNoInteractions(bowService);
    }

    @Test
    void handleProjectileHit_tpBowArrow_callsHandleArrowHit() {
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        when(hitEvent.getEntity()).thenReturn(arrow);
        when(arrow.getShooter()).thenReturn(player);
        when(bowService.isTPBowArrow(arrow)).thenReturn(true);

        service.handleProjectileHit(hitEvent);

        verify(bowService).handleArrowHit(arrow, player);
    }

    @Test
    void handleProjectileHit_regularArrow_skips() {
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        when(hitEvent.getEntity()).thenReturn(arrow);
        when(arrow.getShooter()).thenReturn(player);
        when(bowService.isTPBowArrow(arrow)).thenReturn(false);

        service.handleProjectileHit(hitEvent);

        verify(bowService, never()).handleArrowHit(any(), any());
    }

    @Test
    void handleShootBow_nonPlayer_doesNothing() {
        LivingEntity living = mock(LivingEntity.class);
        when(shootEvent.getEntity()).thenReturn(living);

        service.handleShootBow(shootEvent);

        verifyNoInteractions(bowService);
    }

    @Test
    void handleShootBow_player_callsMarkArrow() {
        Player player = mock(Player.class);
        when(shootEvent.getEntity()).thenReturn(player);

        service.handleShootBow(shootEvent);

        verify(bowService).markArrow(shootEvent);
    }
}
