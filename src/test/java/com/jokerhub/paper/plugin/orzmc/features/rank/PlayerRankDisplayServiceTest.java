package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.RankColorsConfig;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * PlayerRankDisplayService 测试：颜色解析（OP 优先 / 等级组 / 关闭）、
 * 头顶队伍 + Tab 应用、OP 专用 orzmc-op 队伍、冲突让位、关闭还原、退出清理。
 */
class PlayerRankDisplayServiceTest {

    private ServerFacade serverFacade;
    private RankService rankService;
    private MockedStatic<Bukkit> bukkitMock;
    private ScoreboardManager manager;
    private Scoreboard board;

    @BeforeEach
    void setUp() {
        serverFacade = mock(ServerFacade.class);
        rankService = mock(RankService.class);
        bukkitMock = mockStatic(Bukkit.class);
        manager = mock(ScoreboardManager.class);
        board = mock(Scoreboard.class);
        bukkitMock.when(Bukkit::getScoreboardManager).thenReturn(manager);
        when(manager.getMainScoreboard()).thenReturn(board);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    private PlayerRankDisplayService service() {
        return service(RankColorsConfig.from(null));
    }

    private PlayerRankDisplayService service(RankColorsConfig config) {
        return new PlayerRankDisplayService(serverFacade, rankService, () -> config);
    }

    private PlayerRankDisplayService serviceWithTabEnabled() {
        return service(new RankColorsConfig(true, true, true, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS));
    }

    private Player mockPlayer(String name, boolean op) {
        Player p = mock(Player.class);
        when(p.getName()).thenReturn(name);
        when(p.isOnline()).thenReturn(true);
        when(p.isOp()).thenReturn(op);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.displayName()).thenReturn(Component.text(name));
        return p;
    }

    // ---- colorFor ----

    @Test
    void colorFor_disabled_returnsNull() {
        Player p = mockPlayer("Steve", false);
        RankColorsConfig disabled =
                new RankColorsConfig(false, true, true, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS);
        assertNull(service(disabled).colorFor(p));
    }

    @Test
    void colorFor_op_returnsOpColorWithoutRankLookup() {
        Player p = mockPlayer("Steve", true);
        assertEquals(NamedTextColor.GOLD, service().colorFor(p));
        verifyNoInteractions(rankService);
    }

    @Test
    void colorFor_memberGroup_returnsMemberColor() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        assertEquals(NamedTextColor.AQUA, service().colorFor(p));
    }

    @Test
    void colorFor_unknownGroup_returnsGray() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("weird-group");
        assertEquals(NamedTextColor.GRAY, service().colorFor(p));
    }

    // ---- applyTo ----

    @Test
    void applyTo_createsTeamAndSetsPlayerListName() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        when(board.getEntryTeam("Steve")).thenReturn(null);
        when(board.getTeam("orzmc-member")).thenReturn(null);
        Team team = mock(Team.class);
        when(board.registerNewTeam("orzmc-member")).thenReturn(team);

        serviceWithTabEnabled().applyTo(p);

        verify(team).color(NamedTextColor.AQUA);
        verify(team).removeEntry("Steve");
        verify(team).addEntry("Steve");
        ArgumentCaptor<Component> cap = ArgumentCaptor.forClass(Component.class);
        verify(p).playerListName(cap.capture());
        assertEquals(NamedTextColor.AQUA, cap.getValue().color());
    }

    @Test
    void applyTo_op_usesDedicatedOrzmcOpTeam() {
        Player p = mockPlayer("Steve", true);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("admin"); // OP 但组 admin
        when(board.getEntryTeam("Steve")).thenReturn(null);
        when(board.getTeam("orzmc-op")).thenReturn(null);
        Team team = mock(Team.class);
        when(board.registerNewTeam("orzmc-op")).thenReturn(team);

        serviceWithTabEnabled().applyTo(p);

        verify(board).registerNewTeam("orzmc-op");
        verify(team).color(NamedTextColor.GOLD);
        // 不按等级组建队，避免与同组非 OP 共享队伍导致串色
        verify(board, never()).registerNewTeam("orzmc-admin");
    }

    @Test
    void applyTo_moveToNewTeam_removesFromOldOrzmcTeam() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("builder");
        Team oldTeam = mock(Team.class);
        when(oldTeam.getName()).thenReturn("orzmc-default");
        when(board.getEntryTeam("Steve")).thenReturn(oldTeam);
        when(board.getTeam("orzmc-builder")).thenReturn(null);
        Team newTeam = mock(Team.class);
        when(board.registerNewTeam("orzmc-builder")).thenReturn(newTeam);

        serviceWithTabEnabled().applyTo(p);

        verify(oldTeam).removeEntry("Steve");
        verify(newTeam).color(NamedTextColor.GREEN);
        verify(newTeam).addEntry("Steve");
    }

    @Test
    void applyTo_foreignTeam_backsOffNametagButColorsTab() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("admin");
        Team foreign = mock(Team.class);
        when(foreign.getName()).thenReturn("TAB-123");
        when(board.getEntryTeam("Steve")).thenReturn(foreign);

        serviceWithTabEnabled().applyTo(p);

        verify(board, never()).registerNewTeam(anyString());
        verify(foreign, never()).removeEntry(anyString());
        verify(p).playerListName(any(Component.class)); // Tab 仍着色
    }

    @Test
    void applyTo_usesDisplayNameTextForTab_whileNametagKeepsRealName() {
        Player p = mockPlayer("Steve", false);
        when(p.displayName()).thenReturn(Component.text("CoolGuy")); // EssentialsX /nick 昵称
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        when(board.getEntryTeam("Steve")).thenReturn(null);
        when(board.getTeam("orzmc-member")).thenReturn(null);
        Team team = mock(Team.class);
        when(board.registerNewTeam("orzmc-member")).thenReturn(team);

        serviceWithTabEnabled().applyTo(p);

        // Tab 用昵称 + rank 色；头顶队伍 entry 仍是真实名（计分板限制）
        ArgumentCaptor<Component> cap = ArgumentCaptor.forClass(Component.class);
        verify(p).playerListName(cap.capture());
        assertEquals("CoolGuy", PlainTextComponentSerializer.plainText().serialize(cap.getValue()));
        assertEquals(NamedTextColor.AQUA, cap.getValue().color());
        verify(team).addEntry("Steve");
    }

    @Test
    void applyTo_nametagDisabled_stillColorsTabNoTeam() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        when(board.getEntryTeam("Steve")).thenReturn(null);
        RankColorsConfig cfg = new RankColorsConfig(true, false, true, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS);

        service(cfg).applyTo(p);

        // 只关头顶：Tab 仍着色、不建/不碰队伍（聊天由 colorFor 独立着色）
        verify(p).playerListName(Component.text("Steve").color(NamedTextColor.AQUA));
        verify(board, never()).registerNewTeam(anyString());
    }

    @Test
    void applyTo_tabDisabled_colorsNametagResetsTab() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        when(board.getEntryTeam("Steve")).thenReturn(null);
        when(board.getTeam("orzmc-member")).thenReturn(null);
        Team team = mock(Team.class);
        when(board.registerNewTeam("orzmc-member")).thenReturn(team);
        RankColorsConfig cfg = new RankColorsConfig(true, true, false, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS);

        service(cfg).applyTo(p);

        // 只关 Tab：头顶队伍仍着色，Tab 名置空→client 走 team/vanilla 回退（恢复原 team 前缀）
        verify(team).color(NamedTextColor.AQUA);
        verify(team).addEntry("Steve");
        verify(p).playerListName(null);
    }

    @Test
    void applyTo_tabDisabled_resetsTabRegardlessOfDisplayName() {
        Player p = mockPlayer("Steve", false);
        // 有 EssentialsX /nick 昵称也一律置空：还原「服务器原策略」优先于保留昵称
        // （置空后 client 用真实名+team 前缀渲染，与 vanilla 一致）
        when(p.displayName()).thenReturn(Component.text("CoolGuy").color(NamedTextColor.YELLOW));
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        when(board.getEntryTeam("Steve")).thenReturn(null);
        when(board.getTeam("orzmc-member")).thenReturn(null);
        Team team = mock(Team.class);
        when(board.registerNewTeam("orzmc-member")).thenReturn(team);
        RankColorsConfig cfg = new RankColorsConfig(true, true, false, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS);

        service(cfg).applyTo(p);

        // Tab 名置空（不携带任何 displayName/格式），头顶队伍 entry 仍用真实名
        verify(p).playerListName(null);
        verify(team).addEntry("Steve");
    }

    @Test
    void applyTo_tabDisabled_nametagDisabled_resetsTabNoTeam() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        when(board.getEntryTeam("Steve")).thenReturn(null);
        RankColorsConfig cfg = new RankColorsConfig(true, false, false, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS);

        service(cfg).applyTo(p);

        // 头顶+Tab 都关：不建/不碰队伍，Tab 名置空→恢复服务器原策略（聊天由 colorFor 独立着色）
        verify(p).playerListName(null);
        verify(board, never()).registerNewTeam(anyString());
    }

    @Test
    void applyTo_disabled_cleansTeamAndNullsTab() {
        Player p = mockPlayer("Steve", false);
        RankColorsConfig disabled =
                new RankColorsConfig(false, true, true, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS);
        Team orzmc = mock(Team.class);
        when(orzmc.getName()).thenReturn("orzmc-default");
        when(board.getEntryTeam("Steve")).thenReturn(orzmc);

        service(disabled).applyTo(p);

        verify(orzmc).removeEntry("Steve");
        // 总开关关闭同样置空：恢复服务器原显示策略（team/vanilla 渲染，含原 team 前缀）
        verify(p).playerListName(null);
    }

    @Test
    void applyTo_disabled_nullsTabRegardlessOfNick() {
        Player p = mockPlayer("Steve", false);
        when(p.displayName()).thenReturn(Component.text("CoolGuy")); // EssentialsX /nick 昵称
        RankColorsConfig disabled =
                new RankColorsConfig(false, true, true, NamedTextColor.GOLD, RankColorsConfig.DEFAULTS);

        service(disabled).applyTo(p);

        // 置空优先于保留昵称：恢复服务器原策略（真实名 + team 前缀），昵称不强行写进 Tab
        verify(p).playerListName(null);
    }

    @Test
    void applyTo_overlongGroupName_skipsNametagButColorsTab() {
        Player p = mockPlayer("Steve", false);
        // 自定义超长 track 组名 → orzmc-<组> 超 16 协议上限：降级为只 Tab 着色，不建队伍
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("super-long-group-name");
        when(board.getEntryTeam("Steve")).thenReturn(null);

        serviceWithTabEnabled().applyTo(p);

        verify(board, never()).registerNewTeam(anyString());
        verify(p).playerListName(Component.text("Steve").color(NamedTextColor.GRAY));
    }

    // ---- removeFor ----

    @Test
    void removeFor_removesFromOrzmcTeam() {
        Player p = mockPlayer("Steve", false);
        Team orzmc = mock(Team.class);
        when(orzmc.getName()).thenReturn("orzmc-default");
        when(board.getEntryTeam("Steve")).thenReturn(orzmc);

        service().removeFor(p);

        verify(orzmc).removeEntry("Steve");
    }

    @Test
    void removeFor_ignoresForeignTeam() {
        Player p = mockPlayer("Steve", false);
        Team foreign = mock(Team.class);
        when(foreign.getName()).thenReturn("TAB-123");
        when(board.getEntryTeam("Steve")).thenReturn(foreign);

        service().removeFor(p);

        verify(foreign, never()).removeEntry(anyString());
    }

    // ---- refresh ----

    @Test
    void refresh_onlinePlayer_appliesDisplay() {
        Player p = mockPlayer("Steve", false);
        when(rankService.currentGroup(p.getUniqueId())).thenReturn("member");
        bukkitMock.when(() -> Bukkit.getPlayer(p.getUniqueId())).thenReturn(p);
        when(board.getEntryTeam("Steve")).thenReturn(null);
        when(board.getTeam("orzmc-member")).thenReturn(null);
        Team team = mock(Team.class);
        when(board.registerNewTeam("orzmc-member")).thenReturn(team);

        service().refresh(p.getUniqueId());

        verify(team).addEntry("Steve");
    }

    @Test
    void refresh_offlinePlayer_doesNothing() {
        UUID id = UUID.randomUUID();
        bukkitMock.when(() -> Bukkit.getPlayer(id)).thenReturn(null);

        service().refresh(id);

        verifyNoInteractions(board);
    }

    @Test
    void refreshAllOnline_appliesToEveryPlayer() {
        Player a = mockPlayer("A", false);
        when(rankService.currentGroup(a.getUniqueId())).thenReturn("admin");
        Player b = mockPlayer("B", false);
        when(rankService.currentGroup(b.getUniqueId())).thenReturn("member");
        bukkitMock.when(Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(a, b));
        when(board.getEntryTeam("A")).thenReturn(null);
        when(board.getEntryTeam("B")).thenReturn(null);
        when(board.getTeam("orzmc-admin")).thenReturn(null);
        when(board.getTeam("orzmc-member")).thenReturn(null);
        Team admin = mock(Team.class);
        when(board.registerNewTeam("orzmc-admin")).thenReturn(admin);
        Team member = mock(Team.class);
        when(board.registerNewTeam("orzmc-member")).thenReturn(member);

        service().refreshAllOnline();

        verify(admin).addEntry("A");
        verify(member).addEntry("B");
    }
}
