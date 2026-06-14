package com.jokerhub.paper.plugin.orzmc.features.menu;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MenuEventServiceTest {

    private OrzTextStyles styles;
    private Player player;
    private InventoryClickEvent event;
    private InventoryView view;
    private Inventory topInventory;

    private MenuEventService service;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        styles = mock(OrzTextStyles.class);
        player = mock(Player.class);
        event = mock(InventoryClickEvent.class);
        view = mock(InventoryView.class);
        topInventory = mock(Inventory.class);
        bukkitMock = mockStatic(Bukkit.class);
        when(styles.info(anyString())).thenReturn(Component.text("OrzMC Menu"));
        when(Bukkit.createInventory(any(OrzMenuHolder.class), any(InventoryType.class), any(Component.class)))
                .thenReturn(mock(Inventory.class));
        service = new MenuEventService(styles);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void handleClick_nonPlayerWhoClicked_ignores() {
        HumanEntity nonPlayer = mock(HumanEntity.class);
        when(event.getWhoClicked()).thenReturn(nonPlayer);

        service.handleClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void handleClick_nonChestInventory_ignores() {
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(topInventory.getType()).thenReturn(InventoryType.PLAYER);

        service.handleClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void handleClick_nonMenuHolder_ignores() {
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(topInventory.getType()).thenReturn(InventoryType.CHEST);
        when(topInventory.getHolder()).thenReturn(mock(InventoryHolder.class));

        service.handleClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void handleClick_menuHolder_cancelsAndProcesses() {
        OrzMenuHolder holder = new OrzMenuHolder();
        ItemStack clickedItem = new ItemStack(Material.STONE);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(topInventory.getType()).thenReturn(InventoryType.CHEST);
        when(topInventory.getHolder()).thenReturn(holder);
        when(event.getCurrentItem()).thenReturn(clickedItem);
        when(styles.info(anyString())).thenReturn(Component.text("功能开发中"));

        service.handleClick(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage(Component.text("功能开发中"));
    }
}
