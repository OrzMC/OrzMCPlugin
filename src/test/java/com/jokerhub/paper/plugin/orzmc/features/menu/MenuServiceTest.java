package com.jokerhub.paper.plugin.orzmc.features.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MenuServiceTest extends ServiceTestBase {

    private Player player;
    private Inventory inventory;
    private OrzTextStyles styles;

    private MenuService service;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        inventory = mock(Inventory.class);
        styles = mock(OrzTextStyles.class);
        bukkitMock = mockStatic(Bukkit.class);
        when(styles.info(anyString())).thenReturn(Component.text("OrzMC Menu"));
        when(Bukkit.createInventory(any(OrzMenuHolder.class), any(InventoryType.class), any(Component.class)))
                .thenReturn(inventory);
        service = new MenuService(styles);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void buildMenu_createsInventoryWithHolder() {
        Inventory result = service.buildMenu();

        assertSame(inventory, result);
        bukkitMock.verify(
                () -> Bukkit.createInventory(any(OrzMenuHolder.class), eq(InventoryType.CHEST), any(Component.class)));
    }

    @Test
    void openMenu_opensInventoryForPlayer() {
        service.openMenu(player);

        verify(player).openInventory(inventory);
    }

    @Test
    void onClick_nullItem_doesNothing() {
        service.onClick(player, null);

        verify(player, never()).sendMessage((Component) any());
    }

    @Test
    void onClick_airItem_doesNothing() {
        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);

        service.onClick(player, air);

        verify(player, never()).sendMessage((Component) any());
    }

    @Test
    void onClick_nonAirItem_sendsDevMessage() {
        when(styles.info(anyString())).thenReturn(Component.text("功能开发中"));
        ItemStack item = new ItemStack(Material.STONE);

        service.onClick(player, item);

        verify(player).sendMessage(Component.text("功能开发中"));
    }
}
