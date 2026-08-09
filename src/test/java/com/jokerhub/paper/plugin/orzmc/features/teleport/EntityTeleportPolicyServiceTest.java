package com.jokerhub.paper.plugin.orzmc.features.teleport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.testutil.ServiceTestBase;
import java.util.List;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;

class EntityTeleportPolicyServiceTest extends ServiceTestBase {

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

    // ---- 2026-08-09 可配置化：默认不禁止（enabled=false 全放行）+ 白名单豁免 ----

    @Test
    void shouldCancel_disabled_neverCancels() {
        EntityTeleportPolicyService disabled = new EntityTeleportPolicyService(false, List.of());
        Entity villager = mock(Entity.class);
        when(villager.getType()).thenReturn(EntityType.VILLAGER);
        Entity zombie = mock(Entity.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        assertFalse(disabled.shouldCancel(villager));
        assertFalse(disabled.shouldCancel(zombie));
    }

    @Test
    void shouldCancel_enabledWithTypeWhitelist_exemptsListedType() {
        EntityTeleportPolicyService service = new EntityTeleportPolicyService(true, List.of("VILLAGER"));
        Entity villager = mock(Entity.class);
        when(villager.getType()).thenReturn(EntityType.VILLAGER);
        Entity zombie = mock(Entity.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        assertFalse(service.shouldCancel(villager));
        assertTrue(service.shouldCancel(zombie));
    }

    @Test
    void shouldCancel_enabledEmptyWhitelist_cancelsEverything() {
        EntityTeleportPolicyService service = new EntityTeleportPolicyService(true, List.of());
        Entity villager = mock(Entity.class);
        when(villager.getType()).thenReturn(EntityType.VILLAGER);
        assertTrue(service.shouldCancel(villager));
    }
}
