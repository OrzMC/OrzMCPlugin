package com.jokerhub.paper.plugin.orzmc.features.botcommands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.I18nConfig;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 回归：组合根注入真实 {@link I18nService} 后，$h / $cmd ? 群帮助必须随注入语言包渲染
 * （default_lang=en-US 时为英文）——曾因 BotModule 未传 i18n 而恒走 zh fallback。
 */
class BotCommandFeedbackServiceInjectedTest {

    private final BotCommandFeedbackService feedback = new BotCommandFeedbackService();

    @AfterEach
    void tearDown() {
        // 复位静态注入，避免污染其它测试类（BotCommandFeedbackServiceTest 依赖 zh fallback）
        BotCommandFeedbackService.init(null);
    }

    private static I18nService enService() throws Exception {
        return new I18nService(
                BotCommandFeedbackServiceInjectedTest.class.getClassLoader(),
                Files.createTempDirectory("orzmc-bot-i18n-en"),
                () -> new I18nConfig("en-US", Map.of(), Map.of()),
                null);
    }

    @Test
    void helpInfo_followsInjectedEnService() throws Exception {
        BotCommandFeedbackService.init(enService());
        String help = feedback.helpInfo("$");
        assertTrue(help.contains("Group Commands Help"), "总帮助标题应随注入语言渲染英文，实际: " + help);
        assertTrue(help.contains("List online players"), "命令描述应随注入语言渲染英文，实际: " + help);
        assertFalse(help.contains("管理员指令"), "注入 en 后不应再输出中文标题");
    }

    @Test
    void usageTip_followsInjectedEnService() throws Exception {
        BotCommandFeedbackService.init(enService());
        String tip = feedback.usageTip(OrzUserCmd.SHOW_PLAYERS, "$");
        assertTrue(tip.contains("$l"), "用法应包含命令名");
        assertTrue(tip.contains("List online players"), "用法描述应随注入语言渲染英文，实际: " + tip);
    }
}
