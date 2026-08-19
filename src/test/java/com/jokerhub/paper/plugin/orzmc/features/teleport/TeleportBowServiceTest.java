package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.core.OrzConstants;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class TeleportBowServiceTest extends ServiceTestBase {

    private ServerFacade serverFacade;
    private OrzTextStyles styles;
    private TeleportBowService service;
    private JavaPlugin plugin;

    private NamespacedKey tpBowKey;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        serverFacade = mock(ServerFacade.class);
        styles = mock(OrzTextStyles.class);
        tpBowKey = mock(NamespacedKey.class);
        plugin = mock(JavaPlugin.class);
        when(serverFacade.plugin()).thenReturn(plugin);

        ItemFactory itemFactory = mock(ItemFactory.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer metaPdc = mock(PersistentDataContainer.class);
        when(itemMeta.getPersistentDataContainer()).thenReturn(metaPdc);
        when(itemFactory.getItemMeta(Material.BOW)).thenReturn(itemMeta);
        when(itemFactory.getItemMeta(Material.ARROW)).thenReturn(mock(ItemMeta.class));
        when(itemFactory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenReturn(itemMeta);

        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getItemFactory).thenReturn(itemFactory);

        when(serverFacade.key(OrzConstants.TPBOW_KEY)).thenReturn(tpBowKey);
        when(styles.success(anyString())).thenReturn(Component.text("成功"));
        when(styles.tpbowPrefix()).thenReturn(Component.text("[传送弓]"));
        when(styles.colorError()).thenReturn(NamedTextColor.RED);
        when(styles.colorSuccess()).thenReturn(NamedTextColor.GREEN);

        service = new TeleportBowService(serverFacade, styles);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void prefix_returnsTpBowName() {
        Component result = service.prefix();
        assertEquals(Component.text("传送弓"), result);
    }

    @Test
    void giveAndEquip_setsBowInMainHand() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack prevItem = mock(ItemStack.class);
        when(prevItem.getType()).thenReturn(Material.AIR);

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(prevItem);

        service.giveAndEquip(player);

        verify(inventory).setItemInMainHand(any(ItemStack.class));
        verify(player).sendMessage(Component.text("成功"));
    }

    @Test
    void giveAndEquip_withExistingItem_addsToInventory() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack prevItem = new ItemStack(Material.DIAMOND);

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(prevItem);

        service.giveAndEquip(player);

        verify(inventory).addItem(prevItem);
        verify(inventory).setItemInMainHand(any(ItemStack.class));
    }

    @Test
    void isTPBowArrow_nonArrowProjectile_returnsFalse() {
        Projectile proj = mock(Projectile.class);

        boolean result = service.isTPBowArrow(proj);

        assertFalse(result);
    }

    @Test
    void isTPBowArrow_arrowWithKey_returnsTrue() {
        Arrow arrow = mock(Arrow.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(arrow.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(tpBowKey, PersistentDataType.BYTE)).thenReturn(true);

        boolean result = service.isTPBowArrow(arrow);

        assertTrue(result);
    }

    @Test
    void isTPBowArrow_arrowWithoutKey_returnsFalse() {
        Arrow arrow = mock(Arrow.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(arrow.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(tpBowKey, PersistentDataType.BYTE)).thenReturn(false);

        boolean result = service.isTPBowArrow(arrow);

        assertFalse(result);
    }

    @Test
    void markArrow_bowWithoutKey_doesNothing() {
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        ItemStack bow = mock(ItemStack.class);
        ItemMeta bowMeta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(event.getBow()).thenReturn(bow);
        when(bow.getItemMeta()).thenReturn(bowMeta);
        when(bowMeta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(tpBowKey, PersistentDataType.BYTE)).thenReturn(false);

        Arrow marked = service.markArrow(event);

        assertNull(marked);
        verify(event, never()).getProjectile();
    }

    @Test
    void markArrow_bowWithKey_marksArrow() {
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        ItemStack bow = mock(ItemStack.class);
        ItemMeta bowMeta = mock(ItemMeta.class);
        PersistentDataContainer bowPdc = mock(PersistentDataContainer.class);
        Arrow arrow = mock(Arrow.class);
        PersistentDataContainer arrowPdc = mock(PersistentDataContainer.class);
        Player player = mock(Player.class);

        when(event.getBow()).thenReturn(bow);
        when(bow.getItemMeta()).thenReturn(bowMeta);
        when(bowMeta.getPersistentDataContainer()).thenReturn(bowPdc);
        when(bowPdc.has(tpBowKey, PersistentDataType.BYTE)).thenReturn(true);
        when(event.getProjectile()).thenReturn(arrow);
        when(arrow.getPersistentDataContainer()).thenReturn(arrowPdc);
        when(event.getEntity()).thenReturn(player);
        when(player.hasPermission(OrzConstants.PERM_TPBOW_USE)).thenReturn(true);

        Arrow marked = service.markArrow(event);

        assertSame(arrow, marked);
        verify(arrowPdc).set(tpBowKey, PersistentDataType.BYTE, (byte) 1);
    }

    @Test
    void markArrow_bowWithKey_noPermission_doesNotMark_andNotifies() {
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        ItemStack bow = mock(ItemStack.class);
        ItemMeta bowMeta = mock(ItemMeta.class);
        PersistentDataContainer bowPdc = mock(PersistentDataContainer.class);
        Player player = mock(Player.class);

        when(event.getBow()).thenReturn(bow);
        when(bow.getItemMeta()).thenReturn(bowMeta);
        when(bowMeta.getPersistentDataContainer()).thenReturn(bowPdc);
        when(bowPdc.has(tpBowKey, PersistentDataType.BYTE)).thenReturn(true);
        when(event.getEntity()).thenReturn(player);
        when(player.hasPermission(OrzConstants.PERM_TPBOW_USE)).thenReturn(false);

        Arrow marked = service.markArrow(event);

        assertNull(marked);
        verify(event, never()).getProjectile();
        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(captor.getValue())
                .contains("传送弓已被禁用"));
    }

    @Test
    void markArrow_bowWithoutKey_noPermission_doesNotNotify() {
        // 普通弓（无传送弓标记）+ 无权限 → 不提示（权限检查只针对传送弓）
        EntityShootBowEvent event = mock(EntityShootBowEvent.class);
        ItemStack bow = mock(ItemStack.class);
        ItemMeta bowMeta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(event.getBow()).thenReturn(bow);
        when(bow.getItemMeta()).thenReturn(bowMeta);
        when(bowMeta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(tpBowKey, PersistentDataType.BYTE)).thenReturn(false);

        Arrow marked = service.markArrow(event);

        assertNull(marked);
        verify(event, never()).getProjectile();
        verify(event, never()).getEntity(); // PDC 短路：不触达玩家分支
    }

    @Test
    void handleArrowHit_arrowInWater_sendsWaterMessage() {
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        when(arrow.isInWater()).thenReturn(true);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(captor.getValue())
                .contains("水"));
    }

    @Test
    void handleArrowHit_arrowInLava_sendsLavaMessage() {
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        when(arrow.isInLava()).thenReturn(true);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(captor.getValue())
                .contains("岩浆"));
    }

    @Test
    void handleArrowHit_crossWorld_sendsCrossWorldMessage() {
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        Location arrowLoc = mock(Location.class);
        World arrowWorld = mock(World.class);
        World playerWorld = mock(World.class);

        when(arrow.getLocation()).thenReturn(arrowLoc);
        when(arrowLoc.getWorld()).thenReturn(arrowWorld);
        when(player.getWorld()).thenReturn(playerWorld);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(captor.getValue())
                .contains("跨世界"));
    }

    @Test
    void teleportAndFeedback_teleportsAsync_andFeedbackInPlayerRegion() {
        Player player = mock(Player.class);
        Location safe = mock(Location.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));
        when(player.getScheduler()).thenReturn(entityScheduler);

        service.teleportAndFeedback(player, safe);

        // Folia 区域亲和：跨 region 传送必须用异步 API（而非同步 teleport）
        verify(player).teleportAsync(safe);
        // 完成回调把音效/提示投递到玩家 region 线程。
        // 注意：不执行回调体——Sound 枚举静态初始化依赖完整注册表，普通 JUnit 环境不可用。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(entityScheduler).run(eq(plugin), captor.capture(), any(Runnable.class));
    }

    // ===== 补测（2026-08-19，TeleportBowService 45.9% → 目标 70%+）=====

    @Test
    void handleArrowHit_outOfBounds_sendsHeightMessage() {
        // y 超出世界高度 → withinWorldBounds false → 「目标高度不合法」
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        Location arrowLoc = mock(Location.class);
        World w = mock(World.class);
        Vector dir = mock(Vector.class);

        when(arrow.isInWater()).thenReturn(false);
        when(arrow.isInLava()).thenReturn(false);
        when(arrow.getLocation()).thenReturn(arrowLoc);
        when(arrow.getVelocity()).thenReturn(dir);
        when(arrowLoc.getWorld()).thenReturn(w);
        when(arrowLoc.getBlockX()).thenReturn(10);
        when(arrowLoc.getBlockY()).thenReturn(400); // 远超世界高度
        when(arrowLoc.getBlockZ()).thenReturn(20);
        when(w.getMinHeight()).thenReturn(-64);
        when(w.getMaxHeight()).thenReturn(320);
        when(player.getWorld()).thenReturn(w);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(captor.getValue())
                .contains("高度不合法"));
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void handleArrowHit_noStandable_sendsNotStandableMessage() {
        // 所有候选方块均不可站立 → findNearestSafe null → 「目标位置不可站立」
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        Location arrowLoc = mock(Location.class);
        World w = mock(World.class);
        Vector dir = new Vector(0, 0, -1); // 真实 Vector（mock 的 clone() 返回 null）
        Block solid = mock(Block.class); // 脚/头/地面全实心 → 不可站立

        when(arrow.isInWater()).thenReturn(false);
        when(arrow.isInLava()).thenReturn(false);
        when(arrow.getLocation()).thenReturn(arrowLoc);
        when(arrow.getVelocity()).thenReturn(dir);
        when(arrowLoc.getWorld()).thenReturn(w);
        when(arrowLoc.getBlockX()).thenReturn(10);
        when(arrowLoc.getBlockY()).thenReturn(64);
        when(arrowLoc.getBlockZ()).thenReturn(20);
        when(w.getMinHeight()).thenReturn(-64);
        when(w.getMaxHeight()).thenReturn(320);
        when(w.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(solid);
        when(w.getBlockAt(any(Location.class))).thenReturn(solid); // 兼容 getBlockAt(Location) 重载
        when(solid.getType()).thenReturn(Material.STONE); // 非空气 → isStandable false
        when(solid.getRelative(anyInt(), anyInt(), anyInt())).thenReturn(solid);
        when(player.getWorld()).thenReturn(w);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(captor.getValue())
                .contains("不可站立"));
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void handleArrowHit_standableButDangerousGround_returnsNotStandable() {
        // 地面是危险方块（岩浆/火等）→ DANGEROUS 分支（不依赖 Material 注册表，可单测）
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        Location arrowLoc = mock(Location.class);
        World w = mock(World.class);
        Vector dir = new Vector(0, 0, -1);
        Block foot = mock(Block.class);
        Block head = mock(Block.class);
        Block lava = mock(Block.class);

        when(arrow.isInWater()).thenReturn(false);
        when(arrow.isInLava()).thenReturn(false);
        when(arrow.getLocation()).thenReturn(arrowLoc);
        when(arrow.getVelocity()).thenReturn(dir);
        when(arrowLoc.getWorld()).thenReturn(w);
        when(arrowLoc.getBlockX()).thenReturn(10);
        when(arrowLoc.getBlockY()).thenReturn(64);
        when(arrowLoc.getBlockZ()).thenReturn(20);
        when(w.getMinHeight()).thenReturn(-64);
        when(w.getMaxHeight()).thenReturn(320);
        when(w.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int x = inv.getArgument(0);
            int y = inv.getArgument(1);
            int z = inv.getArgument(2);
            if (x == 10 && y == 64 && z == 20) return foot;
            if (x == 10 && y == 65 && z == 20) return head;
            return lava;
        });
        when(w.getBlockAt(any(Location.class))).thenAnswer(inv -> {
            org.bukkit.Location l = inv.getArgument(0);
            if (l.getBlockX() == 10 && l.getBlockY() == 64 && l.getBlockZ() == 20) return foot;
            if (l.getBlockX() == 10 && l.getBlockY() == 65 && l.getBlockZ() == 20) return head;
            return lava;
        });
        when(foot.getType()).thenReturn(Material.AIR);
        when(foot.getRelative(0, 1, 0)).thenReturn(head);
        when(foot.getRelative(0, -1, 0)).thenReturn(lava);
        when(head.getType()).thenReturn(Material.AIR);
        when(lava.getType()).thenReturn(Material.LAVA); // DANGEROUS 列表命中
        when(lava.getRelative(anyInt(), anyInt(), anyInt())).thenReturn(lava);
        when(head.getRelative(anyInt(), anyInt(), anyInt())).thenReturn(lava);
        when(player.getWorld()).thenReturn(w);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(captor.getValue())
                .contains("不可站立"));
        verify(player, never()).teleportAsync(any());
    }
}
