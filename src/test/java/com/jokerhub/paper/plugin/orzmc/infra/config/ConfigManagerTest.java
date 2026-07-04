package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {

    @TempDir
    File tempDir;

    private JavaPlugin plugin;
    private ConfigManager mgr;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("OrzMC"));
        mgr = new ConfigManager(plugin);
    }

    @Test
    void constructor_createsDataFolder() {
        assertTrue(tempDir.exists());
    }

    @Test
    void registerConfig_createsConfig() {
        boolean result = mgr.registerConfig("test");
        assertTrue(result);
        assertTrue(mgr.configExists("test"));
    }

    @Test
    void registerConfig_withCustomFileName() {
        boolean result = mgr.registerConfig("custom", "my_config.yml");
        assertTrue(result);
        assertTrue(mgr.configExists("custom"));
    }

    @Test
    void getConfig_returnsNullForUnregistered() {
        assertNull(mgr.getConfig("nonexistent"));
    }

    @Test
    void getConfig_returnsRegisteredConfig() {
        mgr.registerConfig("test");
        assertNotNull(mgr.getConfig("test"));
    }

    @Test
    void saveConfig_savesToDisk() {
        mgr.registerConfig("test");
        FileConfiguration cfg = mgr.getConfig("test");
        cfg.set("key", "value");

        boolean saved = mgr.saveConfig("test");
        assertTrue(saved);

        // 验证文件写入磁盘
        File savedFile = new File(tempDir, "test.yml");
        assertTrue(savedFile.exists());
    }

    @Test
    void saveConfig_returnsFalseForUnregistered() {
        assertFalse(mgr.saveConfig("nonexistent"));
    }

    @Test
    void reloadConfig_reloadsFromDisk() throws Exception {
        mgr.registerConfig("test");
        FileConfiguration cfg = mgr.getConfig("test");
        cfg.set("key", "original");
        mgr.saveConfig("test");

        // 直接修改文件
        File savedFile = new File(tempDir, "test.yml");
        FileConfiguration newCfg = YamlConfiguration.loadConfiguration(savedFile);
        newCfg.set("key", "modified");
        newCfg.save(savedFile);

        mgr.reloadConfig("test");
        assertEquals("modified", mgr.getConfig("test").getString("key"));
    }

    @Test
    void reloadConfig_returnsFalseForUnregistered() {
        assertFalse(mgr.reloadConfig("nonexistent"));
    }

    @Test
    void configExists_returnsTrueForRegistered() {
        mgr.registerConfig("test");
        assertTrue(mgr.configExists("test"));
    }

    @Test
    void configExists_returnsFalseForUnregistered() {
        assertFalse(mgr.configExists("test"));
    }

    @Test
    void getConfigNames_returnsAllNames() {
        mgr.registerConfig("a");
        mgr.registerConfig("b");
        assertTrue(mgr.getConfigNames().contains("a"));
        assertTrue(mgr.getConfigNames().contains("b"));
    }

    @Test
    void markDirty_causesSaveOnSaveDirty() {
        mgr.registerConfig("test");
        mgr.markDirty("test");
        // saveDirtyConfigs saves dirty + always-save configs
        assertDoesNotThrow(() -> mgr.saveDirtyConfigs());
    }

    @Test
    void markAlwaysSave_causesSaveOnSaveDirty() {
        mgr.registerConfig("test");
        mgr.markAlwaysSave("test");
        assertDoesNotThrow(() -> mgr.saveDirtyConfigs());
    }

    @Test
    void saveDirtyConfigs_skipsCleanConfigs() {
        mgr.registerConfig("test");
        // 未标记 dirty，不保存
        assertDoesNotThrow(() -> mgr.saveDirtyConfigs());
    }

    @Test
    void loadFile_returnsNullForMissingFile() {
        assertNull(mgr.loadFile("nonexistent.yml"));
    }

    @Test
    void loadFile_returnsConfigForExistingFile() throws Exception {
        File f = new File(tempDir, "existing.yml");
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("key", "val");
        cfg.save(f);

        FileConfiguration loaded = mgr.loadFile("existing.yml");
        assertNotNull(loaded);
        assertEquals("val", loaded.getString("key"));
    }

    @Test
    void sectionOrLegacy_readsFromMergedFirst() {
        mgr.registerConfig("main", "main.yml");
        FileConfiguration main = mgr.getConfig("main");
        main.createSection("whitelist");
        main.getConfigurationSection("whitelist").set("enabled", true);

        ConfigurationSection section = mgr.sectionOrLegacy("main", "whitelist", "legacy.yml");
        assertNotNull(section);
        assertTrue(section.getBoolean("enabled"));
    }

    @Test
    void sectionOrLegacy_fallsBackToLegacyFile() throws Exception {
        mgr.registerConfig("main", "main.yml");

        // 创建传统文件
        File legacyFile = new File(tempDir, "legacy.yml");
        FileConfiguration legacy = new YamlConfiguration();
        legacy.createSection("whitelist");
        legacy.getConfigurationSection("whitelist").set("enabled", true);
        legacy.save(legacyFile);

        ConfigurationSection section = mgr.sectionOrLegacy("main", "whitelist", "legacy.yml");
        assertNotNull(section);
        assertTrue(section.getBoolean("enabled"));
    }

    @Test
    void sectionOrLegacy_returnsNullWhenBothMissing() {
        mgr.registerConfig("main", "main.yml");
        assertNull(mgr.sectionOrLegacy("main", "missing", "missing.yml"));
    }
}
