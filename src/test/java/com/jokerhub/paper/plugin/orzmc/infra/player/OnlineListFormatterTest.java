package com.jokerhub.paper.plugin.orzmc.infra.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** OnlineListFormatter 测试：单行/多行/排除逻辑（$l 命令与上下线广播共用的事实源）。 */
class OnlineListFormatterTest {

    private Player player(String name) {
        Player p = mock(Player.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getName()).thenReturn(name);
        when(p.getPlayerProfile()).thenReturn(profile);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        return p;
    }

    @Test
    void line_withoutRankService_omitsGroup() {
        OnlineListFormatter f = new OnlineListFormatter();
        assertEquals("Alice 生存模式", f.line(player("Alice")));
    }

    @Test
    void line_withRankService_includesGroupDisplayName() {
        RankService rankService = mock(RankService.class);
        OnlineListFormatter f = new OnlineListFormatter();
        f.setRankService(rankService);
        Player p = player("Alice");
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("admin");

        assertEquals("Alice 生存模式 管理员", f.line(p));
    }

    @Test
    void list_joinsLines() {
        OnlineListFormatter f = new OnlineListFormatter();
        Player a = player("Alice");
        Player b = player("Bob");

        String list = f.list(List.of(a, b));

        assertTrue(list.contains("Alice 生存模式"));
        assertTrue(list.contains("Bob 生存模式"));
    }

    @Test
    void list_excludesGivenPlayer() {
        OnlineListFormatter f = new OnlineListFormatter();
        Player a = player("Alice");
        Player b = player("Bob");

        String list = f.list(List.of(a, b), b.getUniqueId());

        assertTrue(list.contains("Alice"));
        assertFalse(list.contains("Bob"), "exclude 的玩家不应出现, got: " + list);
    }
}
