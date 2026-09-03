package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 坐牢拦截：prison 玩家不能使用越狱/引导类全开放命令（/tpbow /guide /menu /portal /apply /rank 等）。
 *
 * <p>坐牢玩家仅保留 essentials.msg 私聊权限——聊天与私聊<b>不</b>受本拦截影响（聊天事件不
 * 走命令拦截器链）；/prison 管理命令本身是 admin-only（adminInterceptors），也不挂本拦截。
 * 玩家是坐牢状态时 preHandle 返回提示文案，guardedExec 发送后短路命令（不执行 delegate）。</p>
 *
 * <p>判定的 {@code prisonCheck} 由装配层注入（{@code prisonService::isPrisoner}，LP 软依赖
 * 条件实例化，LP 缺失时恒 false 不拦截）。</p>
 */
public final class PrisonDenyInterceptor implements CommandInterceptor {

    private final Predicate<Player> prisonCheck;
    private final CommandFeedbackService feedbackService = new CommandFeedbackService();

    public PrisonDenyInterceptor(Predicate<Player> prisonCheck) {
        this.prisonCheck = prisonCheck;
    }

    @Override
    public Component preHandle(CommandSender sender, String commandName) {
        if (sender instanceof Player player && prisonCheck.test(player)) {
            return feedbackService.prisonDeniedTip();
        }
        return null;
    }
}
