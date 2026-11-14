package com.jokerhub.paper.plugin.orzmc.features.prison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokerhub.paper.plugin.orzmc.features.review.ReviewNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.PrisonConfig;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.TestI18n;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * PrisonService 单测：坐牢/解除的主流程 + 位置序列化解析 + Folia 实体调度边界。
 *
 * <p>为什么要单独补：该模块此前**只有 1 个分支被覆盖**（`LuckPermsPrisonStoreTest` 覆盖的是 store 层），
 * 而它同时踩三条历史红线——LP 异步、Folia 线程投递、传送回原位置（配置错写会把人传进虚空）。
 * 这里用 {@code mockStatic(Bukkit)} 把静态入口（getPlayer/getWorld）收进测试，覆盖各降级分支。</p>
 */
class PrisonServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private PrisonLpGateway gateway;
    private JavaPlugin plugin;
    private OrzTextStyles styles;
    private ReviewNotifier notifier;
    private I18nService i18n;
    private PrisonService service;
    private MockedStatic<Bukkit> bukkit;

    private PrisonConfig config;

    @BeforeEach
    void setUp() {
        bukkit = Mockito.mockStatic(Bukkit.class);
        gateway = mock(PrisonLpGateway.class);
        plugin = mock(JavaPlugin.class);
        styles = mock(OrzTextStyles.class);
        notifier = mock(ReviewNotifier.class);
        i18n = TestI18n.newService();
        config = new PrisonConfig(PrisonConfig.DEFAULT_CELL_LOCATION);
        when(styles.error(anyString())).thenAnswer(i -> Component.text((String) i.getArgument(0)));
        when(styles.info(anyString())).thenAnswer(i -> Component.text((String) i.getArgument(0)));
        when(styles.success(anyString())).thenAnswer(i -> Component.text((String) i.getArgument(0)));
        service = new PrisonService(gateway, plugin, () -> config, styles, notifier, id -> "Alice", i18n);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    // ===================== isPrisoner =====================

    @Test
    void isPrisoner_gatewayUnavailable_isFalseWithoutTouchingGateway() {
        when(gateway.isAvailable()).thenReturn(false);

        assertFalse(service.isPrisoner(PLAYER_ID));
        verify(gateway, never()).isPrisoner(any());
    }

    @Test
    void isPrisoner_gatewayAvailable_delegates() {
        when(gateway.isAvailable()).thenReturn(true);
        when(gateway.isPrisoner(PLAYER_ID)).thenReturn(true);

        assertTrue(service.isPrisoner(PLAYER_ID));
    }

    // ===================== imprison =====================

    @Test
    void imprison_gatewayUnavailable_returnsFailure() {
        when(gateway.isAvailable()).thenReturn(false);

        PrisonService.Result result = service.imprison(PLAYER_ID).join();

        assertInstanceOf(PrisonService.Result.Failure.class, result);
        verify(gateway, never()).imprison(any(), any());
    }

    @Test
    void imprison_offlinePlayer_passesNullLocationAndNotifies() {
        when(gateway.isAvailable()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(null);
        when(gateway.imprison(PLAYER_ID, null))
                .thenReturn(CompletableFuture.completedFuture(new PrisonLpGateway.ImprisonOutcome(true, "member")));

        PrisonService.Result result = service.imprison(PLAYER_ID).join();

        assertInstanceOf(PrisonService.Result.Success.class, result);
        verify(notifier).gameMessage(eq(PLAYER_ID), anyString());
        verify(notifier).groupEvent(eq("prison_imprisoned"), any());
    }

    @Test
    void imprison_onlinePlayer_recordsSerializedLocationThenTeleportsToCell() {
        when(gateway.isAvailable()).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        Player player = mockPlayer(world, true, new Location(world, 1.5, 64.0, -2.5, 90f, 10f));
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        when(gateway.imprison(eq(PLAYER_ID), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new PrisonLpGateway.ImprisonOutcome(true, "member")));

        PrisonService.Result result = service.imprison(PLAYER_ID).join();

        assertInstanceOf(PrisonService.Result.Success.class, result);
        // 位置被序列化后传给 LP（原位置记忆）
        verify(gateway).imprison(eq(PLAYER_ID), eq("world,1.5,64.0,-2.5,90.0,10.0"));
        // 在线则传送牢房（config 默认 world,0,100,0,0,0）
        verify(player, org.mockito.Mockito.atLeastOnce()).teleportAsync(any(Location.class));
    }

    @Test
    void imprison_lpFailure_returnsFailureAndSkipsNotifications() {
        when(gateway.isAvailable()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(null);
        when(gateway.imprison(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new PrisonLpGateway.ImprisonOutcome(false, null)));

        PrisonService.Result result = service.imprison(PLAYER_ID).join();

        assertInstanceOf(PrisonService.Result.Failure.class, result);
        verify(notifier, never()).gameMessage(any(), anyString());
        verify(notifier, never()).groupEvent(anyString(), any());
    }

    @Test
    void imprison_locationReadFailure_fallsBackToNullLocation() {
        when(gateway.isAvailable()).thenReturn(true);
        World world = mock(World.class);
        Player player = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getScheduler()).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
        // 实体调度拒绝执行（玩家刚好掉线/区域卸载）→ error 回调
        // EntityScheduler#execute 返回 boolean（true=已入队），不是 ScheduledTask
        doAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return true;
                })
                .when(scheduler)
                .execute(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
        when(gateway.imprison(PLAYER_ID, null))
                .thenReturn(CompletableFuture.completedFuture(new PrisonLpGateway.ImprisonOutcome(true, "member")));

        service.imprison(PLAYER_ID).join();

        verify(gateway).imprison(PLAYER_ID, null);
    }

    // ===================== release =====================

    @Test
    void release_gatewayUnavailable_returnsFailure() {
        when(gateway.isAvailable()).thenReturn(false);

        assertInstanceOf(
                PrisonService.Result.Failure.class, service.release(PLAYER_ID).join());
    }

    @Test
    void release_lpFailure_returnsFailure() {
        when(gateway.isAvailable()).thenReturn(true);
        when(gateway.release(PLAYER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new PrisonLpGateway.ReleaseOutcome(false, true, "member", null)));

        assertInstanceOf(
                PrisonService.Result.Failure.class, service.release(PLAYER_ID).join());
        verify(notifier, never()).groupEvent(anyString(), any());
    }

    @Test
    void release_notPrisoner_returnsInfoAndSkipsTeleportAndBroadcast() {
        when(gateway.isAvailable()).thenReturn(true);
        when(gateway.release(PLAYER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new PrisonLpGateway.ReleaseOutcome(true, false, "member", null)));

        assertInstanceOf(
                PrisonService.Result.Success.class, service.release(PLAYER_ID).join());
        verify(notifier, never()).groupEvent(anyString(), any());
        verify(notifier, never()).gameMessage(any(), anyString());
    }

    @Test
    void release_success_restoresSavedLocationAndBroadcasts() {
        when(gateway.isAvailable()).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 8, 65, 8));
        Player player = mockPlayer(world, true, new Location(world, 0, 70, 0));
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        when(gateway.release(PLAYER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new PrisonLpGateway.ReleaseOutcome(true, true, "member", "world,1.0,2.0,3.0,0.0,0.0")));

        assertInstanceOf(
                PrisonService.Result.Success.class, service.release(PLAYER_ID).join());

        org.mockito.ArgumentCaptor<Location> captor = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(player, org.mockito.Mockito.atLeastOnce()).teleportAsync(captor.capture());
        Location restored = captor.getValue();
        assertEquals(1.0, restored.getX(), 1e-9);
        assertEquals(2.0, restored.getY(), 1e-9);
        verify(notifier).gameMessage(eq(PLAYER_ID), anyString());
        verify(notifier).groupEvent(eq("prison_released"), any());
    }

    @Test
    void release_invalidSavedLocation_fallsBackToWorldSpawn() {
        when(gateway.isAvailable()).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location spawn = new Location(world, 8, 65, 8);
        when(world.getSpawnLocation()).thenReturn(spawn);
        Player player = mockPlayer(world, true, new Location(world, 0, 70, 0));
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
        when(gateway.release(PLAYER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new PrisonLpGateway.ReleaseOutcome(true, true, "member", "not-a-location")));

        service.release(PLAYER_ID).join();

        verify(player, org.mockito.Mockito.atLeastOnce()).teleportAsync(spawn);
    }

    @Test
    void release_offlinePlayer_skipsTeleportButStillNotifies() {
        when(gateway.isAvailable()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(null);
        when(gateway.release(PLAYER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new PrisonLpGateway.ReleaseOutcome(true, true, "member", "world,1,2,3,0,0")));

        service.release(PLAYER_ID).join();

        verify(notifier).groupEvent(eq("prison_released"), any());
    }

    // ===================== teleportToCell 边界 =====================

    @Test
    void teleportToCell_offlinePlayer_isNoop() {
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(null);

        service.teleportToCell(PLAYER_ID);
    }

    @Test
    void teleportToCell_blankCellConfig_fallsBackToWorldSpawn() {
        config = new PrisonConfig("  ");
        World world = mock(World.class);
        Location spawn = new Location(world, 0, 64, 0);
        when(world.getSpawnLocation()).thenReturn(spawn);
        Player player = mockPlayer(world, true, spawn);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);

        service.teleportToCell(PLAYER_ID);

        verify(player).teleportAsync(spawn);
    }

    @Test
    void teleportToCell_validCellConfig_usesConfiguredLocation() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        Player player = mockPlayer(world, true, new Location(world, 0, 70, 0));
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);

        service.teleportToCell(PLAYER_ID);

        org.mockito.ArgumentCaptor<Location> captor = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(player).teleportAsync(captor.capture());
        assertEquals(100.0, captor.getValue().getY(), 1e-9);
    }

    @Test
    void teleportToCell_playerOfflineInsideRegionTask_isNoop() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getScheduler()).thenReturn(scheduler);
        // 任务真正执行时玩家已离线
        when(player.isOnline()).thenReturn(false);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
        runScheduler(scheduler);

        service.teleportToCell(PLAYER_ID);

        verify(player, never()).teleportAsync(any());
    }

    @Test
    void teleportToCell_nullWorldAndUnconfiguredCell_noTeleport() {
        config = new PrisonConfig("");
        Player player = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getWorld()).thenReturn(null);
        when(player.getScheduler()).thenReturn(scheduler);
        when(player.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
        runScheduler(scheduler);

        service.teleportToCell(PLAYER_ID);

        verify(player, never()).teleportAsync(any());
    }

    // ===================== 位置序列化/解析（配置错写保护） =====================

    @Test
    void parseLocation_rejectsMalformedAndUnsafeInput() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

        assertNull(PrisonService.parseLocation(null));
        assertNull(PrisonService.parseLocation("   "));
        assertNull(PrisonService.parseLocation("world,1,2")); // 字段不足
        assertNull(PrisonService.parseLocation("unknown,1,2,3")); // 世界不存在
        assertNull(PrisonService.parseLocation("world,1,999,3")); // 超出世界高度
        assertNull(PrisonService.parseLocation("world,1,-100,3")); // 低于最小高度
        assertNull(PrisonService.parseLocation("world,a,2,3")); // 数字格式错误
    }

    @Test
    void parseLocation_acceptsFourAndSixFieldForms() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

        Location four = PrisonService.parseLocation("world,1,2,3");
        assertNotNull(four);
        assertEquals(0f, four.getYaw(), 1e-6);
        assertEquals(0f, four.getPitch(), 1e-6);

        Location six = PrisonService.parseLocation(" world , 1 , 2 , 3 , 90 , 45 ");
        assertNotNull(six);
        assertEquals(90f, six.getYaw(), 1e-6);
        assertEquals(45f, six.getPitch(), 1e-6);
    }

    @Test
    void serializeLocation_roundTripsThroughParse() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

        String raw = PrisonService.serializeLocation(new Location(world, 1.0, 64.0, -2.0, 90f, 10f));

        assertEquals("world,1.0,64.0,-2.0,90.0,10.0", raw);
        Location parsed = PrisonService.parseLocation(raw);
        assertNotNull(parsed);
        assertEquals(64.0, parsed.getY(), 1e-9);
    }

    @Test
    void playerName_fallsBackToUuidWhenUnresolved() {
        PrisonService noResolver = new PrisonService(gateway, plugin, () -> config, styles, notifier, null, i18n);
        when(gateway.isAvailable()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(null);
        when(gateway.imprison(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new PrisonLpGateway.ImprisonOutcome(true, "member")));

        noResolver.imprison(PLAYER_ID).join();

        org.mockito.ArgumentCaptor<java.util.Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(notifier).groupEvent(eq("prison_imprisoned"), captor.capture());
        assertEquals(PLAYER_ID.toString(), captor.getValue().get("player"));
    }

    // ===================== helpers =====================

    /** 在线玩家：scheduler.execute 立即同步执行任务（模拟已在该玩家 region 线程）。 */
    private Player mockPlayer(World world, boolean online, Location location) {
        Player player = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(online);
        when(player.getLocation()).thenReturn(location);
        runScheduler(scheduler);
        return player;
    }

    private void runScheduler(EntityScheduler scheduler) {
        doAnswer(inv -> {
                    ((Runnable) inv.getArgument(1)).run();
                    return true;
                })
                .when(scheduler)
                .execute(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
