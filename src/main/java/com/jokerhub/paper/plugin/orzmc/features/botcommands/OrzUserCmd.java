package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigService;

public enum OrzUserCmd {
    SHOW_PLAYERS("l", "查看在线玩家", false),
    SHOW_WHITELIST("w", "查看白名单玩家", false),
    SHOW_HELP("h", "查看帮助信息", false),
    ADD_PLAYER_TO_WHITELIST("a", "添加玩家到白名单", true),
    REMOVE_PLAYER_FROM_WHITELIST("r", "从白名单移除玩家", true),
    BACKUP("b", "地图备份", true),
    OPTIMIZE_WORLD("o", "优化地图大小", true);

    private final String cmdName;
    private final String description;
    private final boolean needAdminPermission;
    private static ConfigService configService;

    OrzUserCmd(String cmdName, String description, boolean needAdminPermission) {
        this.cmdName = cmdName;
        this.description = description;
        this.needAdminPermission = needAdminPermission;
    }

    public static void setConfigService(ConfigService service) {
        configService = service;
    }

    private static String cmdPromptChar() {
        try {
            if (configService == null) return "$";
            return configService.getConfig("bot").getString("cmd_prompt_char", "$");
        } catch (Exception e) {
            return "$";
        }
    }

    public static boolean isValidCmd(String message) {
        return message.startsWith(cmdPromptChar());
    }

    public String getCmdString() {
        return cmdPromptChar() + this.cmdName;
    }

    @Override
    public String toString() {
        return this.getCmdString() + "\t" + this.description;
    }

    public boolean needAdminPermission() {
        return needAdminPermission;
    }
}
