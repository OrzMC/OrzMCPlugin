package com.jokerhub.paper.plugin.orzmc.infra.templates;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExceptionFormatterTest {

    @Test
    void nullReturnsUnknown() {
        assertEquals("unknown", ExceptionFormatter.summarize(null));
    }

    @Test
    void exceptionWithMessage() {
        Exception e = new RuntimeException("test error message");
        String result = ExceptionFormatter.summarize(e);
        assertTrue(result.startsWith("RuntimeException: test error message"));
        assertTrue(result.contains(" at "));
        assertTrue(result.contains("ExceptionFormatterTest"));
    }

    @Test
    void exceptionWithoutMessage() {
        Exception e = new RuntimeException();
        String result = ExceptionFormatter.summarize(e);
        assertTrue(result.startsWith("RuntimeException"));
        assertTrue(result.contains(" at "));
        assertTrue(result.contains("ExceptionFormatterTest"));
    }

    @Test
    void exceptionWithEmptyMessage() {
        Exception e = new RuntimeException("");
        String result = ExceptionFormatter.summarize(e);
        assertTrue(result.startsWith("RuntimeException"));
        assertTrue(result.contains(" at "));
    }
}
