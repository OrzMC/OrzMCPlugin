package com.jokerhub.paper.plugin.orzmc.features.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

class OrzMenuHolderTest {

    @Test
    void getInventory_default_returnsNull() {
        OrzMenuHolder holder = new OrzMenuHolder();
        assertNull(holder.getInventory());
    }

    @Test
    void setInventory_roundtrip() {
        OrzMenuHolder holder = new OrzMenuHolder();
        Inventory inventory = mock(Inventory.class);
        holder.setInventory(inventory);
        assertSame(inventory, holder.getInventory());
    }
}
