package com.jokerhub.paper.plugin.orzmc.infra.logging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThrottledLoggerTest {

    private ConfigService configService;
    private FileConfiguration botConfig;
    private Logger logger;
    private ThrottledLogger throttledLogger;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        botConfig = mock(FileConfiguration.class);
        logger = mock(Logger.class);
        when(configService.getConfig("bot")).thenReturn(botConfig);
        when(botConfig.getLong("log_throttle_ms")).thenReturn(50000L);
        throttledLogger = new ThrottledLogger(configService, logger);
    }

    @Test
    void error_firstCall_logs() {
        throttledLogger.error("key1", "error message");
        verify(logger).severe("error message");
    }

    @Test
    void error_secondCallWithinPeriod_isThrottled() {
        throttledLogger.error("throttled", "first");
        throttledLogger.error("throttled", "second");
        verify(logger, times(1)).severe(anyString());
    }

    @Test
    void error_differentKeys_notThrottled() {
        throttledLogger.error("key_a", "first");
        throttledLogger.error("key_b", "second");
        verify(logger, times(2)).severe(anyString());
    }

    @Test
    void info_firstCall_logs() {
        throttledLogger.info("infoKey", "info message");
        verify(logger).info("info message");
    }

    @Test
    void info_secondCallWithinPeriod_isThrottled() {
        throttledLogger.info("infoKey", "first");
        throttledLogger.info("infoKey", "second");
        verify(logger, times(1)).info(anyString());
    }

    @Test
    void warning_firstCall_logs() {
        throttledLogger.warning("warnKey", "warn message");
        verify(logger).warning("warn message");
    }

    @Test
    void warning_secondCallWithinPeriod_isThrottled() {
        throttledLogger.warning("warnKey", "first");
        throttledLogger.warning("warnKey", "second");
        verify(logger, times(1)).warning(anyString());
    }

    @Test
    void info_withCustomPeriod_zeroUsesDefault() {
        when(botConfig.getLong("log_throttle_ms")).thenReturn(0L);
        ThrottledLogger custom = new ThrottledLogger(configService, logger);
        custom.info("key", "msg", 0L);
        verify(logger).info("msg");
    }

    @Test
    void defaultPeriodMs_exception_fallsBack() {
        when(configService.getConfig("bot")).thenThrow(new RuntimeException("config error"));
        ThrottledLogger fallbackLogger = new ThrottledLogger(configService, logger);
        fallbackLogger.info("key", "fallback works");
        verify(logger).info("fallback works");
    }
}
