package com.jokerhub.paper.plugin.orzmc.features.command.binding;

import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicy;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public class CooldownInterceptor implements CommandInterceptor {
    private final String commandName;
    private final Supplier<CommandPolicy> policy;
    private final CommandFeedbackService feedbackService = new CommandFeedbackService();

    /** 静态冷却（历史构造器，委托为惰性读取）：等价于固定 {@code cooldownSeconds} 且非 adminOnly。 */
    public CooldownInterceptor(String commandName, int cooldownSeconds) {
        this(commandName, () -> new CommandPolicy(cooldownSeconds, false));
    }

    /**
     * 惰性策略：每次 {@code preHandle} 重新读取 {@link CommandPolicy#cooldownSeconds()}，
     * 使 {@code /orzmc config set command_policies.*.cooldown_secs} 改动即时生效，无需重启。
     */
    public CooldownInterceptor(String commandName, Supplier<CommandPolicy> policy) {
        this.commandName = commandName;
        this.policy = policy;
    }

    @Override
    public Component preHandle(CommandSender sender, String ignored) {
        String key = commandName + "|" + sender.getName();
        if (CooldownRegistry.isCoolingDown(key, Math.max(0, policy.get().cooldownSeconds()))) {
            return feedbackService.cooldownTip();
        }
        return null;
    }
}
