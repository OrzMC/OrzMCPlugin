package com.jokerhub.paper.plugin.orzmc.features.botcommands;

public final class BotCommandFeedbackService {
    public String helpInfo(String promptChar) {
        return "👨‍💼 管理员命令：\n"
                + OrzUserCmd.ADD_PLAYER_TO_WHITELIST.display(promptChar)
                + "\n"
                + OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.display(promptChar)
                + "\n"
                + OrzUserCmd.BACKUP.display(promptChar)
                + "\n"
                + OrzUserCmd.OPTIMIZE_WORLD.display(promptChar)
                + "\n"
                + OrzUserCmd.EXECUTE_CONSOLE_COMMAND.display(promptChar)
                + "\n"
                + "👨🏻‍💻 通用命令: \n"
                + OrzUserCmd.SHOW_PLAYERS.display(promptChar)
                + "\n"
                + OrzUserCmd.SHOW_WHITELIST.display(promptChar)
                + "\n"
                + OrzUserCmd.SHOW_HELP.display(promptChar);
    }

    public String adminRequiredTip(OrzUserCmd cmd, String promptChar) {
        if (cmd.needAdminPermission()) {
            return promptChar + cmd.cmdName() + " 需要管理员权限";
        }
        return "";
    }

    public String usageTip(OrzUserCmd cmd, String promptChar) {
        return switch (cmd) {
            case ADD_PLAYER_TO_WHITELIST, REMOVE_PLAYER_FROM_WHITELIST ->
                "用法：\n"
                        + promptChar
                        + cmd.cmdName()
                        + " "
                        + "[玩家]\n"
                        + promptChar
                        + cmd.cmdName()
                        + " "
                        + "[玩家1] [玩家2] [玩家3]\n"
                        + promptChar
                        + cmd.cmdName()
                        + " "
                        + "[玩家1],[玩家2],[玩家3]\n";
            case EXECUTE_CONSOLE_COMMAND ->
                "用法：\n"
                        + promptChar
                        + cmd.cmdName()
                        + " "
                        + "[控制台命令]\n"
                        + promptChar
                        + cmd.cmdName()
                        + " plugins\n"
                        + promptChar
                        + cmd.cmdName()
                        + " say hello";
            default -> "";
        };
    }
}
