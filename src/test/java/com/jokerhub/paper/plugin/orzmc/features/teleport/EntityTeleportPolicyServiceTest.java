package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;

class EntityTeleportPolicyServiceTest {

    private final EntityTeleportPolicyService service = new EntityTeleportPolicyService();

    @Test
    void shouldCancel_regularEntity_returnsTrue() {
        Entity entity = mock(Entity.class);
        assertTrue(service.shouldCancel(entity));
    }

    @Test
    void shouldCancel_tameable_returnsFalse() {
        Entity entity = mock(Tameable.class);
        assertFalse(service.shouldCancel(entity));
    }

    @Test
    void shouldCancel_enderman_returnsFalse() {
        Entity entity = mock(Enderman.class);
        assertFalse(service.shouldCancel(entity));
    }

    @Test
    void shouldCancel_armorStand_returnsFalse() {
        Entity entity = mock(ArmorStand.class);
        assertFalse(service.shouldCancel(entity));
    }

    @Test
    void shouldCancel_shulker_returnsFalse() {
        Entity entity = mock(Shulker.class);
        assertFalse(service.shouldCancel(entity));
    }
}
