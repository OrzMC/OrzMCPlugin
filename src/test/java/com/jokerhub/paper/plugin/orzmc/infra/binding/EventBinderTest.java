package com.jokerhub.paper.plugin.orzmc.infra.binding;

import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventBinderTest {

    private JavaPlugin plugin;
    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
    }

    @Test
    void bind_registersAllListeners() {
        Listener listener1 = mock(Listener.class);
        Listener listener2 = mock(Listener.class);

        EventBinder.bind(plugin, List.of(listener1, listener2));

        verify(pluginManager).registerEvents(listener1, plugin);
        verify(pluginManager).registerEvents(listener2, plugin);
    }

    @Test
    void bind_emptyList_doesNothing() {
        EventBinder.bind(plugin, List.of());
        verify(pluginManager, never()).registerEvents(any(), any());
    }
}
