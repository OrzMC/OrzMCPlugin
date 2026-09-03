package com.jokerhub.paper.plugin.orzmc.infra.config.configs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoreConfigRecordsTest {

    @Nested
    class TemplatesFromTest {
        @Test
        void fromNull_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Templates.from(null));
        }

        @Test
        void fromSection_parsesValues() {
            ConfigurationSection cfg = mock(ConfigurationSection.class);
            when(cfg.getString(anyString(), anyString())).thenReturn("custom join");
            Templates t = Templates.from(cfg);
            assertEquals("custom join", t.playerJoin());
        }
    }

    @Nested
    class EntityTeleportConfigFromTest {
        @Test
        void fromNull_returnsDefaults() {
            // cfg 缺失/未加载 → enabled=false + 内置 16 项默认白名单
            EntityTeleportConfig c = EntityTeleportConfig.from(null);
            assertFalse(c.enabled());
            assertEquals(EntityTeleportConfig.DEFAULT_ENTITY_TELEPORT_WHITELIST, c.whitelist());
        }

        @Test
        void fromSection_parsesValues() {
            ConfigurationSection cfg = mock(ConfigurationSection.class);
            // EntityTeleportConfig.from() 调用 cfg.getBoolean("entity_teleport_enabled", false)
            when(cfg.getBoolean("entity_teleport_enabled", false)).thenReturn(true);
            when(cfg.getStringList("entity_teleport_whitelist")).thenReturn(List.of("VILLAGER"));
            EntityTeleportConfig c = EntityTeleportConfig.from(cfg);
            assertTrue(c.enabled());
            assertEquals(List.of("VILLAGER"), c.whitelist());
        }
    }

    @Nested
    class PortalsFromTest {
        @Test
        void fromNull_returnsEmpty() {
            Portals p = Portals.from(null);
            assertTrue(p.entries().isEmpty());
        }
    }

    @Nested
    class CommandPolicyTest {
        @Test
        void constructor_defaults() {
            CommandPolicy p = new CommandPolicy(0, false);
            assertEquals(0, p.cooldownSeconds());
            assertFalse(p.adminOnly());
        }

        @Test
        void constructor_withValues() {
            CommandPolicy p = new CommandPolicy(5, true);
            assertEquals(5, p.cooldownSeconds());
            assertTrue(p.adminOnly());
        }
    }

    @Nested
    class CommandPoliciesFromTest {
        @Test
        void fromNull_returnsEmpty() {
            CommandPolicies cp = CommandPolicies.from(null);
            assertTrue(cp.policies().isEmpty());
        }

        @Test
        void fromEmptySection_returnsEmpty() {
            ConfigurationSection cfg = mock(ConfigurationSection.class);
            when(cfg.getKeys(false)).thenReturn(java.util.Set.of());
            CommandPolicies cp = CommandPolicies.from(cfg);
            assertTrue(cp.policies().isEmpty());
        }
    }
}
