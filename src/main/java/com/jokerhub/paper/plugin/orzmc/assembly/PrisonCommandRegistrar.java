package com.jokerhub.paper.plugin.orzmc.assembly;

import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.adminInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.guardedExec;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.requirement;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.prison.PrisonCommandService;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;

/** 坐牢命令注册器：/prison <玩家> on|off（admin）。自 FeatureCommandRegistrar 拆出。 */
final class PrisonCommandRegistrar implements CommandGroup {

    private final PrisonCommandService svc;
    private final OrzTextStyles styles;

    PrisonCommandRegistrar(PrisonCommandService svc, OrzTextStyles styles) {
        this.svc = svc;
        this.styles = styles;
    }

    /** 坐牢: /prison <玩家> on（关入）/ off（释放）。仅 OP/orzmc.admin 可用（adminInterceptors）。 */
    @Override
    public void register(Commands commands) {
        List<CommandInterceptor> interceptors = adminInterceptors("prison");
        Predicate<CommandSourceStack> req = requirement(interceptors);

        // 玩家名用 word（MC 名无空格）；子命令 on/off 收尾，对齐 /prison <玩家> on|off 语法
        commands.register(
                literal("prison")
                        .requires(req)
                        .then(argument("player", StringArgumentType.word())
                                .then(literal("on").executes(guardedExec("prison", interceptors, ctx -> {
                                    String playerName = ctx.getArgument("player", String.class);
                                    renderPrisonResultAsync(ctx.getSource().getSender(), svc.imprison(playerName));
                                    return 1;
                                })))
                                .then(literal("off").executes(guardedExec("prison", interceptors, ctx -> {
                                    String playerName = ctx.getArgument("player", String.class);
                                    renderPrisonResultAsync(ctx.getSource().getSender(), svc.release(playerName));
                                    return 1;
                                }))))
                        .executes(guardedExec("prison", interceptors, ctx -> {
                            ctx.getSource().getSender().sendMessage(styles.info("用法: /prison <玩家> on|off"));
                            return 1;
                        }))
                        .build(),
                "坐牢管理（作弊玩家强制进入 prison 组）",
                List.of());
    }

    /** 异步坐牢结果渲染：LP 操作完成后（回调度线程）给命令发起者反馈，命令本身立即返回。 */
    private void renderPrisonResultAsync(
            CommandSender sender, java.util.concurrent.CompletableFuture<PrisonCommandService.Result> future) {
        future.whenComplete((result, err) -> {
            if (err != null) {
                sender.sendMessage(styles.error("坐牢操作异常: " + (err.getMessage() == null ? "未知错误" : err.getMessage())));
            } else if (result instanceof PrisonCommandService.Result.Success s) {
                sender.sendMessage(s.message());
            } else if (result instanceof PrisonCommandService.Result.Failure f) {
                sender.sendMessage(f.message());
            }
        });
    }
}
