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
import com.jokerhub.paper.plugin.orzmc.features.review.ReviewCommandService;
import com.jokerhub.paper.plugin.orzmc.infra.config.configs.CommandPolicies;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** 通用审核命令注册器：/apply（玩家申请）+ /review（管理员审核）。自 FeatureCommandRegistrar 拆出。 */
final class ReviewCommandRegistrar implements CommandGroup {

    private final ReviewCommandService reviewCommandService;
    private final OrzTextStyles styles;
    private final Supplier<CommandPolicies> cpSupplier;
    private final Predicate<Player> prisonCheck;
    private final I18nService i18n;
    private final CommandFeedbackService feedback;

    ReviewCommandRegistrar(
            ReviewCommandService reviewCommandService,
            OrzTextStyles styles,
            Supplier<CommandPolicies> cpSupplier,
            Predicate<Player> prisonCheck,
            I18nService i18n) {
        this.reviewCommandService = reviewCommandService;
        this.styles = styles;
        this.cpSupplier = cpSupplier;
        this.prisonCheck = prisonCheck;
        this.i18n = i18n;
        this.feedback = new CommandFeedbackService(i18n);
    }

    @Override
    public void register(Commands commands) {

        // ---- /apply 通用申请命令 ----
        List<CommandInterceptor> applyInterceptors =
                withPrisonDeny(commandInterceptors("apply", cpSupplier, false, i18n), prisonCheck, i18n);
        commands.register(
                literal("apply")
                        .requires(requirement(applyInterceptors))
                        // /apply — 列出可申请类型
                        .executes(guardedExec("apply", applyInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(styles.error(feedback.playerRequiredMessage(sender)));
                                return 1;
                            }
                            renderReviewResult(sender, reviewCommandService.listTypes(player));
                            return 1;
                        }))
                        // /apply status — 查看自己的申请
                        .then(literal("status").executes(guardedExec("apply", applyInterceptors, ctx -> {
                            var sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(styles.error(feedback.playerRequiredMessage(sender)));
                                return 1;
                            }
                            renderReviewResult(sender, reviewCommandService.status(player));
                            return 1;
                        })))
                        // /apply cancel <type> — 撤回待审申请
                        .then(literal("cancel")
                                .then(argument("type", StringArgumentType.word())
                                        .executes(guardedExec("apply", applyInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) {
                                                sender.sendMessage(
                                                        styles.error(feedback.playerRequiredMessage(sender)));
                                                return 1;
                                            }
                                            String type = ctx.getArgument("type", String.class);
                                            renderReviewResult(sender, reviewCommandService.cancel(player, type));
                                            return 1;
                                        }))))
                        // /apply <type> [理由] — 提交申请
                        .then(argument("type", StringArgumentType.word())
                                .executes(guardedExec("apply", applyInterceptors, ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    if (!(sender instanceof Player player)) {
                                        sender.sendMessage(styles.error(feedback.playerRequiredMessage(sender)));
                                        return 1;
                                    }
                                    String type = ctx.getArgument("type", String.class);
                                    renderReviewResult(sender, reviewCommandService.apply(player, type, ""));
                                    return 1;
                                }))
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(guardedExec("apply", applyInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player player)) {
                                                sender.sendMessage(
                                                        styles.error(feedback.playerRequiredMessage(sender)));
                                                return 1;
                                            }
                                            String type = ctx.getArgument("type", String.class);
                                            String reason = ctx.getArgument("reason", String.class);
                                            renderReviewResult(
                                                    sender, reviewCommandService.apply(player, type, reason));
                                            return 1;
                                        }))))
                        .build(),
                feedback.commandDescription(MessageKeys.CMD_DESC_APPLY),
                List.of("apply"));

        // ---- /review approve|reject <name> — 管理员审核（替代 /rank approve|reject）----
        List<CommandInterceptor> adminReviewInterceptors = adminInterceptors("review", i18n);
        commands.register(
                literal("review")
                        .requires(requirement(adminReviewInterceptors))
                        .then(literal("approve")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(guardedExec("review", adminReviewInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player admin)) {
                                                sender.sendMessage(
                                                        styles.error(feedback.playerRequiredMessage(sender)));
                                                return 1;
                                            }
                                            String name = ctx.getArgument("name", String.class);
                                            renderReviewResultAsync(
                                                    sender, reviewCommandService.review(admin, name, true));
                                            return 1;
                                        }))))
                        .then(literal("reject")
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(guardedExec("review", adminReviewInterceptors, ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            if (!(sender instanceof Player admin)) {
                                                sender.sendMessage(
                                                        styles.error(feedback.playerRequiredMessage(sender)));
                                                return 1;
                                            }
                                            String name = ctx.getArgument("name", String.class);
                                            renderReviewResultAsync(
                                                    sender, reviewCommandService.review(admin, name, false));
                                            return 1;
                                        }))))
                        .build(),
                feedback.commandDescription(MessageKeys.CMD_DESC_REVIEW),
                List.of("review"));
    }

    private void renderReviewResult(CommandSender sender, ReviewCommandService.Result result) {
        if (result instanceof ReviewCommandService.Result.Failure f) {
            sender.sendMessage(f.message());
        } else if (result instanceof ReviewCommandService.Result.Success s) {
            sender.sendMessage(s.message());
        }
    }

    /** 异步审核结果渲染：授权完成后（回同步调度线程）给命令发起者反馈，命令本身立即返回。 */
    private void renderReviewResultAsync(CommandSender sender, CompletableFuture<ReviewCommandService.Result> future) {
        future.whenComplete((result, err) -> {
            if (err != null) {
                sender.sendMessage(styles.error(feedback.message(
                        sender,
                        MessageKeys.CMD_REVIEW_FAILED,
                        Map.of("detail", err.getMessage() == null ? "" : err.getMessage()))));
            } else {
                renderReviewResult(sender, result);
            }
        });
    }
}
