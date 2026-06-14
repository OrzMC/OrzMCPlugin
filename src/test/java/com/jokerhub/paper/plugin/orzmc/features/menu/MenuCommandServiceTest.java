package com.jokerhub.paper.plugin.orzmc.features.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MenuCommandServiceTest {

    private OrzTextStyles styles;
    private Player player;
    private Inventory inventory;

    private MenuCommandService service;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        styles = mock(OrzTextStyles.class);
        player = mock(Player.class);
        inventory = mock(Inventory.class);
        bukkitMock = mockStatic(Bukkit.class);
        when(styles.info(anyString())).thenReturn(Component.text("OrzMC Menu"));
        when(Bukkit.createInventory(any(OrzMenuHolder.class), any(InventoryType.class), any(Component.class)))
                .thenReturn(inventory);
        service = new MenuCommandService(styles);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void handle_returnsSuccess() {
        MenuCommandService.Result result = service.handle(player);

        assertInstanceOf(MenuCommandService.Result.Success.class, result);
        verify(player).openInventory(inventory);
    }
}
