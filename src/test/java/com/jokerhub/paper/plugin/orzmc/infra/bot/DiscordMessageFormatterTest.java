package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.*;

import com.jokerhub.paper.plugin.orzmc.core.bot.MessageEnvelope;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordMessageFormatterTest {

    private DiscordMessageFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new DiscordMessageFormatter();
    }

    @Test
    void format_defaultFormat_wrapsInCodeBlock() {
        List<String> result = formatter.format("hello", MessageEnvelope.Format.DEFAULT);
        assertEquals(1, result.size());
        assertTrue(result.get(0).startsWith("```\n"));
        assertTrue(result.get(0).endsWith("```"));
        assertEquals("```\nhello```", result.get(0));
    }

    @Test
    void format_codeBlockFormat_wrapsInCodeBlock() {
        List<String> result = formatter.format("test", MessageEnvelope.Format.CODE_BLOCK);
        assertEquals(1, result.size());
        assertEquals("```\ntest```", result.get(0));
    }

    @Test
    void format_plainFormat_returnsPlainText() {
        List<String> result = formatter.format("plain text", MessageEnvelope.Format.PLAIN);
        assertEquals(List.of("plain text"), result);
    }

    @Test
    void format_plainFormat_doesNotWrap() {
        List<String> result = formatter.format("plain\nmultiline", MessageEnvelope.Format.PLAIN);
        // PLAIN format uses split without code block wrapping
        assertFalse(result.get(0).startsWith("```"));
        assertFalse(result.get(0).endsWith("```"));
    }

    @Test
    void format_defaultFormat_splitsLongMessage() {
        // Create a message that exceeds the DISCORD_TEXT_LIMIT (2000)
        // Code block overhead is 6 (```\n + ```), so effective limit is 1994
        String longLine = "A".repeat(3000);
        List<String> result = formatter.format(longLine, MessageEnvelope.Format.DEFAULT);

        // Should be split into multiple parts, each wrapped in code blocks
        assertTrue(result.size() > 1);
        for (String part : result) {
            assertTrue(part.startsWith("```\n"));
            assertTrue(part.endsWith("```"));
            // Each part (including code block markers) should be <= 2000
            assertTrue(part.length() <= 2000, "Part length: " + part.length());
        }
        // Combined content should contain all the original characters
        String combined = result.stream()
                .map(s -> s.substring(4, s.length() - 3)) // strip ```\n and ```
                .collect(Collectors.joining());
        assertEquals(3000, combined.length());
    }

    @Test
    void format_plainFormat_splitsLongMessage() {
        String longLine = "X".repeat(2500);
        List<String> result = formatter.format(longLine, MessageEnvelope.Format.PLAIN);

        assertTrue(result.size() > 1);
        for (String part : result) {
            assertFalse(part.startsWith("```"));
            assertTrue(part.length() <= 2000, "Part length: " + part.length());
        }
        String combined = String.join("", result);
        assertEquals(2500, combined.length());
    }

    @Test
    void format_codeBlockFormat_splitsLongMessage() {
        String longLine = "B".repeat(3000);
        List<String> result = formatter.format(longLine, MessageEnvelope.Format.CODE_BLOCK);

        assertTrue(result.size() > 1);
        for (String part : result) {
            assertTrue(part.startsWith("```\n"));
            assertTrue(part.endsWith("```"));
            assertTrue(part.length() <= 2000, "Part length: " + part.length());
        }
    }

    @Test
    void format_defaultFormat_splitsAtNewline() {
        // Create content with explicit newlines where split should happen
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            if (i > 0) sb.append("\n");
            sb.append("line").append(i);
        }
        String longContent = sb.toString();
        List<String> result = formatter.format(longContent, MessageEnvelope.Format.DEFAULT);

        assertTrue(result.size() > 1);
        // Each code-block-wrapped part should be within limit
        for (String part : result) {
            assertTrue(part.length() <= 2000, "Part length: " + part.length());
        }
    }

    @Test
    void format_defaultFormat_singleShortMessage() {
        List<String> result = formatter.format("short", MessageEnvelope.Format.DEFAULT);
        assertEquals(1, result.size());
        assertEquals("```\nshort```", result.get(0));
    }

    @Test
    void format_defaultFormat_nullMessage_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            formatter.format((String) null, MessageEnvelope.Format.DEFAULT);
        });
    }

    @Test
    void format_defaultFormat_emptyMessage() {
        List<String> result = formatter.format("", MessageEnvelope.Format.DEFAULT);
        assertEquals(1, result.size());
    }
}
