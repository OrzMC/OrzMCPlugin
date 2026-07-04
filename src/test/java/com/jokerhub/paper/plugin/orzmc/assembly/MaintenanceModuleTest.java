package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceModuleTest {

    @Mock
    private PlatformModule platform;

    @Mock
    private BotModule botModule;

    private MaintenanceModule module;

    @BeforeEach
    void setUp() {
        module = new MaintenanceModule(platform, botModule);
    }

    @Test
    void constructor_createsWorldMaintenanceService() {
        assertNotNull(module.worldMaintenanceService());
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
