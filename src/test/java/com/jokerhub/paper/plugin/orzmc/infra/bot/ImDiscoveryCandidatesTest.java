package com.jokerhub.paper.plugin.orzmc.infra.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ImDiscoveryCandidates#bindCommands} 与 {@link ImDiscoveryCandidates.Candidate} 反解测试：
 * D11 候选 → 直接可复制执行的完整 bind 命令（UX 改进：管理员无需自行拼接平台/会话类型/会话 id）。
 */
class ImDiscoveryCandidatesTest {

    @Test
    void bindCommands_groupCandidate_suggestsAdminAndPlayerGroup() {
        List<String> cmds = ImDiscoveryCandidates.bindCommands("qq:group:F73A3B0AE04A8E82B75039A1519AE8EB");

        assertEquals(2, cmds.size(), "群会话应给出 admin_group/player_group 两条建议");
        assertEquals("/config im bind qq group F73A3B0AE04A8E82B75039A1519AE8EB admin_group", cmds.get(0));
        assertEquals("/config im bind qq group F73A3B0AE04A8E82B75039A1519AE8EB player_group", cmds.get(1));
    }

    @Test
    void bindCommands_userCandidate_suggestsAdminDm() {
        List<String> cmds = ImDiscoveryCandidates.bindCommands("feishu:user:oc_3f0ad3c5a62e40f8ec77a010d9b5d1e7");

        assertEquals(1, cmds.size(), "私聊会话应给 admin_dm 一条建议");
        assertEquals("/config im bind feishu user oc_3f0ad3c5a62e40f8ec77a010d9b5d1e7 admin_dm", cmds.get(0));
    }

    @Test
    void bindCommands_unparseableTarget_returnsEmpty() {
        assertTrue(ImDiscoveryCandidates.bindCommands(null).isEmpty());
        assertTrue(ImDiscoveryCandidates.bindCommands("qq").isEmpty(), "无冒号不可解析");
        assertTrue(ImDiscoveryCandidates.bindCommands("qq:").isEmpty(), "缺会话 id 不可解析");
        assertTrue(ImDiscoveryCandidates.bindCommands("qq:group:").isEmpty(), "空会话 id 不可解析");
        assertTrue(ImDiscoveryCandidates.bindCommands(":group:g1").isEmpty(), "空平台不可解析");
        assertTrue(ImDiscoveryCandidates.bindCommands("qq:voice:V-1").isEmpty(), "未知会话类型无建议");
    }

    @Test
    void bindCommands_commandsAreCopyExecutable() {
        // 命令行纯净：不含换行/注释/多余空格（整行复制到控制台即可执行，无尾随 token）
        for (String cmd : ImDiscoveryCandidates.bindCommands("qq:user:U-1")) {
            assertTrue(cmd.startsWith("/config im bind "), cmd);
            assertEquals(cmd, cmd.strip(), "命令行不应有首尾空白（防复制带入换行/空格）");
            assertTrue(!cmd.contains("\n") && !cmd.contains("#"), "命令行不应含注释或换行");
        }
    }

    // ---- Candidate 反解 ----

    @Test
    void candidate_chatTypeAndId_parsedFromTarget() {
        var c = new ImDiscoveryCandidates.Candidate("qq:group:F73A", 1L);

        assertEquals("group", c.chatType());
        assertEquals("F73A", c.chatId());
    }

    @Test
    void candidate_userTarget_parsed() {
        var c = new ImDiscoveryCandidates.Candidate("feishu:user:oc_abc", 1L);

        assertEquals("user", c.chatType());
        assertEquals("oc_abc", c.chatId());
    }

    @Test
    void candidate_malformed_returnsNull() {
        assertEquals(null, new ImDiscoveryCandidates.Candidate("qq", 1L).chatType());
        assertEquals(null, new ImDiscoveryCandidates.Candidate("qq:", 1L).chatId());
    }
}
