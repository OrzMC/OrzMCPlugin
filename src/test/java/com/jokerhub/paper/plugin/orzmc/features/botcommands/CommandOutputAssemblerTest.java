package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandOutputAssemblerTest {

    @Test
    void assemble_syncLinesOnly_joinsWithNewline() {
        String result = CommandOutputAssembler.assemble(List.of("a", "b"), List.of(), 30);
        assertEquals("a\nb", result);
    }

    @Test
    void assemble_logLinesAppendedAfterSyncLines() {
        String result = CommandOutputAssembler.assemble(List.of("sync1"), List.of("log1", "log2"), 30);
        assertEquals("sync1\nlog1\nlog2", result);
    }

    @Test
    void assemble_filtersIssuedServerCommandNoise() {
        String result = CommandOutputAssembler.assemble(
                List.of(), List.of("Rcon issued server command: /list", "real output"), 30);
        assertEquals("real output", result);
    }

    @Test
    void assemble_filtersPlayerChatLines() {
        String result = CommandOutputAssembler.assemble(List.of(), List.of("<Steve> 大家好", "real output"), 30);
        assertEquals("real output", result);
    }

    @Test
    void assemble_keepsCommandOutputWithInlineBracketAngle() {
        // 行内含 <xxx>（如 LP 占位提示）但不是 "<名字> 聊天" 结构，不应误杀
        String result = CommandOutputAssembler.assemble(List.of("> /luckperms user <user>"), List.of(), 30);
        assertEquals("> /luckperms user <user>", result);
    }

    @Test
    void assemble_nonPositiveMaxLines_clampedToOne() {
        String result = CommandOutputAssembler.assemble(List.of("a", "b"), List.of(), 0);
        assertEquals("a\n…（输出过长，已截断，共 2 行）", result);
    }

    @Test
    void assemble_filtersBlankLines() {
        String result = CommandOutputAssembler.assemble(List.of("a", " ", ""), List.of(), 30);
        assertEquals("a", result);
    }

    @Test
    void assemble_deduplicatesSyncAndLogOverlap() {
        String result = CommandOutputAssembler.assemble(List.of("same", "unique"), List.of("same", "extra"), 30);
        assertEquals("same\nunique\nextra", result);
    }

    @Test
    void assemble_trimsLines() {
        String result = CommandOutputAssembler.assemble(List.of("  padded  "), List.of(), 30);
        assertEquals("padded", result);
    }

    @Test
    void assemble_allNoise_returnsEmpty() {
        String result = CommandOutputAssembler.assemble(List.of("issued server command: /x"), List.of(), 30);
        assertEquals("", result);
    }

    @Test
    void assemble_nullInputs_returnsEmpty() {
        assertEquals("", CommandOutputAssembler.assemble(null, null, 30));
    }

    @Test
    void assemble_underMaxLines_returnsAll() {
        String result = CommandOutputAssembler.assemble(List.of("a", "b", "c"), List.of(), 5);
        assertEquals("a\nb\nc", result);
    }

    @Test
    void assemble_overMaxLines_truncatesWithNotice() {
        List<String> lines = List.of("1", "2", "3", "4", "5");
        String result = CommandOutputAssembler.assemble(lines, List.of(), 3);
        assertEquals("1\n2\n3\n…（输出过长，已截断，共 5 行）", result);
    }

    @Test
    void assemble_exactlyMaxLines_noTruncation() {
        List<String> lines = List.of("1", "2", "3");
        String result = CommandOutputAssembler.assemble(lines, List.of(), 3);
        assertEquals("1\n2\n3", result);
    }

    @Test
    void isNoise_issuedServerCommand_returnsTrue() {
        assertTrue(CommandOutputAssembler.isNoise("Bot issued server command: /say hi"));
        assertTrue(CommandOutputAssembler.isNoise("Rcon issued server command: /list"));
    }

    @Test
    void isNoise_regularOutput_returnsFalse() {
        assertFalse(CommandOutputAssembler.isNoise("There are 5 of a max of 20 players online"));
        assertFalse(CommandOutputAssembler.isNoise("玩家 x 已上线"));
        // 含关键词的任意文本都视为噪音（保守过滤，宁可漏过不可放过回显）
        assertTrue(CommandOutputAssembler.isNoise("issued server command"));
    }
}
