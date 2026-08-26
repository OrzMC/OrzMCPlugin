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
        assertNotNull(configService.getConfig("easybot"));
        assertNotNull(configService.getConfig("templates"));
        assertNotNull(configService.getConfig("portals"));
        assertNotNull(configService.getConfig("access_rules"));
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
    void manager_returnsNonNull() {
        assertNotNull(configService.manager());
    }

    @Test
    void setup_backfillsMissingRankColorsKeys() {
        // mock 插件无资源文件 → 磁盘配置为空，等价于旧版服务器升级（config.yml 缺新键）。
        // setup() 的 getOrSetDefault 需把 rank_colors 全键（含 tab_enabled）回填进磁盘配置，
        // 否则 /orzmc config get 显示 <null>（行为虽默认 false，但 UI 与 7 个兄弟键不一致）。
        configService.setup();
        org.bukkit.configuration.file.FileConfiguration cfg = configService.getConfig("config");
        for (String path : java.util.List.of(
                "rank_colors.enabled",
                "rank_colors.nametag_enabled",
                "rank_colors.tab_enabled",
                "rank_colors.op_color",
                "rank_colors.colors.admin",
                "rank_colors.colors.builder",
                "rank_colors.colors.member",
                "rank_colors.colors.default")) {
            assertTrue(cfg.contains(path), "缺键未回填: " + path);
        }
        assertFalse(cfg.getBoolean("rank_colors.tab_enabled"));
    }
}
