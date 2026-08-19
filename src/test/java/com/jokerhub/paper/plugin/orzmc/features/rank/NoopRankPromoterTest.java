package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** NoopRankPromoter 测试：LP 缺失时的降级行为（类加载零 LP 依赖）。 */
class NoopRankPromoterTest {

    private NoopRankPromoter promoter;
    private MockedStatic<org.bukkit.Bukkit> bukkitMock;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        promoter = new NoopRankPromoter();
        bukkitMock = mockStatic(org.bukkit.Bukkit.class);
        bukkitMock.when(() -> org.bukkit.Bukkit.getPluginManager()).thenReturn(mock(PluginManager.class));
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void isAvailable_alwaysFalse() {
        assertFalse(promoter.isAvailable());
    }

    @Test
    void promote_returnsNull() {
        assertNull(promoter.promote(id));
    }

    @Test
    void demote_returnsNull() {
        assertNull(promoter.demote(id));
    }

    @Test
    void currentTrackGroup_returnsNull() {
        assertNull(promoter.currentTrackGroup(id));
    }

    @Test
    void playerName_returnsEmpty() {
        assertEquals(Optional.empty(), promoter.playerName(id));
    }

    @Test
    void resolvePlayerId_knownPlayer_returnsUuid() {
        org.bukkit.OfflinePlayer p = mock(org.bukkit.OfflinePlayer.class);
        when(p.hasPlayedBefore()).thenReturn(true);
        when(p.getUniqueId()).thenReturn(id);
        bukkitMock.when(() -> org.bukkit.Bukkit.getOfflinePlayer("TestPlayer")).thenReturn(p);

        assertEquals(id, promoter.resolvePlayerId("TestPlayer"));
    }
}
