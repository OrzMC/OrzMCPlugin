package com.jokerhub.paper.plugin.orzmc.assembly;

import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.adminInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.guardedExec;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.requirement;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.commands.OrzConfigCommand;
import com.jokerhub.paper.plugin.orzmc.features.bot.ImAdminService;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.infra.config.ConfigPath;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 配置管理命令注册器：/config list|get|set|reset|dump|reload（别名 cfg）。自 FeatureCommandRegistrar 拆出。 */
final class ConfigCommandRegistrar implements CommandGroup {

    private final OrzConfigCommand cfgCmd;
    private final OrzTextStyles styles;
    private final ImAdminService imAdmin;

    ConfigCommandRegistrar(OrzConfigCommand cfgCmd, OrzTextStyles styles, ImAdminService imAdmin) {
        this.cfgCmd = cfgCmd;
        this.styles = styles;
        this.imAdmin = imAdmin;
    }

    /** Config: /config list|get|set|reset|dump|reload */
    @Override
    public void register(Commands commands) {
        List<CommandInterceptor> interceptors = adminInterceptors("config");
        Predicate<CommandSourceStack> req = requirement(interceptors);
        List<String> configPaths = new ArrayList<>(ConfigPath.all().keySet());

        // Tab suggestion provider for config paths
        SuggestionProvider<CommandSourceStack> pathSuggestions = (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (String path : configPaths) {
                if (path.toLowerCase().startsWith(prefix)) {
                    builder.suggest(path);
                }
            }
            return builder.buildFuture();
        };

        LiteralCommandNode<CommandSourceStack> node = literal("config")
                .requires(req)
                .then(literal("list").executes(guardedExec("config", interceptors, ctx -> {
                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[] {"list"});
                    return 1;
                })))
                .then(ImCommandRegistrar.build(imAdmin)) // IM 内建网关管理（/config im setup|status|bind|test，D12）
                .then(literal("get")
                        .then(argument("path", StringArgumentType.greedyString())
                                .suggests(pathSuggestions)
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String path = ctx.getArgument("path", String.class);
                                    cfgCmd.onCommand(
                                            ctx.getSource().getSender(), null, "config", new String[] {"get", path});
                                    return 1;
                                }))))
                .then(literal("set")
                        .then(argument("args", StringArgumentType.greedyString())
                                .suggests(pathSuggestions)
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String rest = ctx.getArgument("args", String.class);
                                    String[] cmdArgs = ("set " + rest).split(" ");
                                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", cmdArgs);
                                    return 1;
                                }))))
                .then(literal("reset")
                        .then(argument("path", StringArgumentType.greedyString())
                                .suggests(pathSuggestions)
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String path = ctx.getArgument("path", String.class);
                                    cfgCmd.onCommand(
                                            ctx.getSource().getSender(), null, "config", new String[] {"reset", path});
                                    return 1;
                                }))))
                .then(literal("dump").executes(guardedExec("config", interceptors, ctx -> {
                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[] {"dump"});
                    return 1;
                })))
                .then(literal("reload")
                        .then(argument("name", StringArgumentType.word())
                                .executes(guardedExec("config", interceptors, ctx -> {
                                    String name = ctx.getArgument("name", String.class);
                                    cfgCmd.onCommand(
                                            ctx.getSource().getSender(), null, "config", new String[] {"reload", name});
                                    return 1;
                                })))
                        .executes(guardedExec("config", interceptors, ctx -> {
                            cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[] {"reload"});
                            return 1;
                        })))
                .executes(guardedExec("config", interceptors, ctx -> {
                    cfgCmd.onCommand(ctx.getSource().getSender(), null, "config", new String[0]);
                    return 1;
                }))
                .build();

        commands.register(node, "配置管理", List.of("cfg"));
    }
}
