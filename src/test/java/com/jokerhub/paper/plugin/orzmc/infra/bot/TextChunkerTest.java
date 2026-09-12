package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 出站长文本分段单测：不超限原样、按字符/字节两种口径、自然边界优先、码点安全、段数上限截断。
 */
class TextChunkerTest {

    @Test
    void withinLimit_isSinglePartUnchanged() {
        TextChunker.Result r = TextChunker.byChars("短消息", 100, 5);
        assertEquals(List.of("短消息"), r.parts());
        assertFalse(r.truncated());
    }

    @Test
    void emptyOrNull_isEmpty() {
        assertTrue(TextChunker.byChars(null, 10, 5).parts().isEmpty());
        assertTrue(TextChunker.byChars("", 10, 5).parts().isEmpty());
    }

    @Test
    void byChars_splitsAtHardLimitWithoutLosingContent() {
        String text = "a".repeat(250);
        TextChunker.Result r = TextChunker.byChars(text, 100, 10);
        assertEquals(3, r.parts().size());
        assertFalse(r.truncated());
        assertEquals(text, String.join("", r.parts()), "分段拼接应还原原文（不丢字符）");
        r.parts().forEach(p -> assertTrue(p.length() <= 100, "每段应 ≤ 上限: " + p.length()));
    }

    @Test
    void byChars_prefersLineBoundary() {
        // 400 字符上限、每行 60 字符 → 应切在换行处，而不是硬切 60/120/... 之后的无意义位置
        String text = ("x".repeat(60) + "\n").repeat(5);
        TextChunker.Result r = TextChunker.byChars(text, 200, 10);
        assertTrue(r.parts().size() >= 2);
        for (String part : r.parts()) {
            assertTrue(part.endsWith("\n"), "应在换行边界收尾: " + part.replace("\n", "\\n"));
        }
    }

    @Test
    void byUtf8Bytes_countsBytesNotChars() {
        // 中文 1 字 = 3 字节：10 个汉字 = 30 字节 → 上限 30 应为单段，上限 29 应切分
        String zh = "中文消息内容很多很多";
        assertEquals(30, zh.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(List.of(zh), TextChunker.byUtf8Bytes(zh, 30, 5).parts());
        TextChunker.Result split = TextChunker.byUtf8Bytes(zh, 29, 5);
        assertTrue(split.parts().size() >= 2);
        split.parts()
                .forEach(p -> assertTrue(
                        p.getBytes(StandardCharsets.UTF_8).length <= 29,
                        "每段字节数应 ≤ 上限: " + p + " = " + p.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void byUtf8Bytes_neverSplitsMultiByteChar() {
        // 全是 3 字节汉字，上限 4 字节：每段最多放 1 个汉字（2 个 = 6 字节会超），且不得出现半个字符
        TextChunker.Result r = TextChunker.byUtf8Bytes("中文字符测试", 4, 100);
        assertFalse(r.parts().isEmpty());
        r.parts().forEach(p -> {
            assertTrue(p.getBytes(StandardCharsets.UTF_8).length <= 4, p);
            // 还原后必须是合法 UTF-8（无替换字符 U+FFFD）
            assertFalse(p.contains("\uFFFD"), "不得切碎多字节字符: " + p);
        });
    }

    @Test
    void byChars_neverSplitsSurrogatePair() {
        String emoji = "😀".repeat(5); // 每个码点 2 个 char
        TextChunker.Result r = TextChunker.byChars(emoji, 3, 10);
        r.parts().forEach(p -> {
            assertFalse(p.contains("\uFFFD"), p);
            assertEquals(0, p.length() % 2, "代理对不应被切开: " + p.length());
        });
        // 每段最多 3 个码点（含首段 cut 后可能为 3 或 2 个，取决于边界策略）
        r.parts().forEach(p -> assertTrue(p.codePointCount(0, p.length()) <= 3, p));
    }

    @Test
    void maxParts_reached_truncatesAndFlags() {
        String text = "y".repeat(1000);
        TextChunker.Result r = TextChunker.byChars(text, 100, 5);
        assertEquals(5, r.parts().size(), "最多段数受平台限制");
        assertTrue(r.truncated(), "超出段数应标记截断供调用方告警");
    }

    @Test
    void exactlyLimit_isNotSplit() {
        assertFalse(TextChunker.byChars("z".repeat(50), 50, 5).truncated());
        assertEquals(1, TextChunker.byChars("z".repeat(50), 50, 5).parts().size());
    }
}
