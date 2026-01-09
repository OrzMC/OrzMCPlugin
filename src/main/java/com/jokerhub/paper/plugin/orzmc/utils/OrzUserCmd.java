package com.jokerhub.paper.plugin.orzmc.utils;

import com.jokerhub.paper.plugin.orzmc.OrzMC;

public enum OrzUserCmd {
    SHOW_PLAYERS("l", "查看在线玩家", false), SHOW_WHITELIST("w", "查看白名单玩家", false), SHOW_HELP("h", "查看帮助信息", false), ADD_PLAYER_TO_WHITELIST("a", "添加玩家到白名单", true), REMOVE_PLAYER_FROM_WHITELIST("r", "从白名单移除玩家", true), BACKUP("b", "地图备份", true);

    private final String cmdName;
    private final String description;
    private final boolean needAdminPermission;

    OrzUserCmd(String cmdName, String description, boolean needAdminPermission) {
        this.cmdName = cmdName;
        this.description = description;
        this.needAdminPermission = needAdminPermission;
    }

    private static String cmdPromptChar() {
        return OrzMC.plugin().getConfig().getString("cmd_prompt_char", "$");
    }

    public static boolean isValidCmd(String message) {
        return message.startsWith(cmdPromptChar());
    }

    public static String helpInfo() {
        return "👨‍💼 管理员命令：\n" + OrzUserCmd.ADD_PLAYER_TO_WHITELIST + "\n" + OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST + "\n" + OrzUserCmd.BACKUP + "\n" + "👨🏻‍💻 通用命令: \n" + OrzUserCmd.SHOW_PLAYERS + "\n" + OrzUserCmd.SHOW_WHITELIST + "\n" + OrzUserCmd.SHOW_HELP;
    }

    public String getCmdString() {
        return cmdPromptChar() + this.cmdName;
    }

    @Override
    public String toString() {
        return this.getCmdString() + "\t" + this.description;
    }

    public String adminPermissionRequiredTip() {
        if (this.needAdminPermission) {
            return this.getCmdString() + " 需要管理员权限";
        } else {
            return "";
        }
    }

    public String usageTip() {
        return switch (this) {
            case OrzUserCmd.ADD_PLAYER_TO_WHITELIST, OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST ->
                    "用法：\n" + this.getCmdString() + " " + "[玩家]\n" + this.getCmdString() + " " + "[玩家1] [玩家2] [玩家3]\n" + this.getCmdString() + " " + "[玩家1],[玩家2],[玩家3]\n";
            default -> "";
        };
    }
}
