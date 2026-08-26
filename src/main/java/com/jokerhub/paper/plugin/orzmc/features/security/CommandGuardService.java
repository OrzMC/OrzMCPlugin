package com.jokerhub.paper.plugin.orzmc.features.security;

import com.jokerhub.paper.plugin.orzmc.infra.config.configs.SecurityGuardConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 危险命令判定核心（纯逻辑，无 Bukkit 依赖）。
 *
 * <p>对一条待执行命令做两类检查：</p>
 * <ol>
 *   <li><b>deny-list</b>：配置的高危命令（默认 {@code op}、{@code publish}、{@code seed}）
 *       命中即 {@code BLOCK}——覆盖站点文章《危险指令》防护原则②「高危节点默认拒绝」；</li>
 *   <li><b>目标选择器守护</b>：{@code kill}/{@code clear}/{@code give}/{@code execute}/{@code effect}
 *       中出现<b>未限定</b> {@code type=..} 或 {@code distance=..} 的裸 {@code @e}/{@code @a} 即 {@code WARN}
 *       ——对应文章「目标选择器警示」与防护原则⑦「执行前过一遍目标」。</li>
 * </ol>
 *
 * <p>配置通过 {@link Supplier} 注入，事件侧每次读取最新配置（与 {@code TntPolicy} 的
 * {@code currentPolicy()} 模式一致），热重载无需重建服务。</p>
 */
public final class CommandGuardService {

    /** 目标选择器守护适用的命令：对这些命令检查裸 @e/@a。 */
    private static final List<String> SELECTOR_SENSITIVE_COMMANDS =
            List.of("kill", "clear", "give", "execute", "effect");

    private final Supplier<SecurityGuardConfig> configSupplier;

    public CommandGuardService(Supplier<SecurityGuardConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    /** 判定一条命令是否允许执行。 */
    public GuardDecision guard(String commandLine) {
        if (!configSupplier.get().enabled()) {
            return GuardDecision.allow();
        }
        if (commandLine == null || commandLine.isBlank()) {
            return GuardDecision.allow();
        }

        ParsedCommand parsed = ParsedCommand.parse(commandLine);

        for (String rule : configSupplier.get().blockedCommands()) {
            if (matchesRule(parsed, rule)) {
                return GuardDecision.block("命令「" + parsed.primary() + "」已被安全拦截（危险命令 deny-list）");
            }
        }

        if (SELECTOR_SENSITIVE_COMMANDS.contains(parsed.primary())) {
            String unqualified = findUnqualifiedSelector(parsed.arguments());
            if (unqualified != null) {
                return GuardDecision.warn("命令含未限定范围的 " + unqualified + "，可能影响全体实体/玩家，请确认已用 type=.. 或 distance=.. 限定");
            }
        }

        return GuardDecision.allow();
    }

    /** deny-list 规则匹配：单词规则匹配命令名；多词规则（如 "plugman reload"）做命令前缀匹配。 */
    private static boolean matchesRule(ParsedCommand parsed, String rule) {
        String[] ruleTokens = rule.split("\\s+");
        if (ruleTokens.length == 1) {
            return parsed.primary().equals(ruleTokens[0]);
        }
        List<String> tokens = parsed.tokens();
        if (tokens.size() < ruleTokens.length) {
            return false;
        }
        for (int i = 0; i < ruleTokens.length; i++) {
            if (!tokens.get(i).equals(ruleTokens[i])) {
                return false;
            }
        }
        return true;
    }

    /** 在参数中查找未限定作用范围的 @e/@a 选择器；无则返回 null。 */
    private static String findUnqualifiedSelector(String arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        String[] tokens = arguments.split("\\s+");
        for (String token : tokens) {
            if (!(token.startsWith("@e") || token.startsWith("@a"))) {
                continue;
            }
            if (token.startsWith("@e[") || token.startsWith("@a[")) {
                if (!isLimitedSelector(token)) {
                    return token;
                }
            } else {
                return token;
            }
        }
        return null;
    }

    /**
     * 选择器是否已把作用范围收窄到安全集合。
     *
     * <p>符合任一即视为已限定：</p>
     * <ul>
     *   <li>{@code limit=}：限制数量（如 {@code @e[limit=1]}）；</li>
     *   <li>{@code distance=..N}：限制最大距离（上界）；</li>
     *   <li>{@code type=X} 正向限定类型；而 {@code type=!X}（排除）作用范围仍然很广，
     *       例如 {@code @e[type=!player]} 会清掉全部非玩家实体，视为未限定。</li>
     * </ul>
     */
    private static boolean isLimitedSelector(String token) {
        if (token.contains("limit=")) {
            return true;
        }
        if (token.contains("distance=..")) {
            return true;
        }
        return token.contains("type=") && !token.contains("type=!");
    }

    /** 归一化后的命令：主命令名 + 全部分词 + 参数。 */
    record ParsedCommand(String primary, List<String> tokens, String arguments) {

        static ParsedCommand parse(String commandLine) {
            String normalized = normalize(commandLine);
            String[] tokenArray = normalized.split("\\s+");
            String primary = tokenArray[0];
            String arguments = tokenArray.length > 1
                    ? normalized.substring(primary.length()).trim()
                    : "";
            return new ParsedCommand(primary, List.of(tokenArray), arguments);
        }

        /** 去前导 /、去首尾空白、转小写、剥命名空间前缀（minecraft:/bukkit:/任意插件 ns:）。 */
        static String normalize(String line) {
            String s = line.trim();
            if (s.startsWith("/")) {
                // 去 / 后再 trim，防 "/ op"（斜杠后空白）产生空首 token 绕过 deny-list
                s = s.substring(1).trim();
            }
            s = s.toLowerCase(Locale.ROOT);
            // 剥命名空间前缀：CommandMap 为命令注册带命名空间的别名（如 bukkit:stop / minecraft:op），
            // deny-list 应匹配命令本体而非命名空间别名。仅当冒号位于命令名（首个 token）内才剥，
            // 避免误伤参数中的冒号（如 "/say hello:world"）。
            int colon = s.indexOf(':');
            int firstSpace = s.indexOf(' ');
            if (colon > 0 && (firstSpace == -1 || colon < firstSpace)) {
                s = s.substring(colon + 1);
            }
            return s;
        }
    }

    /** 判定结果。 */
    public record GuardDecision(Decision kind, String reason) {

        public enum Decision {
            ALLOW,
            BLOCK,
            WARN
        }

        public static GuardDecision allow() {
            return new GuardDecision(Decision.ALLOW, "");
        }

        public static GuardDecision block(String reason) {
            return new GuardDecision(Decision.BLOCK, reason);
        }

        public static GuardDecision warn(String reason) {
            return new GuardDecision(Decision.WARN, reason);
        }

        public boolean blocked() {
            return kind == Decision.BLOCK;
        }

        public boolean warned() {
            return kind == Decision.WARN;
        }
    }
}
