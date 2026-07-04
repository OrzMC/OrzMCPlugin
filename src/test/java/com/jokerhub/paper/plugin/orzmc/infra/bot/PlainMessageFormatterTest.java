package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlainMessageFormatterTest {

    private final PlainMessageFormatter formatter = new PlainMessageFormatter();

    @Test
    void format_returnsMessageInList() {
        List<String> result = formatter.format("hello", MessageEnvelope.Format.DEFAULT);
        assertEquals(List.of("hello"), result);
    }

    @Test
    void format_ignoresFormatParameter() {
        List<String> defaultFormat = formatter.format("test", MessageEnvelope.Format.DEFAULT);
        List<String> codeBlock = formatter.format("test", MessageEnvelope.Format.CODE_BLOCK);
        List<String> plain = formatter.format("test", MessageEnvelope.Format.PLAIN);
        assertEquals(defaultFormat, codeBlock);
        assertEquals(defaultFormat, plain);
    }

    @Test
    void format_nullMessage_returnsEmptyList() {
        List<String> result = formatter.format(null, MessageEnvelope.Format.DEFAULT);
        assertTrue(result.isEmpty());
    }

    @Test
    void format_emptyMessage_returnsListWithEmpty() {
        List<String> result = formatter.format("", MessageEnvelope.Format.DEFAULT);
        assertEquals(List.of(""), result);
    }

    @Test
    void format_multiLineMessage() {
        String multiLine = "line1\nline2\nline3";
        List<String> result = formatter.format(multiLine, MessageEnvelope.Format.DEFAULT);
        assertEquals(List.of(multiLine), result);
    }
}
