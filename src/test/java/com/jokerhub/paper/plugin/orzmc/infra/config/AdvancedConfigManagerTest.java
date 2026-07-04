package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvancedConfigManagerTest {

    @TempDir
    File tempDir;

    private JavaPlugin plugin;
    private AdvancedConfigManager mgr;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("OrzMC"));
        mgr = new AdvancedConfigManager(plugin);
        mgr.registerConfig("test", "test.yml");
    }

    @Test
    void getWrapper_createsWrapper() {
        ConfigWrapper wrapper = mgr.getWrapper("test");
        assertNotNull(wrapper);
    }

    @Test
    void getWrapper_withPrefix() {
        ConfigWrapper wrapper = mgr.getWrapper("test", "prefix");
        assertNotNull(wrapper);
    }

    @Test
    void getOrSetDefault_setsDefault_whenMissing() {
        String result = mgr.getOrSetDefault("test", "key", "defaultValue");
        assertEquals("defaultValue", result);
        assertTrue(mgr.getConfig("test").contains("key"));
    }

    @Test
    void getOrSetDefault_returnsExisting() {
        FileConfiguration cfg = mgr.getConfig("test");
        cfg.set("key", "existing");
        String result = mgr.getOrSetDefault("test", "key", "defaultValue");
        assertEquals("existing", result);
    }

    @Test
    void setDefaults_appliesConsumer() {
        mgr.setDefaults("test", cfg -> cfg.set("custom", "value"));
        assertEquals("value", mgr.getConfig("test").getString("custom"));
    }
}
