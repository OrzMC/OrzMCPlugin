package com.jokerhub.paper.plugin.orzmc.features.whitelist;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WhitelistServiceTest {

    private WhitelistService service;
    private Server server;
    private OfflinePlayer player1;
    private OfflinePlayer player2;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        service = WhitelistService.defaultImpl(plugin);
        server = mock(Server.class);
        player1 = mock(OfflinePlayer.class);
        player2 = mock(OfflinePlayer.class);
    }

    @Test
    void buildWhitelistLines_sortsByLastSeenDesc() {
        when(player1.getName()).thenReturn("Alice");
        when(player1.isOnline()).thenReturn(true);
        when(player1.getLastSeen()).thenReturn(2000L);
        when(player2.getName()).thenReturn("Bob");
        when(player2.isOnline()).thenReturn(false);
        when(player2.getLastSeen()).thenReturn(1000L);
        when(server.getWhitelistedPlayers()).thenReturn(Set.of(player1, player2));

        List<String> lines = service.buildWhitelistLines(server);

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("Alice"));
        assertTrue(lines.get(0).contains("•")); // online marker
        assertTrue(lines.get(1).contains("Bob"));
        assertTrue(lines.get(1).contains("◦")); // offline marker
    }

    @Test
    void buildWhitelistLines_emptyList() {
        when(server.getWhitelistedPlayers()).thenReturn(new HashSet<>());
        assertTrue(service.buildWhitelistLines(server).isEmpty());
    }

    @Test
    void buildWhitelistLines_nullName_skipped() {
        when(player1.getName()).thenReturn(null);
        when(player1.getLastSeen()).thenReturn(0L);
        when(server.getWhitelistedPlayers()).thenReturn(Set.of(player1));

        List<String> lines = service.buildWhitelistLines(server);
        assertTrue(lines.get(0).contains("null")); // OfflinePlayer.getName() can return null
    }

    @Test
    void cleanupInactivePlayers_removesOldPlayers() {
        long now = System.currentTimeMillis();
        when(player1.getLastSeen()).thenReturn(now); // active
        when(player1.isWhitelisted()).thenReturn(true);
        when(player2.getLastSeen()).thenReturn(0L); // never seen
        when(player2.isWhitelisted()).thenReturn(true);
        when(player2.getName()).thenReturn("Bob");

        when(server.getWhitelistedPlayers()).thenReturn(Set.of(player1, player2));

        Set<String> removed = service.cleanupInactivePlayers(server, 30);

        assertTrue(removed.contains("Bob"), "removed: " + removed);
        verify(player2).setWhitelisted(false);
    }

    @Test
    void cleanupInactivePlayers_kicksOnlinePlayerInPlayerRegion() {
        when(player2.getLastSeen()).thenReturn(0L); // never seen → 过期
        when(player2.isWhitelisted()).thenReturn(true);
        when(player2.getName()).thenReturn("Bob");
        when(server.getWhitelistedPlayers()).thenReturn(Set.of(player2));
        Player online = mock(Player.class);
        when(server.getPlayer(player2.getUniqueId())).thenReturn(online);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(online.getScheduler()).thenReturn(entityScheduler);

        service.cleanupInactivePlayers(server, 30);

        // Folia 区域亲和：kick 必须经 EntityScheduler 投递到玩家 region 线程
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(entityScheduler).run(eq(plugin), captor.capture(), any(Runnable.class));
        captor.getValue().accept(mock(ScheduledTask.class));
        verify(online).kick();
    }

    @Test
    void removePlayers_kicksOnlinePlayerInPlayerRegion() {
        when(player1.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player1.getName()).thenReturn("Alice");
        when(player1.isWhitelisted()).thenReturn(true);
        when(server.getOfflinePlayer("Alice")).thenReturn(player1);
        when(server.getWhitelistedPlayers()).thenReturn(Set.of(player1));
        Player online = mock(Player.class);
        when(server.getPlayer(player1.getUniqueId())).thenReturn(online);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(online.getScheduler()).thenReturn(entityScheduler);

        service.removePlayers(server, Set.of("Alice"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(entityScheduler).run(eq(plugin), captor.capture(), any(Runnable.class));
        captor.getValue().accept(mock(ScheduledTask.class));
        verify(online).kick();
    }
}
