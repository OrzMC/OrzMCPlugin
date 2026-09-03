package com.jokerhub.paper.plugin.orzmc.features.rank;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.GamemodeCorrectionConfig;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * GamemodeCorrectionService 测试：创造/观察/冒险模式无对应权限 → 切生存；
 * 有权限/生存模式/离线/功能关闭 → 不处理；观察模式切生存前安全位置处理（传回床点/出生点）；防抖。
 */
class GamemodeCorrectionServiceTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final String PERM_CREATIVE = "essentials.gamemode.creative";
    private static final String PERM_SPECTATOR = "essentials.gamemode.spectator";
    private static final String PERM_ADVENTURE = "essentials.gamemode.adventure";

    private OrzTextStyles styles;
    private GamemodeCorrectionConfig config;
    private Player player;
    private org.bukkit.plugin.java.JavaPlugin mockPlugin;

    @BeforeEach
    void setUp() {
        styles = mock(OrzTextStyles.class);
        when(styles.info(anyString())).thenReturn(Component.text(GamemodeCorrectionService.FIX_MESSAGE));
        mockPlugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        config = new GamemodeCorrectionConfig(true, 2000L, true);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.isOnline()).thenReturn(true);
    }

    private GamemodeCorrectionService service() {
        return new GamemodeCorrectionService(mockPlugin, () -> config, styles);
    }

    // ---- CREATIVE ----

    @Test
    void creative_withoutPermission_switchesToSurvival() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(false);

        boolean corrected = service().correctIfNeeded(player);

        assertTrue(corrected);
        verify(player).setGameMode(GameMode.SURVIVAL);
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void creative_withPermission_doesNotSwitch() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(true);

        boolean corrected = service().correctIfNeeded(player);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
    }

    // ---- SPECTATOR ----

    @Test
    void spectator_withoutPermission_unsafeYLocation_teleportsToSpawnAndSwitches() {
        World world = mock(World.class);
        Location loc = unsafeLowLocation(world);
        when(world.getMinHeight()).thenReturn(-64);
        when(player.getLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(player.hasPermission(PERM_SPECTATOR)).thenReturn(false);
        Location spawn = mock(Location.class);
        when(world.getSpawnLocation()).thenReturn(spawn);

        boolean corrected = service().correctIfNeeded(player);

        assertTrue(corrected);
        verify(player).teleport(spawn);
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void spectator_withoutPermission_unsafeBelowBlock_teleportsToSpawn() {
        World world = mock(World.class);
        Location loc = safeCoordsLocation(world);
        when(world.getMinHeight()).thenReturn(-64);
        // 脚下是空气（悬空飞行）→ 不安全
        Block below = mock(Block.class);
        when(below.getType()).thenReturn(Material.AIR);
        Block at = mock(Block.class);
        when(at.isPassable()).thenReturn(true);
        when(world.getBlockAt(0, 99, 0)).thenReturn(below);
        when(world.getBlockAt(0, 100, 0)).thenReturn(at);
        when(player.getLocation()).thenReturn(loc);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(player.hasPermission(PERM_SPECTATOR)).thenReturn(false);
        when(player.getBedSpawnLocation()).thenReturn(null);
        when(player.getWorld()).thenReturn(world);
        Location spawn = mock(Location.class);
        when(world.getSpawnLocation()).thenReturn(spawn);

        boolean corrected = service().correctIfNeeded(player);

        assertTrue(corrected);
        verify(player).teleport(spawn);
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void spectator_withoutPermission_safeLocation_noTeleport() {
        World world = mock(World.class);
        Location loc = safeCoordsLocation(world);
        when(world.getMinHeight()).thenReturn(-64);
        Block below = mock(Block.class);
        when(below.getType()).thenReturn(Material.STONE);
        Block at = mock(Block.class);
        when(at.isPassable()).thenReturn(true);
        when(world.getBlockAt(0, 99, 0)).thenReturn(below);
        when(world.getBlockAt(0, 100, 0)).thenReturn(at);
        when(player.getLocation()).thenReturn(loc);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(player.hasPermission(PERM_SPECTATOR)).thenReturn(false);

        boolean corrected = service().correctIfNeeded(player);

        assertTrue(corrected);
        verify(player, never()).teleport(any(Location.class));
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void spectator_withoutPermission_unsafeLocation_prefersBedSpawn() {
        World world = mock(World.class);
        Location loc = unsafeLowLocation(world);
        when(world.getMinHeight()).thenReturn(-64);
        when(player.getLocation()).thenReturn(loc);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(player.hasPermission(PERM_SPECTATOR)).thenReturn(false);
        Location bed = mock(Location.class);
        when(player.getBedSpawnLocation()).thenReturn(bed);

        boolean corrected = service().correctIfNeeded(player);

        assertTrue(corrected);
        // 有床点：优先传床点（世界出生点分支不执行）
        verify(player).teleport(bed);
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void spectator_withPermission_doesNotSwitch() {
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(player.hasPermission(PERM_SPECTATOR)).thenReturn(true);

        boolean corrected = service().correctIfNeeded(player);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
    }

    @Test
    void spectator_teleportFixDisabled_noTeleportEvenIfUnsafe() {
        config = new GamemodeCorrectionConfig(true, 2000L, false);
        World world = mock(World.class);
        Location loc = unsafeLowLocation(world);
        when(world.getMinHeight()).thenReturn(-64);
        when(player.getLocation()).thenReturn(loc);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(player.hasPermission(PERM_SPECTATOR)).thenReturn(false);

        boolean corrected = service().correctIfNeeded(player);

        assertTrue(corrected);
        verify(player, never()).teleport(any(Location.class));
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    // ---- ADVENTURE ----

    @Test
    void adventure_withoutPermission_switchesToSurvival() {
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(player.hasPermission(PERM_ADVENTURE)).thenReturn(false);

        boolean corrected = service().correctIfNeeded(player);

        assertTrue(corrected);
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void adventure_withPermission_doesNotSwitch() {
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(player.hasPermission(PERM_ADVENTURE)).thenReturn(true);

        boolean corrected = service().correctIfNeeded(player);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
    }

    // ---- SURVIVAL / 边界 ----

    @Test
    void survival_doesNothing() {
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        boolean corrected = service().correctIfNeeded(player);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void offlinePlayer_skipped() {
        when(player.isOnline()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(false);

        boolean corrected = service().correctIfNeeded(player);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
    }

    @Test
    void nullPlayer_skipped() {
        boolean corrected = service().correctIfNeeded((Player) null);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
    }

    @Test
    void disabledConfig_doesNotCorrect() {
        config = new GamemodeCorrectionConfig(false, 2000L, true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(false);

        boolean corrected = service().correctIfNeeded(player);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
    }

    @Test
    void nullConfig_doesNotCorrect() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(false);

        GamemodeCorrectionService svc = new GamemodeCorrectionService(mockPlugin, () -> null, styles);
        boolean corrected = svc.correctIfNeeded(player);

        assertFalse(corrected);
        verify(player, never()).setGameMode(any());
    }

    // ---- 防抖 ----

    @Test
    void debounce_withinWindow_skipsSecondCorrection() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(false);

        GamemodeCorrectionService service = service();
        assertTrue(service.correctIfNeeded(player));
        // mock 的 getGameMode 不会因 setGameMode 变化，第二次调用仍走防抖判定
        assertFalse(service.correctIfNeeded(player));

        verify(player, times(1)).setGameMode(GameMode.SURVIVAL);
        verify(player, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void debounce_zero_alwaysAllows() {
        config = new GamemodeCorrectionConfig(true, 0L, true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(false);

        GamemodeCorrectionService service = service();
        assertTrue(service.correctIfNeeded(player));
        assertTrue(service.correctIfNeeded(player));

        verify(player, times(2)).setGameMode(GameMode.SURVIVAL);
    }

    // ---- UUID 变体 ----

    @Test
    void correctIfNeeded_uuid_onlinePlayer_corrects() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.hasPermission(PERM_CREATIVE)).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
            boolean corrected = service().correctIfNeeded(PLAYER_ID);
            assertTrue(corrected);
        }
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    // ---- 测试辅助 ----

    /** 构造 y=100 的安全坐标 Location mock（脚下方块见各用例 stub）。 */
    private static Location safeCoordsLocation(World world) {
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getY()).thenReturn(100.0);
        when(loc.getBlockX()).thenReturn(0);
        when(loc.getBlockY()).thenReturn(100);
        when(loc.getBlockZ()).thenReturn(0);
        return loc;
    }

    /** 构造 y=-60 的过低 Location mock（低于 minHeight+10 → 不安全）。 */
    private static Location unsafeLowLocation(World world) {
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getY()).thenReturn(-60.0);
        return loc;
    }
}
