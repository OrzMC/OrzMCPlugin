package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.OrzMC;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import com.jokerhub.paper.plugin.orzmc.infra.config.DefaultTypedConfigProvider;
import com.jokerhub.paper.plugin.orzmc.infra.health.HealthRegistry;
import com.jokerhub.paper.plugin.orzmc.infra.logging.ThrottledLogger;
import com.jokerhub.paper.plugin.orzmc.infra.notify.ThrottledNotifier;
import com.jokerhub.paper.plugin.orzmc.infra.server.ServerFacade;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PlatformModuleTest {

    @TempDir
    File tempDir;

    @Mock
    private OrzMC plugin;

    private AutoCloseable mocks;
    private PlatformModule module;

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);

        // ConfigManager 需要 plugin.getDataFolder() 和 plugin.getLogger()
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("OrzMC"));

        // 预创建 bot.yml 和 guide_book.yml 等配置文件避免 NPE
        Files.createFile(tempDir.toPath().resolve("bot.yml"));
        Files.createFile(tempDir.toPath().resolve("guide_book.yml"));

        module = new PlatformModule(plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void constructor_createsAllServices() {
        assertNotNull(module.serverFacade());
        assertNotNull(module.configService());
        assertNotNull(module.configs());
        assertNotNull(module.textStyles());
        assertNotNull(module.throttledLogger());
        assertNotNull(module.throttledNotifier());
        assertNotNull(module.healthRegistry());
    }

    @Test
    void serverFacade_isAlso_serverAccess() {
        assertSame(module.serverFacade(), module.serverAccess());
    }

    @Test
    void serverFacade_isAlso_serverLogger() {
        assertSame(module.serverFacade(), module.serverLogger());
    }

    @Test
    void serverFacade_isAlso_serverScheduler() {
        assertSame(module.serverFacade(), module.serverScheduler());
    }

    @Test
    void configs_isDefaultTypedConfigProvider() {
        assertInstanceOf(DefaultTypedConfigProvider.class, module.configs());
    }

    @Test
    void serverFacade_isServerFacade() {
        assertInstanceOf(ServerFacade.class, module.serverFacade());
    }

    @Test
    void configService_isConfigService() {
        assertInstanceOf(ConfigService.class, module.configService());
    }

    @Test
    void textStyles_isOrzTextStyles() {
        assertInstanceOf(OrzTextStyles.class, module.textStyles());
    }

    @Test
    void throttledLogger_isThrottledLogger() {
        assertInstanceOf(ThrottledLogger.class, module.throttledLogger());
    }

    @Test
    void throttledNotifier_isThrottledNotifier() {
        assertInstanceOf(ThrottledNotifier.class, module.throttledNotifier());
    }

    @Test
    void healthRegistry_isHealthRegistry() {
        assertInstanceOf(HealthRegistry.class, module.healthRegistry());
    }

    @Test
    void setup_delegatesToConfigService() {
        module.setup();
        assertNotNull(module.configService().getConfig("config"));
    }

    @Test
    void tearDown_delegatesToConfigService() {
        module.setup();
        assertDoesNotThrow(() -> module.tearDown());
    }
}
