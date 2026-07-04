package com.jokerhub.paper.plugin.orzmc.core.ports.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ServerLoggerTest {

    @Test
    void logger_returnsLoggerInstance() {
        ServerLogger serverLogger = () -> Logger.getLogger("test-logger");

        Logger logger = serverLogger.logger();
        assertNotNull(logger);
        assertEquals("test-logger", logger.getName());
    }

    @Test
    void logger_customImplementation() {
        Logger customLogger = Logger.getLogger("custom");

        ServerLogger serverLogger = () -> customLogger;

        assertSame(customLogger, serverLogger.logger());
    }

    @Test
    void logger_nullLogger_allowedByContract() {
        // 合约允许返回 null（虽然不推荐）
        ServerLogger serverLogger = () -> null;

        assertNull(serverLogger.logger());
    }
}
