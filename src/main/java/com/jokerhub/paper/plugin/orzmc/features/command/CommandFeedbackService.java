package com.jokerhub.paper.plugin.orzmc.features.command;

import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.Lang;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 命令通用反馈文案（游戏内玩家可见）。
 *
 * <p>内置通用提示已迁入语言包（{@code common.*}，i18n P1）；方法签名携带
 * {@link CommandSender} 以便在边界决议语言（玩家 locale → 默认）。其余「由调用方组装文本」
 * 的纯包装方法（usage/port/security）暂保留原样，随各自功能域 P2 迁移接管。</p>
 */
public final class CommandFeedbackService {

    private final I18nService i18n;

    public CommandFeedbackService(I18nService i18n) {
        this.i18n = i18n;
    }

    public TextComponent cooldownTip(CommandSender sender) {
        return tip(sender, MessageKeys.COMMON_COOLDOWN);
    }

    public TextComponent adminRequiredTip(CommandSender sender) {
        return tip(sender, MessageKeys.COMMON_ADMIN_REQUIRED);
    }

    public TextComponent playerRequiredTip(CommandSender sender) {
        return tip(sender, MessageKeys.COMMON_PLAYER_REQUIRED);
    }

    public TextComponent prisonDeniedTip(CommandSender sender) {
        return tip(sender, MessageKeys.COMMON_PRISON_DENIED);
    }

    public TextComponent securityBlockedTip(String reason) {
        return Component.text(reason);
    }

    /** 纯文本版「需要玩家执行」提示（供 styles.error 包装保留红色样式；拦截器走 Component 版）。 */
    public String playerRequiredMessage(CommandSender sender) {
        return resolve(sender, MessageKeys.COMMON_PLAYER_REQUIRED);
    }

    /** 游戏内命令注册期帮助描述（/help 可见；无 sender 语境 → default_lang，R1）。 */
    public String commandDescription(String key) {
        return i18n.msg(i18n.langFor(), key);
    }

    /** 管理/运维命令提示：按 default_lang（R1）渲染，与 desc 同源决议。 */
    public String defaultMessage(String key) {
        return i18n.msg(i18n.langFor(), key);
    }

    /** 带变量的管理/运维命令提示（R1）。 */
    public String defaultMessage(String key, Map<String, String> vars) {
        return i18n.msg(i18n.langFor(), key, vars);
    }

    /** 带变量的按 sender 决议渲染（P6 起供 Registrar 内联提示使用）。 */
    public String message(CommandSender sender, String key, Map<String, String> vars) {
        Lang lang = sender instanceof Player player ? i18n.langFor(player) : i18n.langFor();
        return i18n.msg(lang, key, vars);
    }

    private String resolve(CommandSender sender, String key) {
        Lang lang = sender instanceof Player player ? i18n.langFor(player) : i18n.langFor();
        return i18n.msg(lang, key);
    }

    private TextComponent tip(CommandSender sender, String key) {
        return Component.text(resolve(sender, key));
    }
}
