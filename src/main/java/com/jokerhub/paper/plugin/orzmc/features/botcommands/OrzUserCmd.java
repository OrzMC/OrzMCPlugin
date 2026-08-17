package com.jokerhub.paper.plugin.orzmc.features.botcommands;

public enum OrzUserCmd {
    SHOW_PLAYERS("l", "查看在线玩家", false),
    SHOW_WHITELIST("w", "查看白名单玩家", false),
    SHOW_HELP("h", "查看帮助信息", false),
    ADD_PLAYER_TO_WHITELIST("a", "添加玩家到白名单", true),
    REMOVE_PLAYER_FROM_WHITELIST("r", "从白名单移除玩家", true),
    BACKUP("b", "地图备份", true),
    OPTIMIZE_WORLD("o", "优化地图大小", true),
    EXECUTE_CONSOLE_COMMAND("e", "执行控制台命令", true),
    BLACKLIST("d", "添加/移除/查看IP黑名单", true),
    REVIEW("v", "查看/处理审核申请", true),
    PERMISSION("p", "权限管理（升级/降级）", true);

    private final String cmdName;
    private final String description;
    private final boolean needAdminPermission;

    OrzUserCmd(String cmdName, String description, boolean needAdminPermission) {
        this.cmdName = cmdName;
        this.description = description;
        this.needAdminPermission = needAdminPermission;
    }

    public String cmdName() {
        return cmdName;
    }

    public String display(String promptChar) {
        // 10 个半角空格对齐注释列（命令名均为单字符，$x 恒为 2 列宽）
        return promptChar + this.cmdName + "          " + this.description;
    }

    @Override
    public String toString() {
        return this.cmdName + "\t" + this.description;
    }

    public boolean needAdminPermission() {
        return needAdminPermission;
    }
}
