package com.jokerhub.paper.plugin.orzmc.assembly;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InitializableTest {

    @Test
    void afterPropertiesSet_isCalled() {
        AtomicBoolean called = new AtomicBoolean(false);

        Initializable init = () -> called.set(true);

        init.afterPropertiesSet();
        assertTrue(called.get());
    }

    @Test
    void afterPropertiesSet_multipleCalls() {
        AtomicInteger counter = new AtomicInteger(0);

        Initializable init = () -> counter.incrementAndGet();

        init.afterPropertiesSet();
        init.afterPropertiesSet();
        init.afterPropertiesSet();

        assertEquals(3, counter.get());
    }
}
