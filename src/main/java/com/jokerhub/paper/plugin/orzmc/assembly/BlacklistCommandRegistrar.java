package com.jokerhub.paper.plugin.orzmc.assembly;

import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.adminInterceptors;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.guardedExec;
import static com.jokerhub.paper.plugin.orzmc.assembly.BrigadierSupport.requirement;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

import com.jokerhub.paper.plugin.orzmc.features.command.binding.CommandInterceptor;
import com.jokerhub.paper.plugin.orzmc.features.security.AccessRuleService;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRule;
import com.jokerhub.paper.plugin.orzmc.features.security.PlayerNameRuleFeedback;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;

/** 安全特性命令注册器：/blacklist（IP 黑名单 + 玩家名规则，别名 bl）。自 FeatureCommandRegistrar 拆出。 */
final class BlacklistCommandRegistrar implements CommandGroup {

    private final AccessRuleService svc;
    private final OrzTextStyles styles;

    BlacklistCommandRegistrar(AccessRuleService svc, OrzTextStyles styles) {
        this.svc = svc;
        this.styles = styles;
    }

    /** Blacklist: /blacklist list|add|remove <pattern>，并支持 player 玩家名规则子命令。 */
    @Override
    public void register(Commands commands) {
        List<CommandInterceptor> interceptors = adminInterceptors("blacklist");
        Predicate<CommandSourceStack> req = requirement(interceptors);

        commands.register(
                literal("blacklist")
                        .requires(req)
                        .then(literal("list")
                                .executes(guardedExec("blacklist", interceptors, ctx -> {
                                    listAccessRules(ctx.getSource().getSender(), svc, styles);
                                    return 1;
                                }))
                                .then(literal("player").executes(guardedExec("blacklist", interceptors, ctx -> {
                                    listPlayerRules(ctx.getSource().getSender(), svc, styles);
                                    return 1;
                                }))))
                        .then(literal("add")
                                .then(literal("player")
                                        .then(argument("type", StringArgumentType.word())
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                                            String type = ctx.getArgument("type", String.class);
                                                            String value = ctx.getArgument("value", String.class);
                                                            handlePlayerRule(
                                                                    ctx.getSource()
                                                                            .getSender(),
                                                                    svc,
                                                                    styles,
                                                                    false,
                                                                    type,
                                                                    value);
                                                            return 1;
                                                        })))))
                                .then(argument("pattern", StringArgumentType.greedyString())
                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                            // greedyString 保留尾随空格：trim 后再判语法/入库，避免带空格的规则永不命中
                                            String pattern = ctx.getArgument("pattern", String.class)
                                                    .trim();
                                            if (pattern.isEmpty()) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error("用法: /blacklist add <IP>"));
                                                return 1;
                                            }
                                            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error(
                                                                "玩家名规则请使用: /blacklist add player <type> <value>"));
                                                return 1;
                                            }
                                            if (svc.addIpPattern(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success("已添加黑名单: " + pattern));
                                            } else {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success("黑名单已存在: " + pattern));
                                            }
                                            return 1;
                                        }))))
                        .then(literal("remove")
                                .then(literal("player")
                                        .then(argument("type", StringArgumentType.word())
                                                .then(argument("value", StringArgumentType.greedyString())
                                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                                            String type = ctx.getArgument("type", String.class);
                                                            String value = ctx.getArgument("value", String.class);
                                                            handlePlayerRule(
                                                                    ctx.getSource()
                                                                            .getSender(),
                                                                    svc,
                                                                    styles,
                                                                    true,
                                                                    type,
                                                                    value);
                                                            return 1;
                                                        })))))
                                .then(argument("pattern", StringArgumentType.greedyString())
                                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                                            String pattern = ctx.getArgument("pattern", String.class)
                                                    .trim();
                                            if (pattern.isEmpty()) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error("用法: /blacklist remove <IP>"));
                                                return 1;
                                            }
                                            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error(
                                                                "玩家名规则请使用: /blacklist remove player <type> <value>"));
                                                return 1;
                                            }
                                            if (svc.removeIpPattern(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success("已从黑名单移除: " + pattern));
                                            } else {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error("未在黑名单中找到: " + pattern));
                                            }
                                            return 1;
                                        }))))
                        // Shorthand: /blacklist <pattern> → add
                        .then(argument("input", StringArgumentType.greedyString())
                                .executes(guardedExec("blacklist", interceptors, ctx -> {
                                    String input = ctx.getArgument("input", String.class);
                                    // player 玩家名规则绝不落入 IP 简写分支（对齐 bot $d 语义），大小写不敏感
                                    String lower = input.toLowerCase(Locale.ROOT);
                                    if (lower.equals("player") || lower.equals("player list")) {
                                        listPlayerRules(ctx.getSource().getSender(), svc, styles);
                                        return 1;
                                    }
                                    // 简写玩家名规则增删（对齐 bot $d 语义，大小写不敏感）：
                                    // /blacklist -player <type> <value> 移除、/blacklist player <type> <value> 添加
                                    if (lower.startsWith("-player")) {
                                        handlePlayerRuleShorthand(
                                                ctx.getSource().getSender(), svc, styles, true, input);
                                        return 1;
                                    }
                                    if (lower.startsWith("player ")) {
                                        handlePlayerRuleShorthand(
                                                ctx.getSource().getSender(), svc, styles, false, input);
                                        return 1;
                                    }
                                    if (lower.startsWith("player") || lower.startsWith("-player")) {
                                        ctx.getSource()
                                                .getSender()
                                                .sendMessage(styles.error(
                                                        "玩家名规则请使用: /blacklist add|remove player <type> <value>"));
                                        return 1;
                                    }
                                    if (input.startsWith("-")) {
                                        // trim：`/blacklist - exact foo` 破折号后带空格时，去掉空格再判玩家名规则语法
                                        String pattern = input.substring(1).trim();
                                        if (pattern.isEmpty()) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error("用法: /blacklist remove <IP>"));
                                            return 1;
                                        }
                                        if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(
                                                            "玩家名规则请使用: /blacklist remove player <type> <value>"));
                                            return 1;
                                        }
                                        if (svc.removeIpPattern(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("已从黑名单移除: " + pattern));
                                        } else {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error("未在黑名单中找到: " + pattern));
                                        }
                                    } else {
                                        String pattern = input.trim();
                                        if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(
                                                            "玩家名规则请使用: /blacklist add player <type> <value>"));
                                            return 1;
                                        }
                                        if (svc.addIpPattern(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("已添加黑名单: " + pattern));
                                        } else {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success("黑名单已存在: " + pattern));
                                        }
                                    }
                                    return 1;
                                })))
                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                            listAccessRules(ctx.getSource().getSender(), svc, styles);
                            return 1;
                        }))
                        .build(),
                "IP黑名单与玩家名规则管理",
                List.of("bl"));
    }

    private static void listAccessRules(CommandSender sender, AccessRuleService svc, OrzTextStyles styles) {
        List<String> patterns = svc.getIpPatterns();
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (patterns.isEmpty() && rules.isEmpty()) {
            sender.sendMessage(styles.info("访问规则为空"));
            return;
        }
        sender.sendMessage(styles.info("当前访问规则:"));
        if (!patterns.isEmpty()) {
            sender.sendMessage(styles.info("  IP黑名单:"));
            for (String pattern : patterns) {
                sender.sendMessage(styles.info("    " + pattern));
            }
        }
        if (!rules.isEmpty()) {
            sender.sendMessage(styles.info("  玩家名规则:"));
            for (PlayerNameRule rule : rules) {
                sender.sendMessage(styles.info("    " + rule.display()));
            }
        }
    }

    private static void listPlayerRules(CommandSender sender, AccessRuleService svc, OrzTextStyles styles) {
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (rules.isEmpty()) {
            sender.sendMessage(styles.info("玩家名规则为空"));
            return;
        }
        sender.sendMessage(styles.info("当前玩家名规则:"));
        for (PlayerNameRule rule : rules) {
            sender.sendMessage(styles.info("  " + rule.display()));
        }
    }

    private static void handlePlayerRule(
            CommandSender sender,
            AccessRuleService svc,
            OrzTextStyles styles,
            boolean remove,
            String typeRaw,
            String value) {
        if (value == null || value.isBlank()) {
            sender.sendMessage(
                    styles.error("玩家名规则值不能为空: /blacklist " + (remove ? "remove" : "add") + " player <type> <value>"));
            return;
        }
        // 反馈统一走 PlayerNameRuleFeedback（与 bot $d 共用，避免两边实现漂移）
        PlayerNameRuleFeedback.Outcome outcome = PlayerNameRuleFeedback.feedback(svc, typeRaw, value, remove);
        sender.sendMessage(outcome.success() ? styles.success(outcome.message()) : styles.error(outcome.message()));
    }

    /** 游戏侧简写解析（镜像 bot $d）：{@code /blacklist [-player|player] <type> <value>}。 */
    private static void handlePlayerRuleShorthand(
            CommandSender sender, AccessRuleService svc, OrzTextStyles styles, boolean remove, String input) {
        String prefix = remove ? "-player" : "player";
        String rest = input.substring(prefix.length());
        if (rest.isEmpty() || !rest.startsWith(" ")) {
            sender.sendMessage(styles.error("用法: /blacklist " + (remove ? "-" : "") + "player <type> <value>"));
            return;
        }
        String[] parts = rest.trim().split("\\s+", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            sender.sendMessage(styles.error("用法: /blacklist " + (remove ? "-" : "") + "player <type> <value>"));
            return;
        }
        handlePlayerRule(sender, svc, styles, remove, parts[0], parts[1]);
    }
}
