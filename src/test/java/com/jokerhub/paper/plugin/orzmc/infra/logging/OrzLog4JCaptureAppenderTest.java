package com.jokerhub.paper.plugin.orzmc.infra.logging;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;

class OrzLog4JCaptureAppenderTest {

    @Test
    void append_capturesLoggerOutput() {
        LogCaptureService service = new LogCaptureService(100);
        OrzLog4JCaptureAppender appender = new OrzLog4JCaptureAppender(service);
        // 独立 LoggerContext，不污染全局；root level 设 ALL，直接经 root 记录
        LoggerContext ctx = new LoggerContext("OrzCaptureTest");
        ctx.getRootLogger().setLevel(Level.ALL);
        appender.start();
        ctx.getRootLogger().addAppender(appender);
        try {
            ctx.getRootLogger().info("hello from log4j");
            ctx.getRootLogger().warn("warning line");

            long watermark = service.watermark();
            List<String> lines = service.drainSince(0);
            assertTrue(lines.contains("hello from log4j"), "info 行应被捕获");
            assertTrue(lines.contains("warning line"), "warn 行应被捕获");
            assertTrue(watermark >= 2, "水位应前进");
        } finally {
            ctx.getRootLogger().removeAppender(appender);
            appender.stop();
            ctx.stop();
        }
    }

    @Test
    void append_formattedMessageUsesPlaceholders() {
        LogCaptureService service = new LogCaptureService(100);
        OrzLog4JCaptureAppender appender = new OrzLog4JCaptureAppender(service);
        LoggerContext ctx = new LoggerContext("OrzCaptureTest2");
        ctx.getRootLogger().setLevel(Level.ALL);
        appender.start();
        ctx.getRootLogger().addAppender(appender);
        try {
            ctx.getRootLogger().info("players: {} / {}", 5, 20);

            List<String> lines = service.drainSince(0);
            assertTrue(lines.contains("players: 5 / 20"), "占位符应被格式化: " + lines);
        } finally {
            ctx.getRootLogger().removeAppender(appender);
            appender.stop();
            ctx.stop();
        }
    }

    @Test
    void append_multilineMessage_splitsPerLine() {
        LogCaptureService service = new LogCaptureService(100);
        OrzLog4JCaptureAppender appender = new OrzLog4JCaptureAppender(service);
        LoggerContext ctx = new LoggerContext("OrzCaptureTest3");
        ctx.getRootLogger().setLevel(Level.ALL);
        appender.start();
        ctx.getRootLogger().addAppender(appender);
        try {
            ctx.getRootLogger().info("line1\nline2");

            assertEquals(List.of("line1", "line2"), service.drainSince(0));
        } finally {
            ctx.getRootLogger().removeAppender(appender);
            appender.stop();
            ctx.stop();
        }
    }
}
