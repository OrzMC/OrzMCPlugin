package com.jokerhub.paper.plugin.orzmc.infra.notify;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThrottledNotifierTest {

    private ConfigService configService;
    private FileConfiguration config;
    private ThrottledNotifier notifier;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        config = mock(FileConfiguration.class);
        lenient().when(configService.getConfig("config")).thenReturn(config);
        lenient().when(config.getConfigurationSection("tnt")).thenReturn(null);

        notifier = new ThrottledNotifier(configService);
    }

    @Test
    void shouldRun_returnsTrueOnFirstCall() {
        assertTrue(notifier.shouldRunDefault("test-key"));
    }

    @Test
    void shouldRun_returnsFalseWithinPeriod() {
        assertTrue(notifier.shouldRunDefault("test-key"));
        assertFalse(notifier.shouldRunDefault("test-key"));
    }

    @Test
    void shouldRun_differentKeysIndependently() {
        assertTrue(notifier.shouldRunDefault("key-a"));
        assertTrue(notifier.shouldRunDefault("key-b"));
        assertFalse(notifier.shouldRunDefault("key-a"));
        assertFalse(notifier.shouldRunDefault("key-b"));
    }

    @Test
    void runDefault_executesActionOnFirstCall() {
        AtomicInteger count = new AtomicInteger(0);
        notifier.runDefault("key", () -> count.incrementAndGet());
        assertEquals(1, count.get());
    }

    @Test
    void runDefault_skipsActionWithinPeriod() {
        AtomicInteger count = new AtomicInteger(0);
        notifier.runDefault("key", () -> count.incrementAndGet());
        notifier.runDefault("key", () -> count.incrementAndGet());
        assertEquals(1, count.get());
    }

    @Test
    void runDefault_withCustomTtlMs() {
        AtomicInteger count = new AtomicInteger(0);
        notifier.runDefault("key", 10L, () -> count.incrementAndGet());
        assertEquals(1, count.get());
    }

    @Test
    void defaultPeriodMs_fallsBackToDefault_whenConfigFails() {
        reset(config);
        lenient().when(configService.getConfig("config")).thenThrow(new RuntimeException("not ready"));
        assertTrue(notifier.shouldRunDefault("test-key"));
    }
}
