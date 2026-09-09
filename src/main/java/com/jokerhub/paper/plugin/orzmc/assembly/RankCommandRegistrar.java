package com.jokerhub.paper.plugin.orzmc.assembly;

import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.adminInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.commandInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.guardedExec;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.requirement;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.withPrisonDeny;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankCommandService;
import com.jokerhub.paper.plugin.orzmc.features.rank.RankService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 权限晋升查询命令注册器：/rank（玩家查自己 / admin 查指定玩家）。自 FeatureCommandRegistrar 拆出。 */
final class RankCommandRegistrar implements CommandGroup {

    private final RankCommandService rankCommandService;
    private final RankService rankService;
    private final OrzTextStyles styles;
    private final Supplier<CommandPolicies> cpSupplier;
    private final Predicate<Player> prisonCheck;
    private final I18nService i18n;
    private final CommandFeedbackService feedback;

    RankCommandRegistrar(
            RankCommandService rankCommandService,
            RankService rankService,
            OrzTextStyles styles,
            Supplier<CommandPolicies> cpSupplier,
            Predicate<Player> prisonCheck,
            I18nService i18n) {
        this.rankCommandService = rankCommandService;
        this.rankService = rankService;
        this.styles = styles;
        this.cpSupplier = cpSupplier;
        this.prisonCheck = prisonCheck;
        this.i18n = i18n;
        this.feedback = new CommandFeedbackService(i18n);
    }

    @Override
    public void register(Commands commands) {
        // ---- /rank — 查询自己 / /rank <玩家> — admin 查指定玩家 ----
        List<CommandInterceptor> rankInterceptors =
                withPrisonDeny(commandInterceptors("rank", cpSupplier, false, i18n), prisonCheck, i18n);
        List<CommandInterceptor> adminRankInterceptors = adminInterceptors("rank", i18n);
        commands.register(
                literal("rank")
                        .requires(requirement(rankInterceptors))
                        // /rank <玩家> — admin 查指定玩家
                        .then(argument("player", StringArgumentType.greedyString())
                                .requires(requirement(adminRankInterceptors))
                                .executes(guardedExec("rank", adminRankInterceptors, ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    String playerName = ctx.getArgument("player", String.class);
                                    UUID id = rankService.resolvePlayerId(playerName);
                                    if (id == null) {
                                        sender.sendMessage(styles.error(feedback.defaultMessage(
                                                MessageKeys.CMD_RANK_PLAYER_NOT_FOUND, Map.of("player", playerName))));
                                        return 1;
                                    }
                                    renderRankResult(sender, rankCommandService.statusOf(id));
                                    return 1;
                                })))
                        // /rank — 玩家查自己
                        .executes(guardedExec("rank", rankInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(styles.error(feedback.playerRequiredMessage(sender)));
                                return 1;
                            }
                            renderRankResult(sender, rankCommandService.status(player));
                            return 1;
                        }))
                        .build(),
                feedback.commandDescription(MessageKeys.CMD_DESC_RANK),
                List.of("rank"));
    }

    private void renderRankResult(CommandSender sender, RankCommandService.Result result) {
        if (result instanceof RankCommandService.Result.Failure f) {
            sender.sendMessage(f.message());
        } else if (result instanceof RankCommandService.Result.Success s) {
            sender.sendMessage(s.message());
        }
    }
}
