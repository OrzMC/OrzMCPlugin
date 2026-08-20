package com.jokerhub.paper.plugin.orzmc.assembly;

import com.jokerhub.paper.plugin.orzmc.features.command.binding.AdminOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CooldownInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.PlayerOnlyInterceptor;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicy;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * Brigadier 命令注册的拦截器链助手（从 FeatureModule 抽离，纯静态无状态）。
 *
 * <p>责任链：{@link #requirement} 在 {@code .requires()} 阶段过滤 AdminOnly（非管理员不可见），
 * {@link #guardedExec} 在运行时执行 PlayerOnly / Cooldown 检查。AdminOnly 由 requires 隐藏，
 * 故 guardedExec 跳过它。</p>
 */
final class BrigadierSupport {

    private BrigadierSupport() {}

    /**
     * Build a {@link Predicate} for {@code .requires()} on the command node.
     * Only checks {@link AdminOnlyInterceptor} — non-admin users won't see the command.
     */
    static Predicate<CommandSourceStack> requirement(List<CommandInterceptor> interceptors) {
        return stack -> {
            for (CommandInterceptor ci : interceptors) {
                if (ci instanceof AdminOnlyInterceptor aoi) {
                    return aoi.canUse(stack.getSender());
                }
            }
            return true;
        };
    }

    /**
     * Wrap a {@link Command} with runtime interceptor checks
     * (PlayerOnly and Cooldown).  AdminOnly is handled by {@link #requirement(List)}.
     */
    static Command<CommandSourceStack> guardedExec(
            String name, List<CommandInterceptor> interceptors, Command<CommandSourceStack> delegate) {
        return ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            for (CommandInterceptor ci : interceptors) {
                if (ci instanceof AdminOnlyInterceptor) continue;
                Component res = ci.preHandle(sender, name);
                if (res != null) {
                    sender.sendMessage(res);
                    return 1;
                }
            }
            return delegate.run(ctx);
        };
    }

    /** Build interceptors for regular commands from config policies. */
    static List<CommandInterceptor> commandInterceptors(String name, CommandPolicies cp, boolean skipPlayerOnly) {
        CommandPolicy p = cp.policies().getOrDefault(name, new CommandPolicy(0, false));
        List<CommandInterceptor> list = new ArrayList<>();
        if (!skipPlayerOnly) {
            list.add(new PlayerOnlyInterceptor());
        }
        list.add(new AdminOnlyInterceptor(p.adminOnly()));
        list.add(new CooldownInterceptor(name, Math.max(0, p.cooldownSeconds())));
        return list;
    }

    /** Build interceptors for hardcoded admin-only commands (blacklist, config). */
    static List<CommandInterceptor> adminInterceptors(String name) {
        return List.of(new AdminOnlyInterceptor(true), new CooldownInterceptor(name, 0));
    }
}
