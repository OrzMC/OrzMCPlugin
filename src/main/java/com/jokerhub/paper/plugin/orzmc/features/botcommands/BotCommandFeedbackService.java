package com.jokerhub.paper.plugin.orzmc.features.botcommands;

/**
 * 群指令帮助信息生成（统一模板）。
 *
 * <p>总帮助 {@code $h}：🤖 标题 + 发送提示 + emoji 分组标题（👨‍💼/👨🏻‍💻）。
 *
 * <p>单命令帮助 {@code $x ?}：🎯 标题 + 📚 用法 + 🚀 示例 三段式。
 * 所有 {@link OrzUserCmd} 都经由 {@link #usageBlock} 生成同一套结构，
 * fallback（无参数/参数不完整）与主动查询共用同一内容。
 */
public final class BotCommandFeedbackService {

    /** 统一分隔线（ASCII 虚线，33 连字符，与群消息统一样式一致）。 */
    private static final String DIVIDER = "---------------------------------";

    public String helpInfo(String promptChar) {
        return "🤖 OrzMC 群指令帮助\n"
                + "发送「"
                + promptChar
                + "x ?」可查看单个指令的用法示例\n"
                + DIVIDER
                + "\n"
                + "👨‍💼 管理员指令\n"
                + OrzUserCmd.ADD_PLAYER_TO_WHITELIST.display(promptChar)
                + "\n"
                + OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST.display(promptChar)
                + "\n"
                + OrzUserCmd.BLACKLIST.display(promptChar)
                + "\n"
                + OrzUserCmd.BACKUP.display(promptChar)
                + "\n"
                + OrzUserCmd.OPTIMIZE_WORLD.display(promptChar)
                + "\n"
                + OrzUserCmd.EXECUTE_CONSOLE_COMMAND.display(promptChar)
                + "\n"
                + OrzUserCmd.REVIEW.display(promptChar)
                + "\n"
                + OrzUserCmd.PERMISSION.display(promptChar)
                + "\n"
                + DIVIDER
                + "\n"
                + "👨🏻‍💻 通用指令\n"
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

    /**
     * 单命令帮助：🎯 标题 + 📚 用法 + 🚀 示例 三段式统一模板。
     * 全部 11 个命令均有定义，保证 {@code $cmd ?} 与 fallback 永不降级执行。
     */
    public String usageTip(OrzUserCmd cmd, String promptChar) {
        String name = promptChar + cmd.cmdName();
        return switch (cmd) {
            case ADD_PLAYER_TO_WHITELIST, REMOVE_PLAYER_FROM_WHITELIST ->
                usageBlock(
                        "🎯 " + name + " " + (cmd == OrzUserCmd.ADD_PLAYER_TO_WHITELIST ? "添加玩家到白名单" : "移除白名单玩家"),
                        name + " <玩家> <玩家2> ...（空格或逗号分隔，可批量）",
                        name + " Steve\n" + name + " Steve Alex Bob\n" + name + " Steve,Alex,Bob");
            case EXECUTE_CONSOLE_COMMAND ->
                usageBlock("🎯 " + name + " 执行控制台命令", name + " <控制台命令>", name + " plugins\n" + name + " say 大家好");
            case BLACKLIST ->
                usageBlock(
                        "🎯 " + name + " IP/玩家名规则管理",
                        name + "                查看黑名单\n"
                                + name
                                + " [IP]           添加黑名单\n"
                                + name
                                + " -[IP]          移除黑名单\n"
                                + name
                                + " player <type> <value>   添加玩家名规则\n"
                                + name
                                + " -player <type> <value>  移除玩家名规则\n"
                                + name
                                + " player list             查看玩家名规则\n"
                                + "匹配类型 <type>: exact精确 / prefix前缀 / suffix后缀 / contains包含 / glob通配(*?) / regex正则",
                        name + "\n"
                                + name
                                + " 1.2.3.4\n"
                                + name
                                + " -1.2.3.4\n"
                                + name
                                + " player exact 服主\n"
                                + name
                                + " player prefix bot_\n"
                                + name
                                + " player suffix _test\n"
                                + name
                                + " player contains admin\n"
                                + name
                                + " player glob bot_*\n"
                                + name
                                + " player regex ^bot\n"
                                + name
                                + " -player prefix bot_");
            case REVIEW ->
                usageBlock(
                        "🎯 " + name + " 审核申请",
                        name + " l [页码]       查看待审列表\n"
                                + name
                                + " y <玩家>       通过申请\n"
                                + name
                                + " n <玩家>       拒绝申请\n"
                                + "（y/n 可写 yes/no；同玩家多类型申请用 "
                                + name
                                + " y <typeId> <玩家>）",
                        name + " l\n" + name + " l 2\n" + name + " y Steve\n" + name + " y builder-promotion Steve");
            case PERMISSION ->
                usageBlock(
                        "🎯 " + name + " 权限管理",
                        name + " u|up <玩家>    权限升级（default→member→builder→admin）\n"
                                + name
                                + " d|down <玩家>  权限降级（admin→builder→member→default）",
                        name + " u Steve\n" + name + " d Steve");
            case SHOW_PLAYERS -> usageBlock("🎯 " + name + " 查看在线玩家", name + "（无参数，直接执行）", name);
            case SHOW_WHITELIST ->
                usageBlock("🎯 " + name + " 查看白名单玩家", name + " [页码]（可选，如 " + name + " 2）", name + "\n" + name + " 2");
            case SHOW_HELP -> usageBlock("🎯 " + name + " 查看帮助", name + "（无参数，直接显示本帮助）", name);
            case BACKUP -> usageBlock("🎯 " + name + " 地图备份", name + "（无参数，直接执行备份）", name);
            case OPTIMIZE_WORLD -> usageBlock("🎯 " + name + " 优化地图大小", name + "（无参数，直接执行优化）", name);
        };
    }

    /**
     * 单命令帮助统一三段式结构：标题 → 📚 用法 → 🚀 示例。
     * 新增命令只需在此提供三段内容，展示逻辑保持一致。
     */
    private String usageBlock(String title, String usageLines, String exampleLines) {
        return title
                + "\n"
                + DIVIDER
                + "\n"
                + "📚 用法：\n"
                + usageLines
                + "\n"
                + DIVIDER
                + "\n"
                + "🚀 示例：\n"
                + exampleLines;
    }
}
