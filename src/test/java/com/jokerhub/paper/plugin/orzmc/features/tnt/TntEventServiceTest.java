package com.jokerhub.paper.plugin.orzmc.features.tnt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.Format;
import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope.TargetType;
import com.jokerhub.paper.plugin.orzmc.core.ports.config.TypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.core.ports.server.ServerScheduler;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TemplateOptions;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.TntConfig;
import com.jokerhub.paper.plugin.orzmc.infra.notify.Notifier;
import com.jokerhub.paper.plugin.orzmc.infra.player.PlayerDisplayNames;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.infra.templates.TemplateResolvers;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class TntEventServiceTest extends ServiceTestBase {

    private TypedConfigProvider configs;
    private OrzTextStyles styles;
    private Notifier notifier;
    private ServerScheduler scheduler;
    private TntEventService service;

    private MockedStatic<TemplateResolvers> templateResolversMock;
    private MockedStatic<PlayerDisplayNames> displayNamesMock;

    @BeforeEach
    void setUp() {
        configs = mock(TypedConfigProvider.class);
        styles = mock(OrzTextStyles.class);
        notifier = mock(Notifier.class);
        scheduler = mock(ServerScheduler.class);

        TntConfig tntConfig = new TntConfig(
                false, // enable = false (TNT globally disabled)
                true, // enableRespawnAnchor
                0, // placeCooldownSeconds (0 = no cooldown)
                3000L, // notifyAggregateMs (3s 聚合窗口 → 60 ticks)
                List.of(), // whitelistRegions (empty)
                List.of()); // exemptEntities

        when(configs.tnt()).thenReturn(tntConfig);
        when(styles.tntPrefix()).thenReturn(Component.text("[TNT]"));
        when(styles.explosionPrefix()).thenReturn(Component.text("[爆炸]"));
        when(styles.playerName(any())).thenReturn(Component.text("player"));
        when(styles.unknownLabel()).thenReturn(Component.text("unknown"));
        when(styles.coordComponent(anyString())).thenReturn(Component.text("(0,0,0)"));
        when(styles.coordString(any())).thenReturn("(0,0,0)");

        TemplateOptions templateOpts = mock(TemplateOptions.class);
        when(templateOpts.coordScale()).thenReturn(1.0);
        when(templateOpts.coordPrecision()).thenReturn(1);
        when(templateOpts.coordUnitLabel()).thenReturn("m");
        when(configs.templateOptions()).thenReturn(templateOpts);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.PUBLIC, "msg", Format.DEFAULT));

        templateResolversMock = mockStatic(TemplateResolvers.class);
        templateResolversMock
                .when(() -> TemplateResolvers.worldAlias(anyString(), anyString(), any()))
                .thenReturn("world");

        displayNamesMock = mockStatic(PlayerDisplayNames.class);

        service = new TntEventService(configs, styles, notifier, scheduler);
    }

    @AfterEach
    void tearDown() {
        templateResolversMock.close();
        displayNamesMock.close();
    }

    private World mockWorld() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }

    private Block mockBlock(Location loc) {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(loc);
        when(block.getType()).thenReturn(Material.TNT);
        return block;
    }

    // ---- onTNTPrime ----

    @Test
    void onTNTPrime_tntDisabled_notInWhitelist_cancels() {
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block block = mockBlock(loc);
        TNTPrimeEvent event = mock(TNTPrimeEvent.class);
        when(event.getBlock()).thenReturn(block);

        service.onTNTPrime(event);

        verify(event).setCancelled(true);
        // 纯聚合：通知推迟到窗口尾部（游戏内 server 提示同样聚合）
        verify(notifier, never()).server(any(Component.class));
        runTail();
        verify(notifier).server(any(Component.class));
    }

    @Test
    void onTNTPrime_tntEnabled_doesNotCancel() {
        // Recreate service with TNT enabled
        TntConfig tntConfig = new TntConfig(true, false, 0, 3000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.PUBLIC, "msg", Format.DEFAULT));
        service = new TntEventService(configs, styles, notifier, scheduler);

        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block block = mockBlock(loc);
        TNTPrimeEvent event = mock(TNTPrimeEvent.class);
        when(event.getBlock()).thenReturn(block);

        service.onTNTPrime(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    // ---- onPlaceBlock ----

    @Test
    void onPlaceBlock_placingTnt_noCooldown_notCancelled() {
        // TNT enabled with whitelist covering location
        TntConfig tntConfig = new TntConfig(true, false, 0, 3000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.PUBLIC, "msg", Format.DEFAULT));
        service = new TntEventService(configs, styles, notifier, scheduler);

        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block placedBlock = mockBlock(loc);
        Player player = mock(Player.class);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placedBlock);
        when(event.getPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Alice");
        displayNamesMock.when(() -> PlayerDisplayNames.format(player)).thenReturn("Alice");

        service.onPlaceBlock(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void onPlaceBlock_placingTnt_disabledAndNotInWhitelist_cancels() {
        TntConfig tntConfig = new TntConfig(false, false, 0, 3000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        when(configs.renderEvent(anyString(), anyMap()))
                .thenReturn(new MessageEnvelope(TargetType.PUBLIC, "msg", Format.DEFAULT));
        service = new TntEventService(configs, styles, notifier, scheduler);

        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block placedBlock = mockBlock(loc);
        Player player = mock(Player.class);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placedBlock);
        when(event.getPlayer()).thenReturn(player);

        service.onPlaceBlock(event);

        verify(event).setCancelled(true);
    }

    @Test
    void onPlaceBlock_placingRespawnAnchor_disabled_cancels() {
        TntConfig tntConfig = new TntConfig(false, false, 0, 3000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);
        service = new TntEventService(configs, styles, notifier, scheduler);

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.RESPAWN_ANCHOR);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);

        service.onPlaceBlock(event);

        verify(event).setCancelled(true);
    }

    // ---- onBlockPreDispense ----

    @Test
    void onBlockPreDispense_nonTntItem_doesNothing() {
        BlockPreDispenseEvent event = mock(BlockPreDispenseEvent.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        when(event.getItemStack()).thenReturn(item);

        service.onBlockPreDispense(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void onBlockPreDispense_tntItem_disabled_cancels() {
        BlockPreDispenseEvent event = mock(BlockPreDispenseEvent.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.TNT);
        when(event.getItemStack()).thenReturn(item);
        Block block = mock(Block.class);
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(loc);
        when(block.getType()).thenReturn(Material.TNT);
        when(event.getBlock()).thenReturn(block);

        service.onBlockPreDispense(event);

        verify(event).setCancelled(true);
    }

    // ---- onBlockExplode ----

    @Test
    void onBlockExplode_airBlock_doesNothing() {
        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(event.getBlock()).thenReturn(block);

        service.onBlockExplode(event);

        verifyNoInteractions(notifier);
        verifyNoInteractions(scheduler);
    }

    @Test
    void onBlockExplode_nonAirBlock_schedulesTailOnly() {
        BlockExplodeEvent event = blockExplodeAt(10, 64, 20, Material.STONE);

        service.onBlockExplode(event);

        // 纯聚合：不立即发送，仅调度窗口尾部冲刷（3000ms → 60 ticks）
        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler).runLater(any(Runnable.class), eq(60L));
    }

    // ---- onEntityExplode ----

    @Test
    void onEntityExplode_exemptEntity_doesNothing() {
        // Creeper is in the default exempt list
        TntConfig tntConfig = new TntConfig(false, true, 0, 3000L, List.of(), List.of("CREEPER"));
        when(configs.tnt()).thenReturn(tntConfig);
        service = new TntEventService(configs, styles, notifier, scheduler);

        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntityType()).thenReturn(EntityType.CREEPER);

        service.onEntityExplode(event);

        verifyNoInteractions(notifier);
        verifyNoInteractions(scheduler);
    }

    @Test
    void onEntityExplode_nonExemptEntity_schedulesTailOnly() {
        EntityExplodeEvent event = entityExplodeAt(10, 64, 20, EntityType.ENDERMAN);

        service.onEntityExplode(event);

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler).runLater(any(Runnable.class), eq(60L));
    }

    // ---- 热重载：不重建 service，配置变更后立即生效 ----

    @Test
    void onTNTPrime_configChangedAfterConstruction_takesEffectImmediately() {
        // setUp 已建 service（enable=false）。模拟 reload：provider 返回新配置，不重建 service。
        TntConfig tntConfig = new TntConfig(true, false, 0, 3000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);

        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        Block block = mockBlock(loc);
        TNTPrimeEvent event = mock(TNTPrimeEvent.class);
        when(event.getBlock()).thenReturn(block);

        service.onTNTPrime(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void onEntityExplode_exemptListChangedAfterConstruction_takesEffectImmediately() {
        // setUp 的 config 空 exempt → 默认豁免（含 CREEPER，不含 ENDERMAN）。
        // 模拟 reload：新配置把 ENDERMAN 加入豁免，不重建 service。
        TntConfig tntConfig = new TntConfig(false, true, 0, 3000L, List.of(), List.of("ENDERMAN"));
        when(configs.tnt()).thenReturn(tntConfig);

        EntityExplodeEvent event = entityExplodeAt(10, 64, 20, EntityType.ENDERMAN);

        service.onEntityExplode(event);

        verifyNoInteractions(notifier);
        verifyNoInteractions(scheduler);
    }

    // ---- 突发聚合：同区域同类型合并，窗口尾部冲刷补发 ×N ----

    @Test
    void aggregate_sameRegion_tailSendsCountedSingle() {
        service.onEntityExplode(entityExplodeAt(10, 64, 20, EntityType.TNT));
        service.onEntityExplode(entityExplodeAt(30, 64, 40, EntityType.TNT));
        service.onEntityExplode(entityExplodeAt(50, 64, 60, EntityType.TNT));

        // 纯聚合：事件不立即发送，仅调度一次尾部冲刷
        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler, times(1)).runLater(any(Runnable.class), anyLong());

        runTail();

        // 尾部统一冲刷为一条 "×3" 汇总（含首事件坐标），不再双条刷屏
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> vars = ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs, times(1)).renderEvent(eq("tnt_alert"), vars.capture());
        assertEquals("TNT爆炸 ×3", vars.getAllValues().get(0).get("msg"));
    }

    @Test
    void aggregate_singleEvent_tailSendsPlainSingle() {
        service.onEntityExplode(entityExplodeAt(10, 64, 20, EntityType.TNT));

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        runTail();

        // count=1 → 尾部统一发不带次数的单条（仅延迟一个窗口，不双条）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> vars = ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs, times(1)).renderEvent(eq("tnt_alert"), vars.capture());
        assertEquals("TNT爆炸", vars.getAllValues().get(0).get("msg"));
    }

    @Test
    void aggregate_differentRegions_areIndependent() {
        // 区域 0 两个事件 + 区域 1（z≥128）两个事件 → 各自独立聚合
        service.onEntityExplode(entityExplodeAt(10, 64, 20, EntityType.TNT)); // region 0
        service.onEntityExplode(entityExplodeAt(20, 64, 30, EntityType.TNT)); // region 0
        service.onEntityExplode(entityExplodeAt(10, 64, 200, EntityType.TNT)); // region 1
        service.onEntityExplode(entityExplodeAt(20, 64, 220, EntityType.TNT)); // region 1

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler, times(2)).runLater(any(Runnable.class), anyLong());
    }

    @Test
    void aggregate_differentMessageTypes_areIndependent() {
        // 同区域但消息类型不同（实体爆炸 vs 方块爆炸）分开聚合
        service.onEntityExplode(entityExplodeAt(10, 64, 20, EntityType.TNT));
        service.onBlockExplode(blockExplodeAt(30, 64, 40, Material.STONE));

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler, times(2)).runLater(any(Runnable.class), anyLong());
    }

    @Test
    void aggregate_blockExplode_multipleMaterials_mergedIntoSingleLabel() {
        // 大爆炸波及 STONE 与 DIRT：统一归并到 "方块爆炸"，不按材质拆分
        service.onBlockExplode(blockExplodeAt(10, 64, 20, Material.STONE));
        service.onBlockExplode(blockExplodeAt(30, 64, 40, Material.DIRT));

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        runTail();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> vars = ArgumentCaptor.forClass(java.util.Map.class);
        verify(configs, times(1)).renderEvent(eq("tnt_alert"), vars.capture());
        assertEquals("方块爆炸 ×2", vars.getAllValues().get(0).get("msg"));
    }

    @Test
    void aggregate_reloadAfterConstruction_usesNewWindowForTail() {
        // 配置热重载：新窗口 5000ms → 尾部冲刷延迟 100 ticks
        TntConfig tntConfig = new TntConfig(false, true, 0, 5000L, List.of(), List.of());
        when(configs.tnt()).thenReturn(tntConfig);

        service.onEntityExplode(entityExplodeAt(10, 64, 20, EntityType.TNT));

        verify(scheduler).runLater(any(Runnable.class), eq(100L));
    }

    @Test
    void aggregate_differentHeightLayers_areIndependent() {
        // 同 XZ 立柱但高度层不同（y=10 层 0 vs y=250 层 3）→ 分开聚合，告警坐标不串层
        service.onEntityExplode(entityExplodeAt(10, 10, 20, EntityType.TNT)); // ry=0
        service.onEntityExplode(entityExplodeAt(10, 250, 20, EntityType.TNT)); // ry=3

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler, times(2)).runLater(any(Runnable.class), anyLong());
    }

    @Test
    void aggregate_negativeCoordinates_floorDivRegionsSymmetric() {
        // floorDiv：x=-1 → 区域 -1，x=127 → 区域 0；边界恰在 128 整数倍，负坐标区域宽度均匀，不出现 256 宽区域
        service.onEntityExplode(entityExplodeAt(-1, 64, 20, EntityType.TNT)); // region -1
        service.onEntityExplode(entityExplodeAt(127, 64, 20, EntityType.TNT)); // region 0

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler, times(2)).runLater(any(Runnable.class), anyLong());
    }

    @Test
    void aggregate_renderFailure_tailDoesNotOrphanBatch() {
        // 用 doThrow 打桩：避免 when(mock).thenThrow() 后再次 when(mock) 重打桩时执行旧 throw 桩
        doThrow(new IllegalStateException("template broken")).when(configs).renderEvent(anyString(), anyMap());

        // 纯聚合：事件本身不再渲染（异常推迟到尾部冲刷），批次正常调度
        service.onEntityExplode(entityExplodeAt(10, 64, 20, EntityType.TNT));
        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));

        // 尾部冲刷渲染失败 → 异常冒出，批次已移除不留孤儿条目
        assertThrows(IllegalStateException.class, () -> runTail());

        // 恢复后同一 key 的事件应创建全新批次，不被孤儿条目永久静默
        doReturn(new MessageEnvelope(TargetType.PUBLIC, "msg", Format.DEFAULT))
                .when(configs)
                .renderEvent(anyString(), anyMap());
        service.onEntityExplode(entityExplodeAt(30, 64, 40, EntityType.TNT));

        verify(notifier, never()).event(eq("tnt_alert"), any(MessageEnvelope.class));
        verify(scheduler, times(2)).runLater(any(Runnable.class), anyLong());
    }

    /** 执行已捕获的尾部冲刷任务（模拟调度器在窗口到期后运行）。仅适用于恰好调度了一次的场景。 */
    private void runTail() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runLater(task.capture(), anyLong());
        task.getValue().run();
    }

    private EntityExplodeEvent entityExplodeAt(int x, int y, int z, EntityType type) {
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(x);
        when(loc.getBlockY()).thenReturn(y);
        when(loc.getBlockZ()).thenReturn(z);
        EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.getEntityType()).thenReturn(type);
        when(event.getLocation()).thenReturn(loc);
        return event;
    }

    private BlockExplodeEvent blockExplodeAt(int x, int y, int z, Material type) {
        Location loc = mock(Location.class);
        World world = mockWorld();
        when(loc.getWorld()).thenReturn(world);
        when(loc.getBlockX()).thenReturn(x);
        when(loc.getBlockY()).thenReturn(y);
        when(loc.getBlockZ()).thenReturn(z);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getLocation()).thenReturn(loc);
        BlockExplodeEvent event = mock(BlockExplodeEvent.class);
        when(event.getBlock()).thenReturn(block);
        return event;
    }
}
