package com.jokerhub.paper.plugin.orzmc.features.botcommands;

public final class BotCommandFeedbackService {
    public String helpInfo() {
        return "👨‍💼 管理员命令：\n" + OrzUserCmd.ADD_PLAYER_TO_WHITELIST + "\n" + OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST
                + "\n" + OrzUserCmd.BACKUP + "\n" + "👨🏻‍💻 通用命令: \n" + OrzUserCmd.SHOW_PLAYERS + "\n"
                + OrzUserCmd.SHOW_WHITELIST + "\n" + OrzUserCmd.SHOW_HELP;
    }

    public String adminRequiredTip(OrzUserCmd cmd) {
        if (cmd.needAdminPermission()) {
            return cmd.getCmdString() + " 需要管理员权限";
        }
        return "";
    }

    public String usageTip(OrzUserCmd cmd) {
        return switch (cmd) {
            case ADD_PLAYER_TO_WHITELIST, REMOVE_PLAYER_FROM_WHITELIST -> "用法：\n" + cmd.getCmdString() + " "
                    + "[玩家]\n" + cmd.getCmdString() + " " + "[玩家1] [玩家2] [玩家3]\n" + cmd.getCmdString()
                    + " " + "[玩家1],[玩家2],[玩家3]\n";
            default -> "";
        };
    }
}
