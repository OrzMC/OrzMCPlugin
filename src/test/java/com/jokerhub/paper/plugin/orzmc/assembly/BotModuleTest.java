package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotModuleTest {

    @Mock
    private PlatformModule platform;

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
