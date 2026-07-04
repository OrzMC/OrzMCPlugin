package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.portal.PortalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PortalModuleTest {

    @Mock
    private PlatformModule platform;

    private AutoCloseable mocks;
    private PortalModule module;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // PortalService 构造函数仅存储引用，mock 返回值不会被调用
        lenient().when(platform.configService()).thenReturn(mock());
        lenient().when(platform.serverAccess()).thenReturn(mock());
        lenient().when(platform.serverFacade()).thenReturn(mock());
        lenient().when(platform.configs()).thenReturn(mock());

        module = new PortalModule(platform);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) mocks.close();
    }

    @Test
    void constructor_createsPortalService() {
        assertNotNull(module.portalService());
    }

    @Test
    void portalService_isPortalService() {
        assertInstanceOf(PortalService.class, module.portalService());
    }

    @Test
    void setup_doesNotThrow() {
        assertDoesNotThrow(() -> module.setup());
    }

    @Test
    void tearDown_doesNotThrow() {
        assertDoesNotThrow(() -> module.tearDown());
    }
}
