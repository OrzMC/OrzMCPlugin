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
        assertTrue(tip.contains("[玩家]"));
    }

    @Test
    void usageTip_forConsoleCommand() {
        String tip = feedback.usageTip(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, "$");
        assertTrue(tip.contains("$e"));
        assertTrue(tip.contains("[控制台命令]"));
    }

    @Test
    void usageTip_forUnsupported_returnsEmpty() {
        String tip = feedback.usageTip(OrzUserCmd.SHOW_HELP, "$");
        assertEquals("", tip);
    }
}
