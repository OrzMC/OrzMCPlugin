package com.jokerhub.paper.plugin.orzmc.infra.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import java.io.File;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceTest {

    @TempDir
    File tempDir;

    private OrzMC plugin;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        plugin = mock(OrzMC.class);
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("OrzMC"));
        configService = new ConfigService(plugin);
    }

    @Test
    void constructor_createsService() {
        assertNotNull(configService);
    }

    @Test
    void setup_registersConfigs() {
        configService.setup();
        assertNotNull(configService.getConfig("config"));
        assertNotNull(configService.getConfig("bot"));
        assertNotNull(configService.getConfig("templates"));
        assertNotNull(configService.getConfig("portals"));
        assertNotNull(configService.getConfig("ip_blacklist"));
    }

    @Test
    void getConfig_returnsConfigByName() {
        configService.setup();
        assertNotNull(configService.getConfig("config"));
    }

    @Test
    void reloadConfig_returnsTrue_whenRegistered() {
        configService.setup();
        assertTrue(configService.reloadConfig("config"));
    }

    @Test
    void reloadConfig_returnsFalse_whenUnregistered() {
        assertFalse(configService.reloadConfig("nonexistent"));
    }

    @Test
    void reloadAll_doesNotThrow() {
        configService.setup();
        assertDoesNotThrow(() -> configService.reloadAll());
    }

    @Test
    void saveConfig_returnsTrue_whenRegistered() {
        configService.setup();
        assertTrue(configService.saveConfig("config"));
    }

    @Test
    void saveConfig_returnsFalse_whenUnregistered() {
        assertFalse(configService.saveConfig("unknown"));
    }

    @Test
    void tearDown_doesNotThrow() {
        configService.setup();
        assertDoesNotThrow(() -> configService.tearDown());
    }

    @Test
    void loadFile_returnsNullForMissing() {
        assertNull(configService.loadFile("nonexistent.yml"));
    }

    @Test
    void sectionOrLegacy_returnsNullForMissing() {
        configService.setup();
        assertNull(configService.sectionOrLegacy("config", "missing", "missing.yml"));
    }

    @Test
    void manager_returnsNonNull() {
        assertNotNull(configService.manager());
    }
}
