package com.jokerhub.paper.plugin.orzmc.infra.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerDisplayNamesTest {

    private Player player;
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        profile = mock(PlayerProfile.class);
        when(player.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn("TestPlayer");
    }

    @Test
    void opCreativePlayer() {
        when(player.isOp()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertEquals("TestPlayer(op) 创造模式", PlayerDisplayNames.format(player));
    }

    @Test
    void nonOpSurvivalPlayer() {
        when(player.isOp()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        assertEquals("TestPlayer 生存模式", PlayerDisplayNames.format(player));
    }

    @Test
    void adventureMode() {
        when(player.isOp()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        assertEquals("TestPlayer 冒险模式", PlayerDisplayNames.format(player));
    }

    @Test
    void spectatorMode() {
        when(player.isOp()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertEquals("TestPlayer 观察模式", PlayerDisplayNames.format(player));
    }
}
