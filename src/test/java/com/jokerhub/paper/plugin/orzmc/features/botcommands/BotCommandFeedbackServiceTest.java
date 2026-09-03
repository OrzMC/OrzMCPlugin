package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotCommandFeedbackServiceTest {

    private BotCommandFeedbackService feedback;

    @BeforeEach
    void setUp() {
        feedback = new BotCommandFeedbackService();
    }

    @Test
    void helpInfo_containsAdminCommands() {
        String help = feedback.helpInfo("$");
        assertTrue(help.contains("$a"));
        assertTrue(help.contains("$r"));
        assertTrue(help.contains("$b"));
        assertTrue(help.contains("$o"));
    }

    @Test
    void helpInfo_containsUserCommands() {
        String help = feedback.helpInfo("$");
        assertTrue(help.contains("$l"));
        assertTrue(help.contains("$w"));
        assertTrue(help.contains("$h"));
    }

    @Test
    void helpInfo_usesCustomPromptChar() {
        String help = feedback.helpInfo("!");
        assertTrue(help.contains("!a"));
        assertTrue(help.contains("!l"));
    }

    @Test
    void helpInfo_usesEmojiSectionTitles_withoutBrackets() {
        String help = feedback.helpInfo("$");
        // emoji 分组标题，不用中文方括号强调（字面 emoji 与源码同码点，含 ZWJ 序列）
        assertTrue(help.contains("👨‍💼 管理员指令"));
        assertTrue(help.contains("👨🏻‍💻 通用指令"));
        assertFalse(help.contains("【管理员"));
        assertFalse(help.contains("【通用"));
        // 不带冒号尾缀
        assertFalse(help.contains("管理员指令："));
        assertFalse(help.contains("通用指令:"));
    }

    @Test
    void adminRequiredTip_forAdminCmd() {
        String tip = feedback.adminRequiredTip(OrzUserCmd.BACKUP, "$");
        assertTrue(tip.contains("需要管理员权限"));
    }

    @Test
    void adminRequiredTip_forNonAdminCmd() {
        String tip = feedback.adminRequiredTip(OrzUserCmd.SHOW_PLAYERS, "$");
        assertEquals("", tip);
    }

    @Test
    void usageTip_forWhitelistCommands() {
        String tip = feedback.usageTip(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, "$");
        assertTrue(tip.contains("$a"));
        assertTrue(tip.contains("<玩家>"));
    }

    @Test
    void usageTip_forConsoleCommand() {
        String tip = feedback.usageTip(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, "$");
        assertTrue(tip.contains("$e"));
        assertTrue(tip.contains("<控制台命令>"));
    }

    @Test
    void usageTip_forEveryCommand_usesUnifiedTemplate() {
        // 所有命令统一三段式模板：🎯 标题 + 📚 用法 + 🚀 示例，且不含中文方括号
        // 双前缀（$ 与 !）都断言，防止单命令 usageTip 回归为硬编码 "$x"（M3 审查项）
        for (OrzUserCmd cmd : OrzUserCmd.values()) {
            for (String promptChar : new String[] {"$", "!"}) {
                String tip = feedback.usageTip(cmd, promptChar);
                assertNotNull(tip, cmd + " 的 usageTip 不应为 null");
                assertFalse(tip.isBlank(), cmd + " 的 usageTip 不应为空");
                assertTrue(tip.contains("🎯 " + promptChar + cmd.cmdName()), cmd + " 应包含 🎯 标题");
                assertTrue(tip.contains("📚 用法："), cmd + " 应包含用法节");
                assertTrue(tip.contains("🚀 示例："), cmd + " 应包含示例节");
                assertFalse(tip.contains("【"), cmd + " 不应使用中文方括号");
                assertFalse(tip.contains("】"), cmd + " 不应使用中文方括号");
            }
        }
    }

    @Test
    void usageTip_examplesSectionPresent() {
        String whitelistTip = feedback.usageTip(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, "$");
        assertTrue(whitelistTip.contains("$a Steve"));
        assertTrue(whitelistTip.contains("$a Steve,Alex,Bob"));

        String blacklistTip = feedback.usageTip(OrzUserCmd.BLACKLIST, "$");
        assertTrue(blacklistTip.contains("$d -1.2.3.4"));
        // 玩家名规则 6 种匹配类型与示例都要在 $d ? 帮助中完整展示（M4 审查项）
        assertTrue(blacklistTip.contains("exact精确"));
        assertTrue(blacklistTip.contains("regex正则"));
        assertTrue(blacklistTip.contains("$d player glob bot_*"));
        assertTrue(blacklistTip.contains("$d player regex ^bot"));

        String backupTip = feedback.usageTip(OrzUserCmd.BACKUP, "$");
        assertTrue(backupTip.contains("无参数"));
    }

    @Test
    void usageTip_usesCustomPromptChar() {
        String tip = feedback.usageTip(OrzUserCmd.PERMISSION, "!");
        assertTrue(tip.contains("!p u|up <玩家>"));
    }
}
