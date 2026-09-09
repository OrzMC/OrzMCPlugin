package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.I18nConfig;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import java.nio.file.Files;
import java.util.Map;

/**
 * 群指令帮助信息生成（统一模板，i18n P3a）。
 *
 * <p>总帮助 {@code $h}：标题 + 发送提示 + 分组标题 + 每命令描述行；
 * 单命令帮助 {@code $x ?}：🎯 标题 + 📚 用法 + 🚀 示例 三段式。
 * 文案全部来自语言包 {@code bot.*}（名称/提示符经 {@code {name}/{prompt}/{cmd}} 注入），
 * 渲染语言为默认语言（R1）；组合根经 {@link #init} 注入真实 {@link I18nService}，
 * 未注入（单测/早期调用）时回落默认 zh 语言包。</p>
 */
public final class BotCommandFeedbackService {

    /** 统一分隔线（ASCII 虚线，33 连字符，与群消息统一样式一致）。 */
    private static final String DIVIDER = "---------------------------------";

    private static volatile I18nService service;
    private static volatile I18nService fallback;

    /** 组合根注入（BotModule 装配时调用，含数据目录 overlay）；null 时复位（测试隔离用）。 */
    public static void init(I18nService i18n) {
        service = i18n;
    }

    private static I18nService i18n() {
        I18nService s = service;
        if (s != null) {
            return s;
        }
        I18nService f = fallback;
        if (f == null) {
            try {
                f = new I18nService(
                        BotCommandFeedbackService.class.getClassLoader(),
                        Files.createTempDirectory("orzmc-bot-i18n"),
                        () -> I18nConfig.DEFAULT,
                        null);
            } catch (Exception e) {
                f = null;
            }
            fallback = f;
        }
        return f == null ? service : f;
    }

    private String t(String key, Map<String, String> vars) {
        return i18n().msg(i18n().langFor(), key, vars);
    }

    private String t(String key) {
        return i18n().msg(i18n().langFor(), key);
    }

    /** 单命令渲染名（提示符+字母）。 */
    private static String name(OrzUserCmd cmd, String promptChar) {
        return promptChar + cmd.cmdName();
    }

    private static String letter(OrzUserCmd cmd) {
        return cmd.cmdName();
    }

    public String helpInfo(String promptChar) {
        String intro = t("bot.help_intro", Map.of("prompt", promptChar));
        return t("bot.help_title")
                + "\n"
                + intro
                + "\n"
                + DIVIDER
                + "\n"
                + t("bot.help_admin_section")
                + "\n"
                + line(OrzUserCmd.ADD_PLAYER_TO_WHITELIST, promptChar)
                + "\n"
                + line(OrzUserCmd.REMOVE_PLAYER_FROM_WHITELIST, promptChar)
                + "\n"
                + line(OrzUserCmd.BLACKLIST, promptChar)
                + "\n"
                + line(OrzUserCmd.BACKUP, promptChar)
                + "\n"
                + line(OrzUserCmd.OPTIMIZE_WORLD, promptChar)
                + "\n"
                + line(OrzUserCmd.EXECUTE_CONSOLE_COMMAND, promptChar)
                + "\n"
                + line(OrzUserCmd.REVIEW, promptChar)
                + "\n"
                + line(OrzUserCmd.PERMISSION, promptChar)
                + "\n"
                + DIVIDER
                + "\n"
                + t("bot.help_general_section")
                + "\n"
                + line(OrzUserCmd.SHOW_PLAYERS, promptChar)
                + "\n"
                + line(OrzUserCmd.SHOW_WHITELIST, promptChar)
                + "\n"
                + line(OrzUserCmd.SHOW_HELP, promptChar);
    }

    /** 帮助列表单行：{prompt}{字母} + 对齐空格 + 描述。 */
    private String line(OrzUserCmd cmd, String promptChar) {
        String desc = t("bot.desc." + letter(cmd));
        return name(cmd, promptChar) + "          " + desc;
    }

    public String adminRequiredTip(OrzUserCmd cmd, String promptChar) {
        if (cmd.needAdminPermission()) {
            return t("bot.admin_required", Map.of("cmd", name(cmd, promptChar)));
        }
        return "";
    }

    /**
     * 单命令帮助：🎯 标题 + 📚 用法 + 🚀 示例 三段式统一模板。
     * 全部 11 个命令均有定义，保证 {@code $cmd ?} 与 fallback 永不降级执行。
     */
    public String usageTip(OrzUserCmd cmd, String promptChar) {
        String nm = name(cmd, promptChar);
        String l = letter(cmd);
        return usageBlock(
                t("bot.usage." + l, Map.of("name", nm)),
                t("bot.params." + l, Map.of("name", nm)),
                t("bot.examples." + l, Map.of("name", nm)));
    }

    private String usageBlock(String title, String usageLines, String exampleLines) {
        return title
                + "\n"
                + DIVIDER
                + "\n"
                + t("bot.lbl_usage")
                + "\n"
                + usageLines
                + "\n"
                + DIVIDER
                + "\n"
                + t("bot.lbl_example")
                + "\n"
                + exampleLines;
    }
}
