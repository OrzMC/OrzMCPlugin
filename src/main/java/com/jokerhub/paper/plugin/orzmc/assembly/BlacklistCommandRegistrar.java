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
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nService;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.I18nServiceHolder;
import com.jokerhub.paper.plugin.orzmc.infra.i18n.MessageKeys;
import com.jokerhub.paper.plugin.orzmc.infra.styles.OrzTextStyles;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;

/**
 * 安全特性命令注册器：/blacklist（IP 黑名单 + 玩家名规则，别名 bl）。自 FeatureCommandRegistrar 拆出。
 *
 * <p>i18n P5-4：安全运维命令反馈统一默认语言 R1（与 guard/bot 安全提示策略一致），文案走
 * {@code access_rule.*}（与 bot {@code $d} 共用域）；玩家名规则增删核心文案由
 * {@link PlayerNameRuleFeedback} 统一承载 (access_rule.value_empty / invalid_* 与 removed 等)。</p>
 */
final class BlacklistCommandRegistrar implements CommandGroup {

    private final AccessRuleService svc;
    private final OrzTextStyles styles;
    private final I18nService i18n;

    BlacklistCommandRegistrar(AccessRuleService svc, OrzTextStyles styles, I18nService i18n) {
        this.svc = svc;
        this.styles = styles;
        this.i18n = i18n;
    }

    private String msg(String key) {
        return I18nServiceHolder.msg(key);
    }

    private String msg(String key, Map<String, String> vars) {
        return I18nServiceHolder.msg(key, vars);
    }

    /** Blacklist: /blacklist list|add|remove <pattern>，并支持 player 玩家名规则子命令。 */
    @Override
    public void register(Commands commands) {
        List<CommandInterceptor> interceptors = adminInterceptors("blacklist", i18n);
        Predicate<CommandSourceStack> req = requirement(interceptors);

        commands.register(
                literal("blacklist")
                        .requires(req)
                        .then(literal("list")
                                .executes(guardedExec("blacklist", interceptors, ctx -> {
                                    listAccessRules(ctx.getSource().getSender());
                                    return 1;
                                }))
                                .then(literal("player").executes(guardedExec("blacklist", interceptors, ctx -> {
                                    listPlayerRules(ctx.getSource().getSender());
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
                                                        .sendMessage(styles.error(msg("access_rule.usage_ip_add")));
                                                return 1;
                                            }
                                            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error(msg("access_rule.usage_player_add")));
                                                return 1;
                                            }
                                            if (svc.addIpPattern(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success(msg(
                                                                "access_rule.added_ip", Map.of("pattern", pattern))));
                                            } else {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success(msg(
                                                                "access_rule.exists_ip", Map.of("pattern", pattern))));
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
                                                        .sendMessage(styles.error(msg("access_rule.usage_ip_remove")));
                                                return 1;
                                            }
                                            if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(
                                                                styles.error(msg("access_rule.usage_player_remove")));
                                                return 1;
                                            }
                                            if (svc.removeIpPattern(pattern)) {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.success(msg(
                                                                "access_rule.removed_ip", Map.of("pattern", pattern))));
                                            } else {
                                                ctx.getSource()
                                                        .getSender()
                                                        .sendMessage(styles.error(msg(
                                                                "access_rule.not_found_ip",
                                                                Map.of("pattern", pattern))));
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
                                        listPlayerRules(ctx.getSource().getSender());
                                        return 1;
                                    }
                                    // 简写玩家名规则增删（对齐 bot $d 语义，大小写不敏感）：
                                    // /blacklist -player <type> <value> 移除、/blacklist player <type> <value> 添加
                                    if (lower.startsWith("-player")) {
                                        handlePlayerRuleShorthand(
                                                ctx.getSource().getSender(), true, input);
                                        return 1;
                                    }
                                    if (lower.startsWith("player ")) {
                                        handlePlayerRuleShorthand(
                                                ctx.getSource().getSender(), false, input);
                                        return 1;
                                    }
                                    if (lower.startsWith("player") || lower.startsWith("-player")) {
                                        ctx.getSource()
                                                .getSender()
                                                .sendMessage(styles.error(msg("access_rule.usage_player_any")));
                                        return 1;
                                    }
                                    if (input.startsWith("-")) {
                                        // trim：`/blacklist - exact foo` 破折号后带空格时，去掉空格再判玩家名规则语法
                                        String pattern = input.substring(1).trim();
                                        if (pattern.isEmpty()) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(msg("access_rule.usage_ip_remove")));
                                            return 1;
                                        }
                                        if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(msg("access_rule.usage_player_remove")));
                                            return 1;
                                        }
                                        if (svc.removeIpPattern(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success(
                                                            msg("access_rule.removed_ip", Map.of("pattern", pattern))));
                                        } else {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(msg(
                                                            "access_rule.not_found_ip", Map.of("pattern", pattern))));
                                        }
                                    } else {
                                        String pattern = input.trim();
                                        if (PlayerNameRule.looksLikePlayerRuleSyntax(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.error(msg("access_rule.usage_player_add")));
                                            return 1;
                                        }
                                        if (svc.addIpPattern(pattern)) {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success(
                                                            msg("access_rule.added_ip", Map.of("pattern", pattern))));
                                        } else {
                                            ctx.getSource()
                                                    .getSender()
                                                    .sendMessage(styles.success(
                                                            msg("access_rule.exists_ip", Map.of("pattern", pattern))));
                                        }
                                    }
                                    return 1;
                                })))
                        .executes(guardedExec("blacklist", interceptors, ctx -> {
                            listAccessRules(ctx.getSource().getSender());
                            return 1;
                        }))
                        .build(),
                msg(MessageKeys.CMD_DESC_BLACKLIST),
                List.of("bl"));
    }

    private void listAccessRules(CommandSender sender) {
        List<String> patterns = svc.getIpPatterns();
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (patterns.isEmpty() && rules.isEmpty()) {
            sender.sendMessage(styles.info(msg("access_rule.list_empty_all")));
            return;
        }
        sender.sendMessage(styles.info(msg("access_rule.list_title")));
        if (!patterns.isEmpty()) {
            sender.sendMessage(styles.info(msg("access_rule.ip_section")));
            for (String pattern : patterns) {
                sender.sendMessage(styles.info("    " + pattern));
            }
        }
        if (!rules.isEmpty()) {
            sender.sendMessage(styles.info(msg("access_rule.rules_section")));
            for (PlayerNameRule rule : rules) {
                sender.sendMessage(styles.info("    " + rule.display()));
            }
        }
    }

    private void listPlayerRules(CommandSender sender) {
        List<PlayerNameRule> rules = svc.getPlayerNameRules();
        if (rules.isEmpty()) {
            sender.sendMessage(styles.info(msg("access_rule.player_rules_empty")));
            return;
        }
        sender.sendMessage(styles.info(msg("access_rule.player_rules_title")));
        for (PlayerNameRule rule : rules) {
            sender.sendMessage(styles.info("  " + rule.display()));
        }
    }

    private void handlePlayerRule(CommandSender sender, boolean remove, String typeRaw, String value) {
        if (value == null || value.isBlank()) {
            sender.sendMessage(styles.error(
                    msg("access_rule.usage_player_value_empty", Map.of("verb", remove ? "remove" : "add"))));
            return;
        }
        // 反馈统一走 PlayerNameRuleFeedback（与 bot $d 共用，避免两边实现漂移）
        PlayerNameRuleFeedback.Outcome outcome = PlayerNameRuleFeedback.feedback(svc, typeRaw, value, remove);
        sender.sendMessage(outcome.success() ? styles.success(outcome.message()) : styles.error(outcome.message()));
    }

    /** 游戏侧简写解析（镜像 bot $d）：{@code /blacklist [-player|player] <type> <value>}。 */
    private void handlePlayerRuleShorthand(CommandSender sender, boolean remove, String input) {
        String prefix = remove ? "-player" : "player";
        String rest = input.substring(prefix.length());
        if (rest.isEmpty() || !rest.startsWith(" ")) {
            sender.sendMessage(
                    styles.error(msg("access_rule.usage_player_shorthand", Map.of("sign", remove ? "-" : ""))));
            return;
        }
        String[] parts = rest.trim().split("\\s+", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            sender.sendMessage(
                    styles.error(msg("access_rule.usage_player_shorthand", Map.of("sign", remove ? "-" : ""))));
            return;
        }
        handlePlayerRule(sender, remove, parts[0], parts[1]);
    }
}
