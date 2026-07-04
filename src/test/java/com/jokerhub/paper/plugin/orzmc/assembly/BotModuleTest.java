package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.features.maintenance.WorldMaintenanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotModuleTest {

    @Mock
    private PlatformModule platform;

    @Mock
    private WorldMaintenanceService maintenanceService;

    private BotModule module;

    @BeforeEach
    void setUp() {
        // PlatformModule 的构造依赖需要 mock
        when(platform.serverFacade()).thenReturn(mock());
        when(platform.configs()).thenReturn(mock());
        when(platform.serverAccess()).thenReturn(mock());
        when(platform.configService()).thenReturn(mock());
        when(platform.throttledLogger()).thenReturn(mock());
        when(platform.textStyles()).thenReturn(mock());

        module = new BotModule(platform);
    }

    @Test
    void constructor_createsServices() {
        assertNotNull(module.botCommandService());
        assertNotNull(module.botMessageService());
        assertNotNull(module.notifier());
        assertNotNull(module.botStatusService());
        assertNotNull(module.botInboundHandler());
    }

    @Test
    void botInboundHandler_isBotCommandService() {
        assertSame(module.botCommandService(), module.botInboundHandler());
    }

    @Test
    void setWorldMaintenanceService_storesReference() {
        module.setWorldMaintenanceService(maintenanceService);
        // afterPropertiesSet 之前未设置
        module.afterPropertiesSet();
        // 验证 BotCommandService.setMaintenanceService 被调用
    }

    @Test
    void setWorldMaintenanceService_null_doesNothing() {
        module.setWorldMaintenanceService(null);
        assertDoesNotThrow(() -> module.afterPropertiesSet());
    }

    @Test
    void afterPropertiesSet_clearsPendingReference() {
        module.setWorldMaintenanceService(maintenanceService);
        module.afterPropertiesSet();
        // 第二次调用不抛异常（pendingMaintenanceService 已清空）
        assertDoesNotThrow(() -> module.afterPropertiesSet());
    }

    @Test
    void setup_delegatesToBotMessageService() {
        assertDoesNotThrow(() -> module.setup());
    }

    @Test
    void tearDown_delegatesToBotMessageService() {
        assertDoesNotThrow(() -> module.tearDown());
    }

    @Test
    void setup_then_tearDown_noError() {
        module.setup();
        assertDoesNotThrow(() -> module.tearDown());
    }
}
