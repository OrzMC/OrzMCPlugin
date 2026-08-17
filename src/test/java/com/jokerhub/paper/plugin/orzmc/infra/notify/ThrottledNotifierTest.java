package com.jokerhub.paper.plugin.orzmc.infra.notify;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThrottledNotifierTest {

    private ThrottledNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new ThrottledNotifier();
    }

    @Test
    void shouldRun_withFixedPeriod_throttlesWithinPeriod() {
        assertTrue(notifier.shouldRun("fixed-key", 60_000L), "首调应放行");
        assertFalse(notifier.shouldRun("fixed-key", 60_000L), "周期内应抑制");
    }

    @Test
    void shouldRun_withFixedPeriod_differentKeysIndependently() {
        assertTrue(notifier.shouldRun("fixed-key-a", 60_000L));
        assertTrue(notifier.shouldRun("fixed-key-b", 60_000L));
        assertFalse(notifier.shouldRun("fixed-key-a", 60_000L));
    }

    @Test
    void shouldRun_withFixedPeriod_zeroPeriod_alwaysRuns() {
        assertTrue(notifier.shouldRun("zero-key", 0L), "周期为 0 时每次放行");
        assertTrue(notifier.shouldRun("zero-key", 0L));
    }
}
