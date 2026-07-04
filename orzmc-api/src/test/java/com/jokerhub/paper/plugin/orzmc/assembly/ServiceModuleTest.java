package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ServiceModuleTest {

    @Test
    void defaultSetup_doesNotThrow() {
        ServiceModule module = new ServiceModule() {};
        assertDoesNotThrow(module::setup);
    }

    @Test
    void defaultTearDown_doesNotThrow() {
        ServiceModule module = new ServiceModule() {};
        assertDoesNotThrow(module::tearDown);
    }

    @Test
    void customImplementation_bothMethodsCalled() {
        AtomicBoolean setupCalled = new AtomicBoolean(false);
        AtomicBoolean tearDownCalled = new AtomicBoolean(false);

        ServiceModule module = new ServiceModule() {
            @Override
            public void setup() {
                setupCalled.set(true);
            }

            @Override
            public void tearDown() {
                tearDownCalled.set(true);
            }
        };

        module.setup();
        assertTrue(setupCalled.get());

        module.tearDown();
        assertTrue(tearDownCalled.get());
    }

    @Test
    void customImplementation_onlySetup() {
        AtomicBoolean setupCalled = new AtomicBoolean(false);

        ServiceModule module = new ServiceModule() {
            @Override
            public void setup() {
                setupCalled.set(true);
            }
            // tearDown 使用默认空实现
        };

        module.setup();
        assertTrue(setupCalled.get());

        assertDoesNotThrow(module::tearDown);
    }
}
