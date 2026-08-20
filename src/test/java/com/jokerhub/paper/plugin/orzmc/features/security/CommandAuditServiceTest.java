package com.jokerhub.paper.plugin.orzmc.features.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandAuditServiceTest {

    @TempDir
    Path tmpDir;

    private static Logger logger() {
        return Logger.getLogger("test");
    }

    private static Path logFile(Path dir) {
        return dir.resolve(CommandAuditService.LOG_FILE_NAME);
    }

    @Test
    void append_recordsOneLinePerEntry() throws IOException {
        CommandAuditService svc =
                new CommandAuditService(() -> true, tmpDir, CommandAuditService.DEFAULT_MAX_BYTES, logger());
        svc.record(CommandAuditService.SOURCE_GAME, "steve", "/tp a 0 64 0", false);
        svc.record(CommandAuditService.SOURCE_GAME, "alice", "/op bob", true);
        svc.flush();

        List<String> lines = Files.readAllLines(logFile(tmpDir), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        // 格式：ISO 时间 | 来源 | 发送者 | 命令原文 | 结果
        String[] parts = lines.get(0).split(" \\| ");
        assertEquals(5, parts.length);
        assertEquals(CommandAuditService.SOURCE_GAME, parts[1]);
        assertEquals("steve", parts[2]);
        assertEquals("/tp a 0 64 0", parts[3]);
        assertEquals(CommandAuditService.RESULT_EXECUTED, parts[4]);

        String[] blockedParts = lines.get(1).split(" \\| ");
        assertEquals(CommandAuditService.RESULT_BLOCKED, blockedParts[4]);
        assertEquals("alice", blockedParts[2]);
    }

    @Test
    void commandWithNewline_sanitizedToOneLine() throws IOException {
        CommandAuditService svc =
                new CommandAuditService(() -> true, tmpDir, CommandAuditService.DEFAULT_MAX_BYTES, logger());
        svc.record(CommandAuditService.SOURCE_GAME, "steve", "say a\nb", false);
        svc.flush();

        List<String> lines = Files.readAllLines(logFile(tmpDir), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertFalse(lines.get(0).contains("\n"));
        assertTrue(lines.get(0).contains("say a b"));
    }

    @Test
    void overLimit_rotatesCurrentToBackup() throws IOException {
        // 上限设极小，第二条写入前即触发轮转
        CommandAuditService svc = new CommandAuditService(() -> true, tmpDir, 10, logger());
        svc.record(CommandAuditService.SOURCE_GAME, "a", "first", false);
        svc.record(CommandAuditService.SOURCE_GAME, "b", "second", false);
        svc.flush();

        // 当前文件只有最后一条；.1 备份含第一条
        List<String> current = Files.readAllLines(logFile(tmpDir), StandardCharsets.UTF_8);
        List<String> backup =
                Files.readAllLines(tmpDir.resolve(CommandAuditService.ROTATED_FILE_NAME), StandardCharsets.UTF_8);
        assertEquals(1, current.size());
        assertTrue(current.get(0).contains("second"));
        assertTrue(backup.get(0).contains("first"));
    }

    @Test
    void disabled_skipsWriting() throws IOException {
        CommandAuditService svc =
                new CommandAuditService(() -> false, tmpDir, CommandAuditService.DEFAULT_MAX_BYTES, logger());
        svc.record(CommandAuditService.SOURCE_GAME, "steve", "/op bob", true);
        assertFalse(Files.exists(logFile(tmpDir)));
    }

    @Test
    void disabledToggle_turnsAuditBackOn() throws IOException {
        CommandAuditService svc =
                new CommandAuditService(() -> false, tmpDir, CommandAuditService.DEFAULT_MAX_BYTES, logger());
        svc.record(CommandAuditService.SOURCE_GAME, "steve", "/op bob", true);
        assertFalse(Files.exists(logFile(tmpDir)));

        // 运行时切换开关（模拟 /config reload 后配置更新）→ 后续记录生效
        CommandAuditService on =
                new CommandAuditService(() -> true, tmpDir, CommandAuditService.DEFAULT_MAX_BYTES, logger());
        on.record(CommandAuditService.SOURCE_GAME, "steve", "/say hi", false);
        on.flush();
        assertTrue(Files.exists(logFile(tmpDir)));
        List<String> lines = Files.readAllLines(logFile(tmpDir), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("say hi"));
    }
}
