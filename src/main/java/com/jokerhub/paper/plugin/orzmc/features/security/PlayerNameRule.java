package com.jokerhub.paper.plugin.orzmc.features.security;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 玩家名匹配规则。
 *
 * <p>默认大小写不敏感。离线模式下玩家名由客户端上报，名称规则适合做风控/反滥用，
 * 不能替代 UUID 或 IP 作为强身份安全边界。</p>
 */
public final class PlayerNameRule {

    public enum MatchType {
        EXACT,
        PREFIX,
        SUFFIX,
        CONTAINS,
        GLOB,
        REGEX;

        public static MatchType from(String raw) {
            if (raw == null) return null;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public String display() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final MatchType type;
    private final String value;
    private final Pattern pattern;

    private PlayerNameRule(MatchType type, String value) {
        this.type = type;
        this.value = value;
        this.pattern = compilePattern(type, value);
    }

    public static PlayerNameRule of(MatchType type, String value) {
        return new PlayerNameRule(type, value);
    }

    /**
     * 解析并校验玩家名规则参数（bot {@code $d} 与游戏内 {@code /blacklist} 共用，消除重复）。
     *
     * <p>{@code valid()} 为 true 时 {@code rule()} 非 null；{@code valid()} 为 false 时
     * 若 {@code type()} 为 null 表示匹配类型未知，否则表示正则非法。</p>
     */
    public static ParsedRule parse(String typeRaw, String value) {
        MatchType type = MatchType.from(typeRaw);
        if (type == null) {
            return new ParsedRule(null, null, false);
        }
        PlayerNameRule rule = of(type, value);
        return new ParsedRule(type, rule, rule.isValid());
    }

    /** 玩家名规则的解析结果：{@code valid()} 为 false 表示参数不合法（类型未知或正则非法）。 */
    public record ParsedRule(MatchType type, PlayerNameRule rule, boolean valid) {}

    /**
     * 判断输入是否看起来像玩家名规则命令（player/-player 前缀，或首词是六种匹配类型之一）。
     *
     * <p>供 bot {@code $d} 与游戏内 {@code /blacklist} 在把字符串当 IP 规则添加/移除前拦截误输入，
     * 避免把 {@code prefix bot_} 之类静默当成永不匹配的 IP 条目并回复假成功。</p>
     */
    public static boolean looksLikePlayerRuleSyntax(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("player") || lower.startsWith("-player")) return true;
        String firstToken = lower.split("\\s+", 2)[0];
        if (MatchType.from(firstToken) != null) return true;
        // 冒号/粘连形式（exact:foo、prefix_bot）首词带类型词也是玩家名规则语法，
        // 防 `$d exact:foo` / `/blacklist add exact:foo` 落入 IP 添加、存一条永不命中的死规则并假「已添加」。
        // 真实 IPv6 字面量（2001:db8::1）冒号前缀是数字，MatchType.from 为 null，不受影响。
        int colon = firstToken.indexOf(':');
        return colon > 0 && MatchType.from(firstToken.substring(0, colon)) != null;
    }

    public MatchType type() {
        return type;
    }

    public String value() {
        return value;
    }

    public boolean isValid() {
        return type != MatchType.REGEX || pattern != null;
    }

    /**
     * 匹配玩家名。REGEX 用 {@code find()}（未锚定的正则即「包含」语义：{@code regex admin} 命中
     * 名字含 admin 的玩家；需要整名匹配时显式写 {@code ^admin$}），GLOB 保持 ^...$ 整名锚定
     * （{@code glob bot_*} 命中以 bot_ 开头的名字）。
     */
    public boolean matches(String name) {
        if (name == null || value == null || value.isEmpty()) return false;
        return switch (type) {
            case EXACT -> name.equalsIgnoreCase(value);
            case PREFIX -> name.regionMatches(true, 0, value, 0, value.length());
            case SUFFIX -> {
                int start = name.length() - value.length();
                yield start >= 0 && name.regionMatches(true, start, value, 0, value.length());
            }
            case CONTAINS -> containsIgnoreCase(name, value);
            case REGEX -> pattern != null && pattern.matcher(name).find();
            case GLOB -> pattern != null && pattern.matcher(name).matches();
        };
    }

    public String display() {
        return type.display() + ":" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerNameRule other)) return false;
        if (type != other.type) return false;
        // 与 AccessRuleService.sameRule（equalsIgnoreCase 去重）口径一致，避免
        // equals/hashCode 与去重判定对仅大小写不同的规则给出互相矛盾的结果。
        return value == null ? other.value == null : value.equalsIgnoreCase(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value == null ? null : value.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return display();
    }

    private static boolean containsIgnoreCase(String name, String needle) {
        int needleLength = needle.length();
        for (int i = 0; i <= name.length() - needleLength; i++) {
            if (name.regionMatches(true, i, needle, 0, needleLength)) return true;
        }
        return false;
    }

    private static Pattern compilePattern(MatchType type, String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            String regex =
                    switch (type) {
                        case GLOB -> globToRegex(value);
                        case REGEX -> value;
                        default -> null;
                    };
            return regex == null ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.append('$').toString();
    }
}
