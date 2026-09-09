package com.jokerhub.paper.plugin.orzmc.assembly;

import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.adminInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.guardedExec;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.requirement;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.features.command.CommandFeedbackService;
import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.update.UpdateCommandService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.function.Predicate;

/** 自更新命令注册器：/update check|now（admin，别名 upd）。自 FeatureCommandRegistrar 拆出。 */
final class UpdateCommandRegistrar implements CommandGroup {

    private final UpdateCommandService svc;
    private final OrzTextStyles styles;
    private final I18nService i18n;
    private final CommandFeedbackService feedback;

    UpdateCommandRegistrar(UpdateCommandService svc, OrzTextStyles styles, I18nService i18n) {
        this.svc = svc;
        this.styles = styles;
        this.i18n = i18n;
        this.feedback = new CommandFeedbackService(i18n);
    }

    /** Update: /update check|now（插件自更新，管理员专属）。 */
    @Override
    public void register(Commands commands) {
        List<CommandInterceptor> interceptors = adminInterceptors("update", i18n);
        Predicate<CommandSourceStack> req = requirement(interceptors);
        commands.register(
                literal("update")
                        .requires(req)
                        .then(literal("check").executes(guardedExec("update", interceptors, ctx -> {
                            svc.check(ctx.getSource().getSender());
                            return 1;
                        })))
                        .then(literal("now").executes(guardedExec("update", interceptors, ctx -> {
                            svc.downloadNow(ctx.getSource().getSender());
                            return 1;
                        })))
                        .executes(guardedExec("update", interceptors, ctx -> {
                            ctx.getSource()
                                    .getSender()
                                    .sendMessage(styles.info(feedback.defaultMessage(MessageKeys.CMD_UPDATE_USAGE)));
                            return 1;
                        }))
                        .build(),
                feedback.commandDescription(MessageKeys.CMD_DESC_UPDATE),
                List.of("upd"));
    }
}
