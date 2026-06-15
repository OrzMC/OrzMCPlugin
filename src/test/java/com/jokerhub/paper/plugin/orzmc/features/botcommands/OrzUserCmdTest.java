package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrzUserCmdTest {

    @Test
    void cmdName_matchesExpected() {
        assertEquals("l", OrzUserCmd.SHOW_PLAYERS.cmdName());
        assertEquals("w", OrzUserCmd.SHOW_WHITELIST.cmdName());
        assertEquals("h", OrzUserCmd.SHOW_HELP.cmdName());
        assertEquals("a", OrzUserCmd.ADD_PLAYER_TO_WHITELIST.cmdName());
        assertEquals("r", OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.cmdName());
        assertEquals("b", OrzUserCmd.BACKUP.cmdName());
        assertEquals("o", OrzUserCmd.OPTIMIZE_WORLD.cmdName());
        assertEquals("e", OrzUserCmd.EXECUTE_CONSOLE_COMMAND.cmdName());
    }

    @Test
    void display_includesPromptAndDescription() {
        String disp = OrzUserCmd.SHOW_PLAYERS.display("$");
        assertTrue(disp.startsWith("$l"));
        assertTrue(disp.contains("查看在线玩家"));
    }

    @Test
    void display_customPromptChar() {
        String disp = OrzUserCmd.SHOW_PLAYERS.display("!");
        assertTrue(disp.startsWith("!l"));
    }

    @Test
    void needAdminPermission_adminCommands() {
        assertTrue(OrzUserCmd.ADD_PLAYER_TO_WHITELIST.needAdminPermission());
        assertTrue(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.needAdminPermission());
        assertTrue(OrzUserCmd.BACKUP.needAdminPermission());
        assertTrue(OrzUserCmd.OPTIMIZE_WORLD.needAdminPermission());
        assertTrue(OrzUserCmd.EXECUTE_CONSOLE_COMMAND.needAdminPermission());
    }

    @Test
    void needAdminPermission_userCommands() {
        assertFalse(OrzUserCmd.SHOW_PLAYERS.needAdminPermission());
        assertFalse(OrzUserCmd.SHOW_WHITELIST.needAdminPermission());
        assertFalse(OrzUserCmd.SHOW_HELP.needAdminPermission());
    }
}
