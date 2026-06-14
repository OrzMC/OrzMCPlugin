package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.core.OrzConstants;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class TeleportBowServiceTest {

    private ServerFacade serverFacade;
    private OrzTextStyles styles;
    private TeleportBowService service;

    private NamespacedKey tpBowKey;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        serverFacade = mock(ServerFacade.class);
        styles = mock(OrzTextStyles.class);
        tpBowKey = mock(NamespacedKey.class);

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
        ItemStack prevItem = new ItemStack(Material.AIR);

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

        service.markArrow(event);

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

        when(event.getBow()).thenReturn(bow);
        when(bow.getItemMeta()).thenReturn(bowMeta);
        when(bowMeta.getPersistentDataContainer()).thenReturn(bowPdc);
        when(bowPdc.has(tpBowKey, PersistentDataType.BYTE)).thenReturn(true);
        when(event.getProjectile()).thenReturn(arrow);
        when(arrow.getPersistentDataContainer()).thenReturn(arrowPdc);

        service.markArrow(event);

        verify(arrowPdc).set(tpBowKey, PersistentDataType.BYTE, (byte) 1);
    }

    @Test
    void handleArrowHit_arrowInWater_sendsWaterMessage() {
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        when(arrow.isInWater()).thenReturn(true);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(captor.getValue()).contains("水"));
    }

    @Test
    void handleArrowHit_arrowInLava_sendsLavaMessage() {
        Arrow arrow = mock(Arrow.class);
        Player player = mock(Player.class);
        when(arrow.isInLava()).thenReturn(true);

        service.handleArrowHit(arrow, player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.captor();
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(captor.getValue()).contains("岩浆"));
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
        assertTrue(PlainTextComponentSerializer.plainText().serialize(captor.getValue()).contains("跨世界"));
    }
}
